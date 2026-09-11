-- Verto v262-v266 — atomic inventory command ingestion, cursor pull, cutover and observability.
-- Prerequisite: v257 + v258. Apply backend-first before enabling client contract v2.

alter table public.inventory_items add column if not exists is_archived boolean not null default false;
alter table public.inventory_items add column if not exists archived_at timestamptz;
alter table public.inventory_items add column if not exists archived_by uuid;
-- Metadata-only clients never send a stock snapshot. New rows therefore start
-- at zero and are changed exclusively by the command RPC below.
alter table public.inventory_items alter column quantity set default 0;
alter table public.inventory_movements enable row level security;
alter table public.inventory_cost_revisions enable row level security;
revoke insert, update, delete on public.inventory_movements from authenticated;
revoke insert, update, delete on public.inventory_cost_revisions from authenticated;
drop policy if exists inventory_movements_v2_org_read on public.inventory_movements;
create policy inventory_movements_v2_org_read on public.inventory_movements for select
using (organization_id = public.get_my_org_id());
drop policy if exists inventory_cost_revisions_v2_org_read on public.inventory_cost_revisions;
create policy inventory_cost_revisions_v2_org_read on public.inventory_cost_revisions for select
using (organization_id = public.get_my_org_id());

create table if not exists public.inventory_contract_control (
    organization_id uuid primary key,
    minimum_contract_version integer not null default 1,
    reject_snapshot_writes boolean not null default false,
    writes_enabled boolean not null default true,
    updated_at timestamptz not null default now()
);

create table if not exists public.inventory_server_sequences (
    organization_id uuid primary key,
    last_sequence bigint not null default 0
);

create table if not exists public.inventory_sync_conflicts (
    id text primary key,
    organization_id uuid not null,
    conflict_key text not null,
    item_id text not null,
    conflict_type text not null check (conflict_type in ('OVERSOLD_CONFLICT','COST_ORDER_CONFLICT')),
    server_sequence bigint not null,
    projected_quantity bigint not null,
    status text not null default 'OPEN' check (status in ('OPEN','RESOLVED')),
    details jsonb not null default '{}'::jsonb,
    detected_at timestamptz not null default now(),
    resolved_at timestamptz,
    unique (organization_id, conflict_key)
);

create table if not exists public.inventory_command_quarantine (
    id text primary key,
    organization_id uuid not null,
    command_id text not null,
    item_id text,
    reason text not null,
    payload jsonb not null,
    detected_at timestamptz not null default now(),
    resolved_at timestamptz,
    unique (organization_id, command_id)
);

alter table public.inventory_contract_control enable row level security;
alter table public.inventory_sync_conflicts enable row level security;
alter table public.inventory_command_quarantine enable row level security;
drop policy if exists inventory_contract_control_read on public.inventory_contract_control;
create policy inventory_contract_control_read on public.inventory_contract_control for select
using (organization_id = public.get_my_org_id());
drop policy if exists inventory_conflicts_org_read on public.inventory_sync_conflicts;
create policy inventory_conflicts_org_read on public.inventory_sync_conflicts for select
using (organization_id = public.get_my_org_id());
drop policy if exists inventory_quarantine_org_read on public.inventory_command_quarantine;
create policy inventory_quarantine_org_read on public.inventory_command_quarantine for select
using (organization_id = public.get_my_org_id());

create or replace function public.inventory_guard_snapshot_write_v2()
returns trigger language plpgsql security definer set search_path = public as $$
declare v_control public.inventory_contract_control%rowtype;
begin
  if new.quantity is not distinct from old.quantity then return new; end if;
  select * into v_control from public.inventory_contract_control where organization_id = old.organization_id;
  if coalesce(v_control.reject_snapshot_writes, false)
     and current_setting('verto.inventory_rpc', true) <> 'on' then
    raise exception 'UPGRADE_REQUIRED_INVENTORY_CONTRACT_V2' using errcode = 'P0001';
  end if;
  return new;
end $$;

drop trigger if exists inventory_guard_snapshot_write_v2 on public.inventory_items;
create trigger inventory_guard_snapshot_write_v2 before update of quantity on public.inventory_items
for each row execute function public.inventory_guard_snapshot_write_v2();

