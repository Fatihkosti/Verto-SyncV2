-- Verto F248 — invoice lifecycle, optimistic versioning and immutable posting snapshots.
-- Additive contract; apply before enabling F248 lifecycle sync.

alter table public.invoices
    add column if not exists lifecycle_status text not null default 'POSTED',
    add column if not exists lifecycle_version integer not null default 1,
    add column if not exists posted_at bigint not null default 0,
    add column if not exists voided_at bigint not null default 0,
    add column if not exists void_reason text not null default '',
    add column if not exists void_write_id text not null default '';

update public.invoices
set lifecycle_status = case when coalesce(voided, false) then 'VOID' else 'POSTED' end,
    posted_at = case
        when not coalesce(voided, false) and posted_at = 0
        then (extract(epoch from created_at) * 1000)::bigint
        else posted_at
    end;

alter table public.invoices drop constraint if exists invoices_lifecycle_status_check;
alter table public.invoices add constraint invoices_lifecycle_status_check
    check (lifecycle_status in ('DRAFT','POSTED','VOID'));
alter table public.invoices drop constraint if exists invoices_lifecycle_version_check;
alter table public.invoices add constraint invoices_lifecycle_version_check
    check (lifecycle_version >= 1);

create unique index if not exists uq_invoices_void_write_id
    on public.invoices (organization_id, void_write_id)
    where void_write_id <> '';

alter table public.invoice_items
    add column if not exists item_sku_snapshot text not null default '',
    add column if not exists unit_snapshot text not null default '';

-- One original payment may have at most one reversal row across all devices.
create unique index if not exists uq_payments_reversed_payment_once
    on public.payments (reversed_payment_id)
    where reversed_payment_id is not null;

create or replace function public.verto_prevent_posted_invoice_delete()
returns trigger
language plpgsql
as $$
begin
    if old.lifecycle_status <> 'DRAFT' then
        raise exception 'posted invoices must be voided, not deleted';
    end if;
    return old;
end;
$$;

drop trigger if exists trg_prevent_posted_invoice_delete on public.invoices;
create trigger trg_prevent_posted_invoice_delete
before delete on public.invoices
for each row execute function public.verto_prevent_posted_invoice_delete();
