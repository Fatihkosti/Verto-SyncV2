-- Withdrawal approval is a server-owned transition.
-- The Android client must call this RPC; it must not update withdrawal_requests.status.
create or replace function public.approve_withdrawal(
    p_withdrawal_id uuid,
    p_transaction_ref text default '',
    p_client_request_id text default null
)
returns void
language plpgsql
security definer
set search_path = public, pg_temp
as $$
declare
    v_withdrawal public.withdrawal_requests%rowtype;
    v_role text := public.get_my_role();
begin
    if coalesce(v_role, '') not in ('admin', 'accountant')
       and not coalesce(public.has_employee_permission('commission_manage'), false) then
        raise exception 'PERMISSION_DENIED' using errcode = '42501';
    end if;

    select *
      into v_withdrawal
      from public.withdrawal_requests
     where id = p_withdrawal_id
       and org_id = public.get_my_org_id()
     for update;

    if not found then
        raise exception 'WITHDRAWAL_NOT_FOUND_OR_ALREADY_PROCESSED';
    end if;

    -- The same request key is accepted on every retry. It is never replaced.
    if p_client_request_id is not null
       and v_withdrawal.client_request_id is distinct from p_client_request_id then
        raise exception 'CLIENT_REQUEST_ID_MISMATCH';
    end if;

    -- A repeated approval, or a request completed by a prior attempt, is idempotent.
    if v_withdrawal.status in ('APPROVED', 'COMPLETED') then
        return;
    end if;

    if v_withdrawal.status <> 'PENDING' then
        raise exception 'WITHDRAWAL_INVALID_TRANSITION';
    end if;

    update public.withdrawal_requests
       set status = 'APPROVED',
           transaction_ref = coalesce(p_transaction_ref, ''),
           processed_at = now(),
           processed_by = auth.uid()
     where id = p_withdrawal_id;
end;
$$;

revoke all on function public.approve_withdrawal(uuid, text, text) from public, anon;
grant execute on function public.approve_withdrawal(uuid, text, text) to authenticated;