create or replace function public.inventory_apply_commands_v2(p_commands jsonb)
returns table(client_outbox_id text, movement_id text, status text, server_sequence bigint,
              projected_quantity bigint, conflict_type text)
language plpgsql security definer set search_path = public as $$
declare
  v_org uuid := public.get_my_org_id();
  v_user uuid := auth.uid();
  v_command jsonb;
  v_item public.inventory_items%rowtype;
  v_existing public.inventory_movements%rowtype;
  v_sequence bigint;
  v_after bigint;
  v_delta bigint;
  v_kind text;
  v_contract integer;
  v_control public.inventory_contract_control%rowtype;
begin
  if v_org is null or v_user is null then raise exception 'AUTH_SESSION_REQUIRED'; end if;
  if jsonb_typeof(p_commands) <> 'array' or jsonb_array_length(p_commands) > 100 then
    raise exception 'INVALID_INVENTORY_BATCH';
  end if;
  select * into v_control from public.inventory_contract_control where organization_id = v_org;
  if found and not v_control.writes_enabled then raise exception 'INVENTORY_WRITES_DISABLED'; end if;

  for v_command in select value from jsonb_array_elements(p_commands) loop
    client_outbox_id := nullif(v_command->>'client_outbox_id','');
    movement_id := nullif(v_command->>'movement_id','');
    v_contract := coalesce((v_command->>'contract_version')::integer, 0);
    v_delta := coalesce((v_command->>'signed_base_quantity')::bigint, 0);
    v_kind := nullif(v_command->>'movement_kind','');
    conflict_type := null;

    if movement_id is null or client_outbox_id is null or v_delta = 0
       or v_contract < greatest(2, coalesce(v_control.minimum_contract_version, 2)) then
      raise exception 'INVALID_OR_OBSOLETE_INVENTORY_COMMAND';
    end if;

    select * into v_existing from public.inventory_movements
      where organization_id=v_org and idempotency_key=v_command->>'idempotency_key';
    if found then
      status := 'DUPLICATE'; server_sequence := v_existing.server_sequence;
      select quantity::bigint into projected_quantity from public.inventory_items
        where organization_id=v_org and id=v_existing.item_id;
      return next; continue;
    end if;

    select * into v_item from public.inventory_items
      where organization_id=v_org and id=v_command->>'item_id' and not is_archived for update;
    if not found then raise exception 'INVENTORY_ITEM_NOT_FOUND_OR_ARCHIVED'; end if;

    if v_kind = 'REVERSAL' and not exists (
      select 1 from public.inventory_movements where organization_id=v_org
        and id=v_command->>'reverses_movement_id' and movement_kind <> 'REVERSAL'
    ) then
      insert into public.inventory_command_quarantine(id,organization_id,command_id,item_id,reason,payload)
      values ('quarantine:'||(v_command->>'command_id'),v_org,v_command->>'command_id',v_item.id,
              'REVERSAL_ORIGINAL_MISSING',v_command)
      on conflict (organization_id,command_id) do nothing;
      status := 'QUARANTINED'; server_sequence := 0; projected_quantity := v_item.quantity;
      return next; continue;
    end if;

    insert into public.inventory_server_sequences(organization_id,last_sequence) values(v_org,1)
    on conflict (organization_id) do update set last_sequence=public.inventory_server_sequences.last_sequence+1
    returning last_sequence into v_sequence;
    v_after := v_item.quantity::bigint + v_delta;
    if v_after < -2147483648 or v_after > 2147483647 then raise exception 'INVENTORY_QUANTITY_OVERFLOW'; end if;
    perform set_config('verto.inventory_rpc','on',true);
    update public.inventory_items set quantity=v_after::integer, updated_at=now()
      where organization_id=v_org and id=v_item.id;

    insert into public.inventory_movements(
      id,organization_id,item_id,invoice_id,client_id,movement_type,quantity,quantity_before,quantity_after,
      unit_price,note,source_type,source_id,source_line_id,command_id,idempotency_key,posting_group_id,
      reverses_movement_id,conversion_factor_snapshot,movement_kind,signed_base_quantity,occurred_at,recorded_at,
      server_accepted_at,server_sequence,created_by,device_id,contract_version,created_at)
    values(
      movement_id,v_org,v_item.id,coalesce(v_command->>'invoice_id',''),nullif(v_command->>'client_id','')::uuid,
      case when v_delta>0 then 'IN' else 'OUT' end,abs(v_delta)::integer,v_item.quantity,v_after::integer,
      coalesce((v_command->>'unit_price_minor')::numeric,0)/100,coalesce(v_command->>'note',''),
      v_command->>'source_type',v_command->>'source_id',v_command->>'source_line_id',v_command->>'command_id',
      v_command->>'idempotency_key',v_command->>'posting_group_id',v_command->>'reverses_movement_id',
      coalesce(v_command->>'conversion_factor_snapshot','1'),v_kind,v_delta,
      (v_command->>'occurred_at')::bigint,(extract(epoch from now())*1000)::bigint,now(),v_sequence,v_user,
      coalesce(v_command->>'device_id','unknown'),v_contract,now());

    if v_after < 0 then
      conflict_type := 'OVERSOLD_CONFLICT';
      insert into public.inventory_sync_conflicts(id,organization_id,conflict_key,item_id,conflict_type,
                                                   server_sequence,projected_quantity,details)
      values ('oversold:'||v_item.id||':'||v_sequence,v_org,'oversold:'||v_item.id||':'||v_sequence,
              v_item.id,conflict_type,v_sequence,v_after,jsonb_build_object('movement_id',movement_id))
      on conflict (organization_id,conflict_key) do nothing;
    end if;
    status := 'APPLIED'; server_sequence := v_sequence; projected_quantity := v_after; return next;
  end loop;
