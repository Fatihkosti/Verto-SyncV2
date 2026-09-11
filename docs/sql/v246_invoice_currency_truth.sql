-- Verto v246 — immutable invoice/payment currency truth.
-- Additive and idempotent. Legacy international rows remain UNKNOWN until reviewed.
begin;

alter table if exists public.invoices
  add column if not exists transaction_currency_code text not null default '',
  add column if not exists functional_currency_code text not null default '',
  add column if not exists transaction_amount_minor bigint not null default 0,
  add column if not exists invoice_exchange_rate_snapshot text not null default '',
  add column if not exists exchange_rate_direction text not null default 'FUNCTIONAL_PER_TRANSACTION',
  add column if not exists exchange_rate_timestamp bigint not null default 0,
  add column if not exists exchange_rate_source text not null default '',
  add column if not exists functional_amount_at_recognition_minor bigint not null default 0,
  add column if not exists legacy_currency_status text not null default 'REVIEW_REQUIRED';

alter table if exists public.payments
  add column if not exists payment_currency_code text not null default '',
  add column if not exists supplier_amount_minor bigint not null default 0,
  add column if not exists payment_exchange_rate text not null default '',
  add column if not exists payment_exchange_rate_direction text not null default 'FUNCTIONAL_PER_TRANSACTION',
  add column if not exists payment_exchange_rate_timestamp bigint not null default 0,
  add column if not exists payment_exchange_rate_source text not null default '',
  add column if not exists functional_cash_amount_minor bigint not null default 0,
  add column if not exists historical_functional_amount_minor bigint not null default 0,
  add column if not exists realized_fx_difference_minor bigint not null default 0,
  add column if not exists legacy_currency_status text not null default 'REVIEW_REQUIRED';

update public.invoices
set legacy_currency_status = 'UNKNOWN'
where purchase_scope = 'INTERNATIONAL'
  and legacy_currency_status = 'REVIEW_REQUIRED'
  and (transaction_currency_code = '' or functional_currency_code = '' or invoice_exchange_rate_snapshot = '');

update public.payments p
set legacy_currency_status = 'UNKNOWN'
where exists (
  select 1 from public.invoices i
  where i.id = p.invoice_id and i.legacy_currency_status = 'UNKNOWN'
);

alter table if exists public.invoices drop constraint if exists invoices_legacy_currency_status_check;
alter table if exists public.invoices add constraint invoices_legacy_currency_status_check
  check (legacy_currency_status in ('KNOWN','UNKNOWN','REVIEW_REQUIRED'));
alter table if exists public.payments drop constraint if exists payments_legacy_currency_status_check;
alter table if exists public.payments add constraint payments_legacy_currency_status_check
  check (legacy_currency_status in ('KNOWN','UNKNOWN','REVIEW_REQUIRED'));

create table if not exists public.payment_allocations (
  organization_id uuid not null,
  id text not null,
  payment_id text not null,
  invoice_id text not null,
  allocated_transaction_amount_minor bigint not null,
  historical_functional_amount_minor bigint not null,
  realized_fx_difference_minor bigint not null default 0,
  created_at timestamptz not null,
  write_id text not null default '',
  primary key (id),
  unique (organization_id, payment_id, invoice_id)
);

create table if not exists public.realized_fx_events (
  organization_id uuid not null,
  id text not null,
  payment_id text not null,
  invoice_id text not null,
  functional_currency_code text not null,
  historical_functional_amount_minor bigint not null,
  functional_cash_amount_minor bigint not null,
  difference_minor bigint not null check (difference_minor >= 0),
  result text not null check (result in ('GAIN','LOSS','NONE')),
  occurred_at timestamptz not null,
  write_id text not null default '',
  primary key (id),
  unique (organization_id, payment_id)
);

create index if not exists payment_allocations_org_invoice_idx
  on public.payment_allocations(organization_id, invoice_id);
create index if not exists realized_fx_events_org_invoice_idx
  on public.realized_fx_events(organization_id, invoice_id);

-- Tenant isolation for the two new immutable fact tables.
do $$
declare t text; p text;
begin
  foreach t in array array['payment_allocations','realized_fx_events'] loop
    execute format('alter table public.%I enable row level security', t);
    execute format('revoke all on public.%I from anon', t);
    execute format('grant select, insert, update on public.%I to authenticated', t);

    p := t || '_org_select';
    execute format('drop policy if exists %I on public.%I', p, t);
    execute format(
      'create policy %I on public.%I for select to authenticated using (organization_id = (select organization_id from public.app_users where id = auth.uid() and coalesce(is_active,true) limit 1))',
      p, t
    );

    p := t || '_org_insert';
    execute format('drop policy if exists %I on public.%I', p, t);
    execute format(
      'create policy %I on public.%I for insert to authenticated with check (organization_id = (select organization_id from public.app_users where id = auth.uid() and coalesce(is_active,true) limit 1))',
      p, t
    );

    p := t || '_org_update';
    execute format('drop policy if exists %I on public.%I', p, t);
    execute format(
      'create policy %I on public.%I for update to authenticated using (organization_id = (select organization_id from public.app_users where id = auth.uid() and coalesce(is_active,true) limit 1)) with check (organization_id = (select organization_id from public.app_users where id = auth.uid() and coalesce(is_active,true) limit 1))',
      p, t
    );
  end loop;
end $$;

commit;
