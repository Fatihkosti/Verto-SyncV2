-- Notification server contract hardening: permissions, tenant isolation, canonical read state, pagination, idempotency key.

ALTER TABLE public.notifications
    ADD COLUMN IF NOT EXISTS event_key text;

CREATE UNIQUE INDEX IF NOT EXISTS uq_notifications_org_recipient_event_key
    ON public.notifications(org_id, target_user_id, event_key)
    WHERE event_key IS NOT NULL;

ALTER TABLE public.notifications DROP CONSTRAINT IF EXISTS notifications_type_check;
ALTER TABLE public.notifications
    ADD CONSTRAINT notifications_type_check CHECK (type = ANY (ARRAY[
        'GENERIC_NOTIFICATION','TEST_NOTIFICATION','MORNING_GREETING',
        'CREDIT_SALE_INVOICE_CREATED','PAYMENT_RECORDED','PAYMENT_DUE_REMINDER','PAYMENT_OVERDUE',
        'LOW_STOCK','PURCHASE_INVOICE_CREATED','GOODS_RECEIVED',
        'NEW_COMMISSION','COMMISSION_WITHDRAWABLE','COMMISSION_PAID','BALANCE_CREDITED',
        'NEW_INVOICE','WEEK_ENDING_SOON','INACTIVITY','WEEKLY_GOAL_ACHIEVED','NEW_CHAT_MESSAGE',
        'WITHDRAWAL_APPROVED','WITHDRAWAL_REJECTED','WITHDRAWAL_COMPLETED','PROFILE_INCOMPLETE','WELCOME',
        'ADMIN_COMMISSION_NEEDED','WITHDRAWAL_REQUESTED','MARKETER_REGISTERED','ADMIN_REMINDER',
        'AUTODRIVE_JOIN_REQUEST'
    ]::text[]));

DROP POLICY IF EXISTS notifications_visible_update ON public.notifications;
DROP POLICY IF EXISTS notifications_admin_insert ON public.notifications;

DROP POLICY IF EXISTS push_tokens_own ON public.push_tokens;
CREATE POLICY push_tokens_own
ON public.push_tokens
FOR ALL
TO authenticated
USING (
    user_id = (select auth.uid())
    AND (
        EXISTS (
            SELECT 1 FROM public.app_users u
            WHERE u.id = (select auth.uid())
              AND u.organization_id = push_tokens.org_id
              AND COALESCE(u.is_active, true) = true
        )
        OR EXISTS (
            SELECT 1 FROM public.autodrive_users au
            WHERE au.user_id = (select auth.uid())
              AND au.org_id = push_tokens.org_id
        )
    )
)
WITH CHECK (
    user_id = (select auth.uid())
    AND (
        EXISTS (
            SELECT 1 FROM public.app_users u
            WHERE u.id = (select auth.uid())
              AND u.organization_id = push_tokens.org_id
              AND COALESCE(u.is_active, true) = true
        )
        OR EXISTS (
            SELECT 1 FROM public.autodrive_users au
            WHERE au.user_id = (select auth.uid())
              AND au.org_id = push_tokens.org_id
        )
    )
);

CREATE OR REPLACE FUNCTION public.mark_notification_read(p_notification_id uuid)
RETURNS void
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path TO 'pg_catalog', 'public', 'pg_temp'
AS $$
BEGIN
    IF auth.uid() IS NULL THEN
        RAISE EXCEPTION 'Authentication required' USING ERRCODE = '42501';
    END IF;
    IF NOT EXISTS (
        SELECT 1 FROM public.notifications n
        WHERE n.id = p_notification_id
          AND n.org_id = public.get_my_org_id()
          AND COALESCE(n.target_user_id, n.user_id) = auth.uid()
    ) THEN
        RAISE EXCEPTION 'Notification is not visible to current user' USING ERRCODE = '42501';
    END IF;
    INSERT INTO public.notification_reads(notification_id, user_id, read_at)
    VALUES (p_notification_id, auth.uid(), now())
    ON CONFLICT (notification_id, user_id)
    DO UPDATE SET read_at = EXCLUDED.read_at;
END;
$$;