end $$;

revoke all on function public.inventory_apply_commands_v2(jsonb) from public;
grant execute on function public.inventory_apply_commands_v2(jsonb) to authenticated;

create or replace function public.inventory_apply_cost_revisions_v2(p_revisions jsonb)
returns table(client_outbox_id text, cost_revision_id text, status text, cost_sequence bigint)
language plpgsql security definer set search_path=public as $$
declare
  v_org uuid := public.get_my_org_id(); v_user uuid := auth.uid(); v jsonb;
  v_existing public.inventory_cost_revisions%rowtype; v_sequence bigint; v_kind text; v_item text;
begin
  if v_org is null or v_user is null then raise exception 'AUTH_SESSION_REQUIRED'; end if;
  if jsonb_typeof(p_revisions)<>'array' or jsonb_array_length(p_revisions)>100 then raise exception 'INVALID_COST_BATCH'; end if;
  for v in select value from jsonb_array_elements(p_revisions) loop
    client_outbox_id := v->>'client_outbox_id'; cost_revision_id := v->>'cost_revision_id';
    v_kind := v->>'revision_kind'; v_item := v->>'item_id';
    if client_outbox_id is null or cost_revision_id is null or coalesce((v->>'contract_version')::integer,0)<2
       or coalesce((v->>'approved_inventory_cost_minor')::bigint,-1)<0 then
      raise exception 'INVALID_COST_REVISION';
    end if;
    select * into v_existing from public.inventory_cost_revisions
      where organization_id=v_org and idempotency_key=v->>'idempotency_key';
    if found then status:='DUPLICATE'; cost_sequence:=v_existing.cost_sequence; return next; continue; end if;
    if not exists(select 1 from public.inventory_items where organization_id=v_org and id=v_item and not is_archived) then
      raise exception 'INVENTORY_ITEM_NOT_FOUND_OR_ARCHIVED';
    end if;
    if v_kind='REVERSAL' and not exists(select 1 from public.inventory_cost_revisions
      where organization_id=v_org and cost_revision_id=v->>'reverses_cost_revision_id' and revision_kind<>'REVERSAL') then
      status:='QUARANTINED'; cost_sequence:=0; return next; continue;
    end if;
    insert into public.inventory_server_sequences(organization_id,last_sequence) values(v_org,1)
    on conflict (organization_id) do update set last_sequence=public.inventory_server_sequences.last_sequence+1
    returning last_sequence into v_sequence;
    insert into public.inventory_cost_revisions(
      cost_revision_id,organization_id,item_id,source_type,source_id,source_line_id,revision_kind,
      direct_purchase_cost_minor,landed_cost_per_base_unit_minor,approved_inventory_cost_minor,
      currency_code,exchange_rate_snapshot,allocation_basis,allocation_residual_minor,is_provisional,
      reverses_cost_revision_id,command_id,idempotency_key,cost_sequence,approved_at,recorded_at,
      created_by,device_id,contract_version)
    values(cost_revision_id,v_org,v_item,v->>'source_type',v->>'source_id',v->>'source_line_id',v_kind,
      (v->>'direct_purchase_cost_minor')::bigint,(v->>'landed_cost_per_base_unit_minor')::bigint,
      (v->>'approved_inventory_cost_minor')::bigint,v->>'currency_code',(v->>'exchange_rate_snapshot')::numeric,
      coalesce(v->>'allocation_basis',''),coalesce((v->>'allocation_residual_minor')::bigint,0),
      coalesce((v->>'is_provisional')::boolean,false),v->>'reverses_cost_revision_id',v->>'command_id',
      v->>'idempotency_key',v_sequence,(v->>'approved_at')::bigint,(extract(epoch from now())*1000)::bigint,
      v_user,coalesce(v->>'device_id','unknown'),(v->>'contract_version')::integer);
    update public.inventory_items set buy_price=((v->>'approved_inventory_cost_minor')::numeric/100),updated_at=now()
      where organization_id=v_org and id=v_item;
    status:='APPLIED'; cost_sequence:=v_sequence; return next;
  end loop;
