create table if not exists verto_internal.sync_v2_migration_state (
    organization_id uuid primary key references public.organizations(id) on delete cascade,
    first_v2_write_at timestamptz,
    first_v2_write_marker text,
    migrated_at timestamptz,
    migration_version integer,
    safe_to_project_to_legacy boolean not null default true,
    created_at timestamptz not null default clock_timestamp(),
    updated_at timestamptz not null default clock_timestamp(),
    constraint sync_v2_migration_state_first_write_pair_chk check ((first_v2_write_at is null) = (first_v2_write_marker is null)),
    constraint sync_v2_migration_state_version_chk check (migration_version is null or migration_version > 0),
    constraint sync_v2_migration_state_no_legacy_after_v2_chk check (first_v2_write_at is null or safe_to_project_to_legacy = false)
);

revoke all on table verto_internal.sync_v2_migration_state from public, anon, authenticated;

create or replace function verto_internal.capture_first_v2_write_m08()
returns trigger
language plpgsql
security definer
set search_path = pg_catalog, public, verto_internal
as $$
begin
  if new.status <> 'APPLIED' then
    return new;
  end if;

  insert into verto_internal.sync_v2_migration_state (
      organization_id,
      first_v2_write_at,
      first_v2_write_marker,
      safe_to_project_to_legacy,
      updated_at
  ) values (
      new.organization_id,
      new.created_at,
      'receipt:' || new.mutation_id,
      false,
      clock_timestamp()
  )
  on conflict (organization_id) do update
  set first_v2_write_at = coalesce(verto_internal.sync_v2_migration_state.first_v2_write_at, excluded.first_v2_write_at),
      first_v2_write_marker = coalesce(verto_internal.sync_v2_migration_state.first_v2_write_marker, excluded.first_v2_write_marker),
      safe_to_project_to_legacy = case
          when verto_internal.sync_v2_migration_state.first_v2_write_at is null then false
          else verto_internal.sync_v2_migration_state.safe_to_project_to_legacy
      end,
      updated_at = case
          when verto_internal.sync_v2_migration_state.first_v2_write_at is null then clock_timestamp()
          else verto_internal.sync_v2_migration_state.updated_at
      end;

  return new;
end;
$$;

revoke all on function verto_internal.capture_first_v2_write_m08() from public, anon, authenticated;

drop trigger if exists trg_capture_first_v2_write_m08 on public.verto_sync_receipts;
create trigger trg_capture_first_v2_write_m08
after insert on public.verto_sync_receipts
for each row execute function verto_internal.capture_first_v2_write_m08();

insert into verto_internal.sync_v2_migration_state (
    organization_id,
    first_v2_write_at,
    first_v2_write_marker,
    safe_to_project_to_legacy,
    updated_at
)
select distinct on (r.organization_id)
    r.organization_id,
    r.created_at,
    'receipt:' || r.mutation_id,
    false,
    clock_timestamp()
from public.verto_sync_receipts r
where r.status = 'APPLIED'
order by r.organization_id, r.created_at asc, r.mutation_id asc
on conflict (organization_id) do update
set first_v2_write_at = coalesce(verto_internal.sync_v2_migration_state.first_v2_write_at, excluded.first_v2_write_at),
    first_v2_write_marker = coalesce(verto_internal.sync_v2_migration_state.first_v2_write_marker, excluded.first_v2_write_marker),
    safe_to_project_to_legacy = case
        when verto_internal.sync_v2_migration_state.first_v2_write_at is null then false
        else verto_internal.sync_v2_migration_state.safe_to_project_to_legacy
    end,
    updated_at = case
        when verto_internal.sync_v2_migration_state.first_v2_write_at is null then clock_timestamp()
        else verto_internal.sync_v2_migration_state.updated_at
    end;

create or replace function verto_internal.mark_sync_v2_migrated_m08(
    p_organization_id uuid,
    p_migration_version integer
)
returns void
language plpgsql
security definer
set search_path = pg_catalog, public, verto_internal
as $$
begin
  if p_migration_version is null or p_migration_version <= 0 then
    raise exception 'positive migration version required' using errcode = '22023';
  end if;

  update verto_internal.sync_v2_migration_state
  set migrated_at = coalesce(migrated_at, clock_timestamp()),
      migration_version = case
          when migration_version is null then p_migration_version
          when migration_version = p_migration_version then migration_version
          else migration_version
      end,
      safe_to_project_to_legacy = false,
      updated_at = clock_timestamp()
  where organization_id = p_organization_id
    and first_v2_write_marker is not null;

  if not found then
    raise exception 'organization has no durable first V2 write marker' using errcode = '55000';
  end if;

  if exists (
      select 1 from verto_internal.sync_v2_migration_state
      where organization_id = p_organization_id
        and migration_version <> p_migration_version
  ) then
    raise exception 'organization already has a different migration version' using errcode = '55000';
  end if;
end;
$$;

revoke all on function verto_internal.mark_sync_v2_migrated_m08(uuid, integer) from public, anon, authenticated;
