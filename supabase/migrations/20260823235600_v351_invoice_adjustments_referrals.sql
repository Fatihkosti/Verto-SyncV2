-- Verto 351 — invoice discount + commission/referral attribution.
-- The mobile UI stays simple; the server keeps beneficiary/accounting truth explicit.

alter table public.invoices
  add column if not exists discount numeric not null default 0,
  add column if not exists discount_minor bigint not null default 0,
  add column if not exists commission_beneficiary_client_id uuid null references public.clients(id),
  add column if not exists commission_source text not null default 'NONE';

alter table public.invoices drop constraint if exists invoices_discount_nonnegative_v351;
alter table public.invoices add constraint invoices_discount_nonnegative_v351 check (discount >= 0);
alter table public.invoices drop constraint if exists invoices_commission_source_v351;
alter table public.invoices add constraint invoices_commission_source_v351
  check (commission_source in ('NONE','BUYER','REFERRER'));
create index if not exists idx_invoices_commission_beneficiary_v351
  on public.invoices (commission_beneficiary_client_id)
  where commission_beneficiary_client_id is not null;

-- Existing positive commissions belonged to the buyer under the old model.
update public.invoices
set commission_beneficiary_client_id = client_id,
    commission_source = 'BUYER',
    discount_minor = round(coalesce(discount,0) * 100)::bigint
where category = 'SALE'
  and coalesce(commission,0) > 0
  and commission_beneficiary_client_id is null;

create or replace function public.enforce_invoice_sensitive_permissions()
returns trigger
language plpgsql
set search_path to 'public'
as $function$
begin
  if current_user in ('service_role','postgres','supabase_admin') then
    if tg_op = 'DELETE' then return old; end if;
    return new;
  end if;

  if tg_op = 'INSERT' then
    if (coalesce(new.commission,0) <> 0
        or new.commission_beneficiary_client_id is not null
        or coalesce(new.commission_source,'NONE') <> 'NONE')
       and not public.has_employee_permission('commission_manage') then
      raise exception 'لا تملك صلاحية إدارة العمولات';
    end if;
    return new;
  end if;

  if tg_op = 'UPDATE' then
    if (old.commission is distinct from new.commission
        or old.commission_beneficiary_client_id is distinct from new.commission_beneficiary_client_id
        or old.commission_source is distinct from new.commission_source)
       and not public.has_employee_permission('commission_manage') then
      raise exception 'لا تملك صلاحية إدارة العمولات';
    end if;

    if old.commission is distinct from new.commission
       or old.commission_beneficiary_client_id is distinct from new.commission_beneficiary_client_id
       or old.commission_source is distinct from new.commission_source then
      if exists (
        select 1 from public.commission_ledger cl
        where cl.invoice_id = old.id and cl.status in ('WITHDRAWABLE','PAID_OUT')
      ) then
        raise exception 'COMMISSION_SETTLED_LOCKED';
      end if;
    end if;

    if old.voided is distinct from new.voided
       and not public.can_delete_invoice_category(coalesce(old.category,new.category)) then
      raise exception 'لا تملك صلاحية حذف الفواتير';
    end if;
    return new;
  end if;

  if tg_op = 'DELETE' then
    if not public.can_delete_invoice_category(old.category) then
      raise exception 'لا تملك صلاحية حذف الفواتير';
    end if;
    return old;
  end if;
  return null;
end;
$function$;

create or replace function public.sync_commission_ledger_on_invoice()
returns trigger
language plpgsql
security definer
set search_path to 'public'
as $function$
declare
  v_status text;
  v_beneficiary uuid;