end $$;
revoke all on function public.inventory_apply_cost_revisions_v2(jsonb) from public;
grant execute on function public.inventory_apply_cost_revisions_v2(jsonb) to authenticated;

create or replace function public.inventory_pull_cost_revisions_v2(p_after_sequence bigint, p_limit integer default 500)
returns setof public.inventory_cost_revisions language sql security definer set search_path=public as $$
  select r.* from public.inventory_cost_revisions r
  where r.organization_id=public.get_my_org_id() and r.contract_version>=2
    and r.cost_sequence > greatest(coalesce(p_after_sequence,0),0)
  order by r.cost_sequence limit least(greatest(coalesce(p_limit,500),1),1000)
$$;
revoke all on function public.inventory_pull_cost_revisions_v2(bigint,integer) from public;
grant execute on function public.inventory_pull_cost_revisions_v2(bigint,integer) to authenticated;

create or replace function public.inventory_pull_movements_v2(p_after_sequence bigint, p_limit integer default 500)
returns setof public.inventory_movements language sql security definer set search_path=public as $$
  select m.* from public.inventory_movements m
  where m.organization_id=public.get_my_org_id() and m.contract_version>=2
    and m.server_sequence > greatest(coalesce(p_after_sequence,0),0)
  order by m.server_sequence limit least(greatest(coalesce(p_limit,500),1),1000)
$$;
revoke all on function public.inventory_pull_movements_v2(bigint,integer) from public;
grant execute on function public.inventory_pull_movements_v2(bigint,integer) to authenticated;

create or replace function public.archive_inventory_item_v2(p_item_id text)
returns boolean language plpgsql security definer set search_path=public as $$
begin
  update public.inventory_items set is_archived=true,archived_at=now(),archived_by=auth.uid(),updated_at=now()
  where organization_id=public.get_my_org_id() and id=p_item_id and not is_archived;
  return found;
end $$;
revoke all on function public.archive_inventory_item_v2(text) from public;
grant execute on function public.archive_inventory_item_v2(text) to authenticated;

-- Cutover is explicit after reconciliation dry-run:
-- insert into inventory_contract_control(organization_id,minimum_contract_version,reject_snapshot_writes,writes_enabled)
-- values ('ORG_UUID',2,true,true) on conflict (organization_id) do update
-- set minimum_contract_version=2,reject_snapshot_writes=true,writes_enabled=true,updated_at=now();
