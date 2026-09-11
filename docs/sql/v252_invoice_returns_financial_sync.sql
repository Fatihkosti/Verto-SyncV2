-- Verto F252 — extend the F249 financial event contract with immutable invoice returns.
-- Apply after v249_financial_event_sync.sql. Safe for existing financial_sync_events rows.

do $$
declare
    v_constraint text;
begin
    select c.conname into v_constraint
      from pg_constraint c
     where c.conrelid = 'public.financial_sync_events'::regclass
       and c.contype = 'c'
       and pg_get_constraintdef(c.oid) like '%operation_type%'
     limit 1;
    if v_constraint is not null then
        execute format('alter table public.financial_sync_events drop constraint %I', v_constraint);
    end if;
    alter table public.financial_sync_events
        add constraint ck_financial_sync_operation_type
        check (operation_type in (
            'INVOICE_CREATED','INVOICE_UPDATED','INVOICE_VOIDED',
            'PAYMENT_RECORDED','PAYMENT_REVERSED','INVOICE_RETURN_POSTED'
        ));
end $$;

create or replace function public.financial_sync_apply_event_v1(
    p_event_id text,
    p_write_id text,
    p_aggregate_id text,
    p_aggregate_version integer,
    p_aggregate_sequence bigint,
    p_operation_type text,
    p_payload_version integer,
    p_schema_version integer,
    p_payload text,
    p_occurred_at bigint,
    p_recorded_at bigint
)
returns table (
    event_id text,
    write_id text,
    aggregate_id text,
    server_revision bigint,
    server_recorded_at bigint,
    status text,
    replayed boolean,
    conflict_reason text
)
language plpgsql
security definer
set search_path = public, pg_temp
as $$
declare
    v_org uuid;
    v_existing public.financial_sync_events%rowtype;
    v_last public.financial_sync_events%rowtype;
    v_invoice_status text;
    v_invoice_version integer;
    v_logical_version integer := 0;
    v_parent_exists boolean := false;
    v_payload jsonb;