CREATE OR REPLACE FUNCTION public.mark_all_notifications_read()
RETURNS void
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path TO 'pg_catalog', 'public', 'pg_temp'
AS $$
BEGIN
    IF auth.uid() IS NULL THEN
        RAISE EXCEPTION 'Authentication required' USING ERRCODE = '42501';
    END IF;
    INSERT INTO public.notification_reads(notification_id, user_id, read_at)
    SELECT n.id, auth.uid(), now()
    FROM public.notifications n
    WHERE n.org_id = public.get_my_org_id()
      AND COALESCE(n.target_user_id, n.user_id) = auth.uid()
    ON CONFLICT (notification_id, user_id)
    DO UPDATE SET read_at = EXCLUDED.read_at;
END;
$$;

COMMENT ON COLUMN public.notifications.is_read IS
'Legacy compatibility mirror only. Canonical per-user read state is public.notification_reads.';

CREATE OR REPLACE FUNCTION public.get_my_notifications_page_v1(
    p_limit integer DEFAULT 50,
    p_before_created_at timestamptz DEFAULT NULL,
    p_before_id uuid DEFAULT NULL
)
RETURNS TABLE(
    id uuid, org_id uuid, branch_id uuid, target_user_id uuid, audience text, type text,
    title text, body text, related_entity_id text, related_entity_type text,
    navigation_route text, is_read boolean, created_at timestamptz, created_by uuid
)
LANGUAGE sql
SECURITY DEFINER
SET search_path TO 'pg_catalog', 'public', 'pg_temp'
AS $$
    SELECT n.id, n.org_id, n.branch_id, COALESCE(n.target_user_id, n.user_id),
           n.audience, n.type, n.title, n.body, n.related_entity_id, n.related_entity_type,
           COALESCE(n.data->>'navigation_route', n.data->>'nav_route', n.data->>'route'),
           EXISTS (
               SELECT 1 FROM public.notification_reads r
               WHERE r.notification_id = n.id AND r.user_id = auth.uid()
           ),
           n.created_at, n.created_by
    FROM public.notifications n
    WHERE auth.uid() IS NOT NULL
      AND n.org_id = public.get_my_org_id()
      AND COALESCE(n.target_user_id, n.user_id) = auth.uid()
      AND (
          p_before_created_at IS NULL OR n.created_at < p_before_created_at
          OR (n.created_at = p_before_created_at AND p_before_id IS NOT NULL AND n.id < p_before_id)
      )
    ORDER BY n.created_at DESC, n.id DESC
    LIMIT GREATEST(1, LEAST(COALESCE(p_limit, 50), 100));
$$;

REVOKE ALL ON FUNCTION public.create_direct_employee_notification(uuid,text,text,text,text,text,text) FROM PUBLIC, anon, authenticated;
GRANT EXECUTE ON FUNCTION public.create_direct_employee_notification(uuid,text,text,text,text,text,text) TO authenticated, service_role;
REVOKE ALL ON FUNCTION public.create_all_employees_notification(text,text,text,text,text,text) FROM PUBLIC, anon, authenticated;
GRANT EXECUTE ON FUNCTION public.create_all_employees_notification(text,text,text,text,text,text) TO authenticated, service_role;
REVOKE ALL ON FUNCTION public.create_manager_only_notification(text,text,text,text,text,text) FROM PUBLIC, anon, authenticated;
GRANT EXECUTE ON FUNCTION public.create_manager_only_notification(text,text,text,text,text,text) TO authenticated, service_role;
REVOKE ALL ON FUNCTION public.create_system_all_employees_notification(text,text,text,text,text,text) FROM PUBLIC, anon, authenticated;
GRANT EXECUTE ON FUNCTION public.create_system_all_employees_notification(text,text,text,text,text,text) TO service_role;
REVOKE ALL ON FUNCTION public.get_my_notifications_page_v1(integer,timestamptz,uuid) FROM PUBLIC, anon, authenticated;
GRANT EXECUTE ON FUNCTION public.get_my_notifications_page_v1(integer,timestamptz,uuid) TO authenticated, service_role;
REVOKE ALL ON FUNCTION public.mark_notification_read(uuid) FROM PUBLIC, anon, authenticated;
GRANT EXECUTE ON FUNCTION public.mark_notification_read(uuid) TO authenticated, service_role;
REVOKE ALL ON FUNCTION public.mark_all_notifications_read() FROM PUBLIC, anon, authenticated;
GRANT EXECUTE ON FUNCTION public.mark_all_notifications_read() TO authenticated, service_role;