begin
  if coalesce(new.commission,0) <= 0 or new.category <> 'SALE' then
    delete from public.commission_ledger
     where invoice_id = new.id and status not in ('WITHDRAWABLE','PAID_OUT');
    return new;
  end if;

  v_beneficiary := coalesce(new.commission_beneficiary_client_id,new.client_id);
  if v_beneficiary is null then
    raise exception 'COMMISSION_BENEFICIARY_REQUIRED';
  end if;
  if not exists (
    select 1 from public.clients c
    where c.id = v_beneficiary
      and (coalesce(c.client_types,'') like '%MARKETER%'
           or coalesce(c.client_types,'') like '%WORKSHOP_OWNER%')
  ) then
    raise exception 'COMMISSION_BENEFICIARY_NOT_MARKETING_PARTY';
  end if;

  v_status := case when new.status = 'CLOSED_CASH' then 'EARNED' else 'PENDING' end;
  insert into public.commission_ledger(
    invoice_id,client_id,org_id,amount,credited_amount,paid_out_amount,status,last_event_at
  ) values (
    new.id,v_beneficiary,new.organization_id,new.commission,0,0,v_status,now()
  )
  on conflict (invoice_id) do update
  set client_id = case when commission_ledger.status in ('WITHDRAWABLE','PAID_OUT')
                       then commission_ledger.client_id else excluded.client_id end,
      amount = case when commission_ledger.status in ('WITHDRAWABLE','PAID_OUT')
                    then commission_ledger.amount else excluded.amount end,
      status = case when commission_ledger.status in ('WITHDRAWABLE','PAID_OUT')
                    then commission_ledger.status else excluded.status end,
      last_event_at = now();

  perform public.promote_due_commissions_to_balance(new.id);
  return new;
end;
$function$;

create or replace view public.commission_eligibility
with (security_invoker = true)
as
select
  i.id as invoice_id,
  coalesce(cl.client_id,i.commission_beneficiary_client_id,i.client_id) as client_id,
  coalesce(cl.org_id,i.organization_id) as org_id,
  i.commission,
  i.invoice_number,
  i.total_amount,
  i.status as invoice_status,
  i.category,
  i.created_at,
  cl.status as ledger_status,
  cl.paid_out_amount,
  coalesce(sum(p.amount),0::numeric) as payments_sum,
  public.last_friday_9am() as week_start,
  case
    when cl.status = 'PAID_OUT' then 'PAID'
    when cl.status = 'WITHDRAWABLE' then 'WITHDRAWABLE'
    when i.created_at < public.last_friday_9am() and i.status = 'CLOSED_CASH' then 'WITHDRAWABLE'
    when i.created_at < public.last_friday_9am() and i.status = 'CLOSED_CREDIT'
         and coalesce(sum(p.amount),0::numeric) >= i.total_amount then 'WITHDRAWABLE'
    else 'PENDING'
  end as eligibility
from public.invoices i
left join public.commission_ledger cl on cl.invoice_id = i.id
left join public.payments p on p.invoice_id = i.id
where i.category = 'SALE' and i.commission > 0
  and coalesce(i.voided,false) = false
group by i.id,i.client_id,i.commission_beneficiary_client_id,i.organization_id,i.commission,
         i.invoice_number,i.total_amount,i.status,i.category,i.created_at,
         cl.status,cl.paid_out_amount,cl.org_id,cl.client_id;

-- Trigger notification resolves the marketing beneficiary, not necessarily the buyer.
create or replace function public.notify_admins_commission_needed()
returns trigger
language plpgsql
security definer
set search_path to 'public'
as $function$
declare
  v_beneficiary uuid;
  v_client_name text;
  v_client_types text;
  v_creator_role text;
begin
  if new.category <> 'SALE' then return new; end if;
  v_beneficiary := coalesce(new.commission_beneficiary_client_id,new.client_id);
  select name,client_types into v_client_name,v_client_types from public.clients where id=v_beneficiary;
  if v_client_types is null or (v_client_types not like '%MARKETER%' and v_client_types not like '%WORKSHOP_OWNER%') then
    return new;
  end if;
  select role into v_creator_role from public.app_users where id=new.created_by;
  if v_creator_role='admin' then return new; end if;
  insert into public.notifications(user_id,client_id,org_id,type,title,body,data)
  select u.id,v_beneficiary,new.organization_id,'ADMIN_COMMISSION_NEEDED','فاتورة بحاجة لعمولة',
         format('تم إضافة فاتورة مرتبطة بـ %s — اضغط لإضافة عمولة',coalesce(v_client_name,'طرف تسويقي')),
         jsonb_build_object('invoice_id',new.id,'client_name',coalesce(v_client_name,''),'beneficiary_client_id',v_beneficiary)
  from public.app_users u
  where u.organization_id=new.organization_id and u.role='admin' and u.is_active=true;
  return new;
exception when others then
  raise warning 'notify_admins_commission_needed failed: %',sqlerrm;
  return new;
end;
$function$;