begin
    select au.organization_id
      into v_org
      from public.app_users au
     where au.id = auth.uid() and coalesce(au.is_active, true)
     limit 1;

    if v_org is null then
        raise exception 'financial sync requires an active organization';
    end if;
    if coalesce(p_event_id, '') = '' or coalesce(p_write_id, '') = '' or coalesce(p_aggregate_id, '') = '' then
        raise exception 'financial sync identity is required';
    end if;
    if p_aggregate_version < 1 or p_aggregate_sequence < 1 then
        raise exception 'financial sync version/sequence must be positive';
    end if;
    if p_schema_version <> 1 or p_payload_version < 1 then
        raise exception 'unsupported financial sync schema/payload version';
    end if;
    if p_operation_type not in ('INVOICE_CREATED','INVOICE_UPDATED','INVOICE_VOIDED','PAYMENT_RECORDED','PAYMENT_REVERSED','INVOICE_RETURN_POSTED') then
        raise exception 'unsupported financial operation';
    end if;

    begin
        v_payload := p_payload::jsonb;
    exception when others then
        raise exception 'financial sync payload must be JSON';
    end;

    -- Exactly-once effect: same organization + operation + writeId returns the original result.
    select * into v_existing
      from public.financial_sync_events e
     where e.organization_id = v_org
       and e.operation_type = p_operation_type
       and e.write_id = p_write_id
     limit 1;

    if found then
        if v_existing.event_id <> p_event_id or v_existing.aggregate_id <> p_aggregate_id then
            return query select
                v_existing.event_id, v_existing.write_id, v_existing.aggregate_id,
                v_existing.server_revision, v_existing.server_recorded_at,
                'CONFLICT'::text, false, 'idempotency identity reused for different event'::text;
        else
            return query select
                v_existing.event_id, v_existing.write_id, v_existing.aggregate_id,
                v_existing.server_revision, v_existing.server_recorded_at,
                'REPLAYED'::text, true, ''::text;
        end if;
        return;
    end if;

    -- Aggregate ordering. During the compatibility window an existing legacy invoice may establish
    -- the baseline even if it has no historical event rows yet; after the first event, sequence is strict.
    select * into v_last
      from public.financial_sync_events e
     where e.organization_id = v_org and e.aggregate_id = p_aggregate_id
     order by e.aggregate_sequence desc
     limit 1;

    if found then
        if p_aggregate_sequence <= v_last.aggregate_sequence then
            return query select p_event_id, p_write_id, p_aggregate_id, v_last.server_revision,
                v_last.server_recorded_at, 'CONFLICT'::text, false,
                'stale or competing aggregate sequence'::text;
            return;
        elsif p_aggregate_sequence <> v_last.aggregate_sequence + 1 then
            return query select p_event_id, p_write_id, p_aggregate_id, v_last.server_revision,
                v_last.server_recorded_at, 'WAITING_DEPENDENCY'::text, false,
                'previous aggregate event has not arrived'::text;
            return;
        end if;
        v_logical_version := v_last.aggregate_version;
        v_parent_exists := true;
    end if;

    select i.lifecycle_status, i.lifecycle_version
      into v_invoice_status, v_invoice_version
      from public.invoices i
     where i.organization_id = v_org and i.id::text = p_aggregate_id
     limit 1;

    if found then
        v_parent_exists := true;
        v_logical_version := greatest(v_logical_version, coalesce(v_invoice_version, 1));
    end if;

    -- Parent-before-child. Invoice creation itself may lead the compatibility row upsert.
    if p_operation_type <> 'INVOICE_CREATED' and not v_parent_exists then
        return query select p_event_id, p_write_id, p_aggregate_id, 0::bigint, 0::bigint,
            'WAITING_DEPENDENCY'::text, false, 'parent invoice has not arrived'::text;
        return;
    end if;

    -- Posted/voided lifecycle never uses timestamp LWW. Invoice mutations advance one version.
    if p_operation_type in ('INVOICE_UPDATED','INVOICE_VOIDED') then
        if p_aggregate_version <> v_logical_version + 1 then
            -- Transition compatibility: an old row-sync may already have materialized this exact version.
            if not (v_last.event_id is null and p_aggregate_version = v_logical_version) then
                return query select p_event_id, p_write_id, p_aggregate_id,
                    coalesce(v_last.server_revision, 0), coalesce(v_last.server_recorded_at, 0),
                    'CONFLICT'::text, false, 'optimistic aggregate version conflict'::text;
                return;
            end if;
        end if;
    elsif p_operation_type = 'INVOICE_CREATED' then
        if v_last.event_id is not null then
            return query select p_event_id, p_write_id, p_aggregate_id,
                v_last.server_revision, v_last.server_recorded_at,
                'CONFLICT'::text, false, 'invoice aggregate already has event history'::text;
            return;
        end if;
    else
        -- Payment/reversal events do not rewrite invoice lifecycle version, but they must not claim
        -- a future or stale parent version.
        if v_logical_version > 0 and p_aggregate_version <> v_logical_version then
            return query select p_event_id, p_write_id, p_aggregate_id,
                coalesce(v_last.server_revision, 0), coalesce(v_last.server_recorded_at, 0),
                'CONFLICT'::text, false, 'payment parent version conflict'::text;
            return;
        end if;
    end if;

    -- Return facts and a whole-invoice void are mutually exclusive: combining both would double-reverse.
    if p_operation_type = 'INVOICE_VOIDED' and exists (
        select 1 from public.financial_sync_events e
         where e.organization_id = v_org and e.aggregate_id = p_aggregate_id
           and e.operation_type = 'INVOICE_RETURN_POSTED'
    ) then
        return query select p_event_id, p_write_id, p_aggregate_id,
            coalesce(v_last.server_revision, 0), coalesce(v_last.server_recorded_at, 0),
            'CONFLICT'::text, false, 'invoice with returns cannot be voided'::text;
        return;
    end if;

    -- Two-device void: once the logical stream contains VOID at this or a later version, another void
    -- cannot create a second financial reversal even if the legacy invoice row has not arrived yet.
    if p_operation_type = 'INVOICE_VOIDED' and exists (
        select 1 from public.financial_sync_events e
         where e.organization_id = v_org and e.aggregate_id = p_aggregate_id
           and e.operation_type = 'INVOICE_VOIDED'
    ) then
        return query select p_event_id, p_write_id, p_aggregate_id,
            coalesce(v_last.server_revision, 0), coalesce(v_last.server_recorded_at, 0),
            'CONFLICT'::text, false, 'invoice was already voided'::text;
        return;
    end if;

    -- F252 return is an immutable child of a POSTED invoice. Validate payload identity and every
    -- returned line against the original item plus all previously accepted return events.
    if p_operation_type = 'INVOICE_RETURN_POSTED' then
        if coalesce(v_payload->>'returnId', '') = ''
           or coalesce(v_payload->>'writeId', '') = ''
           or v_payload->>'originalInvoiceId' <> p_aggregate_id
           or v_payload->>'writeId' <> p_write_id
           or coalesce(v_payload->>'documentType', '') not in ('SALES_RETURN_CREDIT_NOTE','PURCHASE_RETURN_DEBIT_NOTE')
           or coalesce(v_payload->>'settlementMode', '') not in ('CREDIT_BALANCE','CASH_REFUND')
           or coalesce((v_payload->>'transactionAmountMinor')::bigint, 0) <= 0
           or coalesce((v_payload->>'functionalAmountMinor')::bigint, 0) <= 0
           or jsonb_typeof(v_payload->'lines') <> 'array'
           or jsonb_array_length(v_payload->'lines') = 0 then
            raise exception 'invalid invoice return payload';
        end if;

        if not exists (
            select 1 from public.invoices i
             where i.organization_id = v_org
               and i.id::text = p_aggregate_id
               and i.client_id::text = v_payload->>'clientId'
               and i.legacy_currency_status::text = 'KNOWN'
               and i.transaction_currency_code = v_payload->>'transactionCurrencyCode'
               and i.functional_currency_code = v_payload->>'functionalCurrencyCode'
               and i.transaction_amount_minor > 0
               and i.functional_amount_at_recognition_minor > 0
               and (v_payload->>'functionalAmountMinor')::bigint = round(
                    (v_payload->>'transactionAmountMinor')::numeric
                    * i.functional_amount_at_recognition_minor::numeric
                    / i.transaction_amount_minor::numeric
               )::bigint
               and (
                    (i.category::text = 'SALE' and v_payload->>'documentType' = 'SALES_RETURN_CREDIT_NOTE')
                 or (i.category::text = 'PURCHASE' and v_payload->>'documentType' = 'PURCHASE_RETURN_DEBIT_NOTE')
               )
        ) then
            raise exception 'invoice return party/type/currency truth does not match original invoice';
        end if;

        if coalesce(v_invoice_status, '') <> 'POSTED' or exists (
            select 1 from public.financial_sync_events e
             where e.organization_id = v_org and e.aggregate_id = p_aggregate_id
               and e.operation_type = 'INVOICE_VOIDED'
        ) then
            return query select p_event_id, p_write_id, p_aggregate_id,
                coalesce(v_last.server_revision, 0), coalesce(v_last.server_recorded_at, 0),
                'CONFLICT'::text, false, 'return requires a posted non-void invoice'::text;
            return;
        end if;

        if exists (
            select 1
              from jsonb_array_elements(v_payload->'lines') line
             where coalesce(line->>'originalInvoiceItemId', '') = ''
                or coalesce((line->>'quantity')::integer, 0) <= 0
                or not exists (
                    select 1 from public.invoice_items ii
                     where ii.id::text = line->>'originalInvoiceItemId'
                       and ii.invoice_id::text = p_aggregate_id
                )
        ) then
            raise exception 'invoice return line does not belong to original invoice';
        end if;

        if exists (
            select 1
              from jsonb_array_elements(v_payload->'lines') line
             where not exists (
                 select 1 from public.invoice_items ii
                  where ii.id::text = line->>'originalInvoiceItemId'
                    and ii.invoice_id::text = p_aggregate_id
                    and coalesce(ii.inventory_item_id::text, '') = coalesce(line->>'inventoryItemId', '')
                    and (
                        (
                            v_payload->>'documentType' = 'SALES_RETURN_CREDIT_NOTE'
                            and (line->>'unitTransactionAmountMinor')::bigint = ii.unit_sell_price_minor
                            and (line->>'unitCostAtSaleMinor')::bigint = ii.unit_cost_at_sale_minor
                            and ((coalesce(ii.inventory_item_id::text, '') = '') or ii.cost_snapshot_status = 'KNOWN')
                        )
                        or (
                            v_payload->>'documentType' = 'PURCHASE_RETURN_DEBIT_NOTE'
                            and (line->>'unitTransactionAmountMinor')::bigint = round(ii.buy_price * 100)::bigint
                            and (line->>'originalPurchaseUnitCostMinor')::bigint = round(ii.buy_price * 100)::bigint
                        )
                    )
             )
        ) then
            raise exception 'invoice return monetary facts do not match original invoice line';
        end if;

        if exists (
            select 1
              from jsonb_array_elements(v_payload->'lines') line
             where coalesce(line->>'id', '') = ''
                or coalesce((line->>'unitTransactionAmountMinor')::bigint, -1) < 0
                or coalesce((line->>'transactionAmountMinor')::bigint, 0) <= 0
                or coalesce((line->>'unitFunctionalAmountMinor')::bigint, -1) < 0
                or coalesce((line->>'functionalAmountMinor')::bigint, 0) <= 0
                or coalesce((line->>'unitCostAtSaleMinor')::bigint, -1) < 0
                or coalesce((line->>'historicalCostAmountMinor')::bigint, -1) < 0
                or coalesce((line->>'originalPurchaseUnitCostMinor')::bigint, -1) < 0
                or (line->>'transactionAmountMinor')::bigint <>
                   (line->>'unitTransactionAmountMinor')::bigint * (line->>'quantity')::bigint
                or (
                    v_payload->>'documentType' = 'SALES_RETURN_CREDIT_NOTE'
                    and (
                        (line->>'historicalCostAmountMinor')::bigint <>
                            (line->>'unitCostAtSaleMinor')::bigint * (line->>'quantity')::bigint
                        or (line->>'originalPurchaseUnitCostMinor')::bigint <> 0
                    )
                )
                or (
                    v_payload->>'documentType' = 'PURCHASE_RETURN_DEBIT_NOTE'
                    and (
                        (line->>'unitCostAtSaleMinor')::bigint <> 0
                        or (line->>'historicalCostAmountMinor')::bigint <> 0
                    )
                )
        ) then
            raise exception 'invalid invoice return line monetary facts';
        end if;

        if exists (
            select 1
              from jsonb_array_elements(v_payload->'lines') line
             group by line->>'originalInvoiceItemId'
            having count(*) > 1
        ) or exists (
            select 1
              from jsonb_array_elements(v_payload->'lines') line
             group by line->>'id'
            having count(*) > 1
        ) then
            raise exception 'duplicate invoice return line identity';
        end if;

        if (select sum((line->>'transactionAmountMinor')::bigint)
              from jsonb_array_elements(v_payload->'lines') line)
             <> (v_payload->>'transactionAmountMinor')::bigint
           or (select sum((line->>'functionalAmountMinor')::bigint)
                 from jsonb_array_elements(v_payload->'lines') line)
             <> (v_payload->>'functionalAmountMinor')::bigint then
            raise exception 'invoice return document totals do not match lines';
        end if;

        if exists (
            select 1 from public.financial_sync_events e
             where e.organization_id = v_org
               and e.operation_type = 'INVOICE_RETURN_POSTED'
               and e.payload->>'returnId' = v_payload->>'returnId'
               and e.write_id <> p_write_id
        ) then
            return query select p_event_id, p_write_id, p_aggregate_id,
                coalesce(v_last.server_revision, 0), coalesce(v_last.server_recorded_at, 0),
                'CONFLICT'::text, false, 'return identity already belongs to another write'::text;
            return;
        end if;

        if exists (
            select 1
              from (
                    select line->>'originalInvoiceItemId' as original_item_id,
                           sum((line->>'quantity')::integer) as current_quantity
                      from jsonb_array_elements(v_payload->'lines') line
                     group by line->>'originalInvoiceItemId'
              ) current_return
              join public.invoice_items ii
                on ii.id::text = current_return.original_item_id
               and ii.invoice_id::text = p_aggregate_id
             where coalesce((
                    select sum((prior_line->>'quantity')::integer)
                      from public.financial_sync_events prior_event
                      cross join lateral jsonb_array_elements(prior_event.payload->'lines') prior_line
                     where prior_event.organization_id = v_org
                       and prior_event.aggregate_id = p_aggregate_id
                       and prior_event.operation_type = 'INVOICE_RETURN_POSTED'
                       and prior_line->>'originalInvoiceItemId' = current_return.original_item_id
                ), 0) + current_return.current_quantity > ii.quantity
        ) then
            return query select p_event_id, p_write_id, p_aggregate_id,
                coalesce(v_last.server_revision, 0), coalesce(v_last.server_recorded_at, 0),
                'CONFLICT'::text, false, 'return quantity exceeds original invoice item'::text;
            return;
        end if;

        -- International purchase invoices do not post stock themselves. A goods return may only
        -- reverse quantity that has actually been accepted and posted by Logistics receiving.
        if v_payload->>'documentType' = 'PURCHASE_RETURN_DEBIT_NOTE' and exists (
            select 1
              from (
                    select line->>'originalInvoiceItemId' as original_item_id,
                           sum((line->>'quantity')::bigint) as current_quantity
                      from jsonb_array_elements(v_payload->'lines') line
                     group by line->>'originalInvoiceItemId'
              ) current_return
              join public.invoice_items ii
                on ii.id::text = current_return.original_item_id
               and ii.invoice_id::text = p_aggregate_id
              join public.invoices i
                on i.organization_id = v_org
               and i.id::text = p_aggregate_id
             where i.purchase_scope::text = 'INTERNATIONAL'
               and coalesce((
                    select sum((prior_line->>'quantity')::bigint)
                      from public.financial_sync_events prior_event
                      cross join lateral jsonb_array_elements(prior_event.payload->'lines') prior_line
                     where prior_event.organization_id = v_org
                       and prior_event.aggregate_id = p_aggregate_id
                       and prior_event.operation_type = 'INVOICE_RETURN_POSTED'
                       and prior_line->>'originalInvoiceItemId' = current_return.original_item_id
               ), 0) + current_return.current_quantity > coalesce((
                    select sum(posting.quantity)::bigint
                      from public.logistics_inventory_postings posting
                      join public.logistics_receiving_lines receiving
                        on receiving.organization_id = posting.organization_id
                       and receiving.id = posting.receiving_line_id
                      join public.logistics_shipment_lines shipment_line
                        on shipment_line.organization_id = receiving.organization_id
                       and shipment_line.id = receiving.shipment_line_id
                      join public.logistics_shipments shipment
                        on shipment.organization_id = shipment_line.organization_id
                       and shipment.id = shipment_line.shipment_id
                     where posting.organization_id = v_org
                       and shipment_line.source_invoice_id = p_aggregate_id
                       and shipment_line.source_invoice_item_id = current_return.original_item_id
                       and shipment_line.inventory_item_id = coalesce(ii.inventory_item_id::text, '')
                       and posting.quantity > 0
                       and shipment.cancelled_at is null
                       and upper(shipment.state) not in ('CANCELLED','CANCELED')
               ), 0)
        ) then
            return query select p_event_id, p_write_id, p_aggregate_id,
                coalesce(v_last.server_revision, 0), coalesce(v_last.server_recorded_at, 0),
                'CONFLICT'::text, false, 'international purchase return exceeds posted receipt quantity'::text;
            return;
        end if;

        if jsonb_typeof(coalesce(v_payload->'paymentAllocations', '[]'::jsonb)) <> 'array' then
            raise exception 'invoice return paymentAllocations must be an array';
        end if;

        if exists (
            select 1
              from jsonb_array_elements(coalesce(v_payload->'paymentAllocations', '[]'::jsonb)) alloc
             where coalesce(alloc->>'id', '') = ''
                or coalesce(alloc->>'paymentId', '') = ''
                or coalesce((alloc->>'allocatedFunctionalAmountMinor')::bigint, 0) <= 0
                or not exists (
                    select 1 from public.payments p
                     where p.organization_id = v_org
                       and p.id::text = alloc->>'paymentId'
                       and p.invoice_id::text = p_aggregate_id
                       and p.legacy_currency_status::text = 'KNOWN'
                       and not exists (
                           select 1 from public.payments reversal
                            where reversal.organization_id = v_org
                              and reversal.reversed_payment_id::text = p.id::text
                       )
                       and not exists (
                           select 1 from public.financial_sync_events reversal_event
                            where reversal_event.organization_id = v_org
                              and reversal_event.aggregate_id = p_aggregate_id
                              and reversal_event.operation_type = 'PAYMENT_REVERSED'
                              and reversal_event.payload->>'reversedPaymentId' = p.id::text
                       )
                )
        ) then
            raise exception 'invalid invoice return payment allocation';
        end if;

        if exists (
            select 1
              from jsonb_array_elements(coalesce(v_payload->'paymentAllocations', '[]'::jsonb)) alloc
             group by alloc->>'paymentId'
            having count(*) > 1
        ) or exists (
            select 1
              from jsonb_array_elements(coalesce(v_payload->'paymentAllocations', '[]'::jsonb)) alloc
             group by alloc->>'id'
            having count(*) > 1
        ) then
            raise exception 'duplicate invoice return payment allocation identity';
        end if;

        if coalesce((
            select sum((alloc->>'allocatedFunctionalAmountMinor')::bigint)
              from jsonb_array_elements(coalesce(v_payload->'paymentAllocations', '[]'::jsonb)) alloc
        ), 0) > (v_payload->>'functionalAmountMinor')::bigint then
            raise exception 'return allocations exceed return functional amount';
        end if;

        if v_payload->>'settlementMode' = 'CASH_REFUND' and coalesce((
            select sum((alloc->>'allocatedFunctionalAmountMinor')::bigint)
              from jsonb_array_elements(coalesce(v_payload->'paymentAllocations', '[]'::jsonb)) alloc
        ), 0) <> (v_payload->>'functionalAmountMinor')::bigint then
            return query select p_event_id, p_write_id, p_aggregate_id,
                coalesce(v_last.server_revision, 0), coalesce(v_last.server_recorded_at, 0),
                'CONFLICT'::text, false, 'cash return exceeds effective paid amount'::text;
            return;
        end if;

        if exists (
            select 1
              from (
                    select alloc->>'paymentId' as payment_id,
                           sum((alloc->>'allocatedFunctionalAmountMinor')::bigint) as current_allocated
                      from jsonb_array_elements(coalesce(v_payload->'paymentAllocations', '[]'::jsonb)) alloc
                     group by alloc->>'paymentId'
              ) current_alloc
              join public.payments p
                on p.organization_id = v_org
               and p.id::text = current_alloc.payment_id
               and p.invoice_id::text = p_aggregate_id
             where coalesce((
                    select sum((prior_alloc->>'allocatedFunctionalAmountMinor')::bigint)
                      from public.financial_sync_events prior_event
                      cross join lateral jsonb_array_elements(
                          coalesce(prior_event.payload->'paymentAllocations', '[]'::jsonb)
                      ) prior_alloc
                     where prior_event.organization_id = v_org
                       and prior_event.aggregate_id = p_aggregate_id
                       and prior_event.operation_type = 'INVOICE_RETURN_POSTED'
                       and prior_alloc->>'paymentId' = current_alloc.payment_id
                ), 0) + current_alloc.current_allocated > case
                    when coalesce(p.functional_cash_amount_minor, 0) > 0 then p.functional_cash_amount_minor
                    when coalesce(p.historical_functional_amount_minor, 0) > 0 then p.historical_functional_amount_minor
                    else p.amount_minor
                end
        ) then
            return query select p_event_id, p_write_id, p_aggregate_id,
                coalesce(v_last.server_revision, 0), coalesce(v_last.server_recorded_at, 0),
                'CONFLICT'::text, false, 'return payment allocation exceeds effective payment'::text;
            return;
        end if;
    end if;

    -- Payment reversal must reference an existing payment row or a preceding PAYMENT_RECORDED event.
    if p_operation_type = 'PAYMENT_REVERSED' then
        if coalesce(v_payload->>'reversedPaymentId', '') = '' then
            raise exception 'payment reversal requires reversedPaymentId';
        end if;
        if not exists (
            select 1 from public.payments p
             where p.organization_id = v_org and p.id::text = v_payload->>'reversedPaymentId'
        ) and not exists (
            select 1 from public.financial_sync_events e
             where e.organization_id = v_org and e.aggregate_id = p_aggregate_id
               and e.operation_type = 'PAYMENT_RECORDED'
               and e.payload->>'paymentId' = v_payload->>'reversedPaymentId'
        ) then
            return query select p_event_id, p_write_id, p_aggregate_id,
                coalesce(v_last.server_revision, 0), coalesce(v_last.server_recorded_at, 0),
                'WAITING_DEPENDENCY'::text, false, 'original payment has not arrived'::text;
            return;
        end if;
    end if;

    begin
        insert into public.financial_sync_events (
            event_id, organization_id, write_id, aggregate_id, aggregate_version,
            aggregate_sequence, operation_type, payload_version, schema_version, payload,
            occurred_at, recorded_at, created_by
        ) values (
            p_event_id, v_org, p_write_id, p_aggregate_id, p_aggregate_version,
            p_aggregate_sequence, p_operation_type, p_payload_version, p_schema_version, v_payload,
            p_occurred_at, p_recorded_at, auth.uid()
        )
        returning * into v_existing;
    exception when unique_violation then
        -- A concurrent device won the aggregate sequence or idempotency race. Re-check identity so
        -- retries get the same answer and competing operations become an explicit review conflict.
        select * into v_existing from public.financial_sync_events e
         where e.organization_id = v_org and e.operation_type = p_operation_type and e.write_id = p_write_id
         limit 1;
        if found and v_existing.event_id = p_event_id and v_existing.aggregate_id = p_aggregate_id then
            return query select v_existing.event_id, v_existing.write_id, v_existing.aggregate_id,
                v_existing.server_revision, v_existing.server_recorded_at,
                'REPLAYED'::text, true, ''::text;
        else
            return query select p_event_id, p_write_id, p_aggregate_id, 0::bigint, 0::bigint,
                'CONFLICT'::text, false, 'concurrent aggregate event conflict'::text;
        end if;
        return;
    end;

    return query select v_existing.event_id, v_existing.write_id, v_existing.aggregate_id,
        v_existing.server_revision, v_existing.server_recorded_at,
        'APPLIED'::text, false, ''::text;
end;
$$;

revoke all on function public.financial_sync_apply_event_v1(text,text,text,integer,bigint,text,integer,integer,text,bigint,bigint) from public;
grant execute on function public.financial_sync_apply_event_v1(text,text,text,integer,bigint,text,integer,integer,text,bigint,bigint) to authenticated;
