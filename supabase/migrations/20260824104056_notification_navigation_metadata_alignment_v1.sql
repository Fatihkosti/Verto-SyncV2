CREATE OR REPLACE FUNCTION public.notify_admins_commission_needed()
RETURNS trigger
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path TO 'pg_catalog', 'public', 'pg_temp'
AS $function$
DECLARE
    v_client_name  text;
    v_client_types text;
    v_creator_role text;
    v_route text;
BEGIN
    IF NEW.category <> 'SALE' THEN RETURN NEW; END IF;

    SELECT c.name, c.client_types
      INTO v_client_name, v_client_types
      FROM public.clients c
     WHERE c.id = NEW.client_id;

    IF v_client_types IS NULL
       OR (v_client_types NOT LIKE '%MARKETER%' AND v_client_types NOT LIKE '%WORKSHOP_OWNER%') THEN
        RETURN NEW;
    END IF;

    SELECT u.role INTO v_creator_role FROM public.app_users u WHERE u.id = NEW.created_by;
    IF v_creator_role = 'admin' THEN RETURN NEW; END IF;

    v_route := 'invoice/' || NEW.id::text || '?openCommission=true';

    INSERT INTO public.notifications (
        user_id, client_id, org_id, type, title, body,
        related_entity_id, related_entity_type, data
    )
    SELECT u.id, NEW.client_id, NEW.organization_id, 'ADMIN_COMMISSION_NEEDED',
           'فاتورة بحاجة لعمولة',
           format('تم إضافة فاتورة لـ %s — اضغط لإضافة عمولة', COALESCE(v_client_name, 'عميل')),
           NEW.id::text, 'INVOICE',
           jsonb_build_object(
               'invoice_id', NEW.id,
               'client_name', COALESCE(v_client_name, ''),
               'navigation_route', v_route,
               'route', v_route
           )
    FROM public.app_users u
    WHERE u.organization_id = NEW.organization_id
      AND u.role = 'admin'
      AND u.is_active = true;

    RETURN NEW;
EXCEPTION WHEN others THEN
    RAISE WARNING 'notify_admins_commission_needed failed: %', SQLERRM;
    RETURN NEW;
END;
$function$;

UPDATE public.notifications n
SET related_entity_id = COALESCE(n.related_entity_id, n.data->>'invoice_id'),
    related_entity_type = COALESCE(n.related_entity_type, 'INVOICE'),
    data = n.data || jsonb_build_object(
        'navigation_route', 'invoice/' || (n.data->>'invoice_id') || '?openCommission=true',
        'route', 'invoice/' || (n.data->>'invoice_id') || '?openCommission=true'
    )
WHERE n.type = 'ADMIN_COMMISSION_NEEDED'
  AND COALESCE(n.data->>'invoice_id', '') <> '';

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
AS $function$
    SELECT
        n.id,
        n.org_id,
        n.branch_id,
        COALESCE(n.target_user_id, n.user_id) AS target_user_id,
        n.audience,
        n.type,
        n.title,
        n.body,
        COALESCE(
            n.related_entity_id,
            CASE
                WHEN n.type IN (
                    'ADMIN_COMMISSION_NEEDED','CREDIT_SALE_INVOICE_CREATED','PAYMENT_RECORDED',
                    'PAYMENT_DUE_REMINDER','PAYMENT_OVERDUE','PURCHASE_INVOICE_CREATED'
                ) THEN n.data->>'invoice_id'
                WHEN n.type = 'GOODS_RECEIVED' THEN n.data->>'shipment_id'
                WHEN n.type = 'AUTODRIVE_JOIN_REQUEST' THEN n.data->>'request_id'
                ELSE NULL
            END
        ) AS related_entity_id,
        COALESCE(
            n.related_entity_type,
            CASE
                WHEN n.type IN (
                    'ADMIN_COMMISSION_NEEDED','CREDIT_SALE_INVOICE_CREATED','PAYMENT_RECORDED',
                    'PAYMENT_DUE_REMINDER','PAYMENT_OVERDUE','PURCHASE_INVOICE_CREATED'
                ) AND COALESCE(n.data->>'invoice_id', '') <> '' THEN 'INVOICE'
                WHEN n.type = 'GOODS_RECEIVED' AND COALESCE(n.data->>'shipment_id', '') <> '' THEN 'SHIPMENT'
                WHEN n.type = 'AUTODRIVE_JOIN_REQUEST' AND COALESCE(n.data->>'request_id', '') <> '' THEN 'AUTODRIVE_JOIN_REQUEST'
                ELSE NULL
            END
        ) AS related_entity_type,
        COALESCE(
            n.data->>'navigation_route', n.data->>'nav_route', n.data->>'route',
            CASE
                WHEN n.type = 'ADMIN_COMMISSION_NEEDED' AND COALESCE(n.data->>'invoice_id', '') <> ''
                    THEN 'invoice/' || (n.data->>'invoice_id') || '?openCommission=true'
                WHEN n.type IN (
                    'CREDIT_SALE_INVOICE_CREATED','PAYMENT_RECORDED','PAYMENT_DUE_REMINDER',
                    'PAYMENT_OVERDUE','PURCHASE_INVOICE_CREATED'
                ) AND COALESCE(n.data->>'invoice_id', '') <> ''
                    THEN 'invoice/' || (n.data->>'invoice_id') || '?openCommission=false'
                WHEN n.type = 'LOW_STOCK' THEN 'inventory'
                WHEN n.type = 'GOODS_RECEIVED' AND COALESCE(n.data->>'shipment_id', '') <> ''
                    THEN 'logistics_v2_detail/' || (n.data->>'shipment_id')
                WHEN n.type = 'WITHDRAWAL_REQUESTED' THEN 'commission_management'
                WHEN n.type IN ('MARKETER_REGISTERED', 'AUTODRIVE_JOIN_REQUEST') THEN 'management/benzine'
                WHEN n.type = 'NEW_CHAT_MESSAGE' THEN 'messages'
                ELSE NULL
            END
        ) AS navigation_route,
        EXISTS (
            SELECT 1 FROM public.notification_reads r
            WHERE r.notification_id = n.id AND r.user_id = auth.uid()
        ) AS is_read,
        n.created_at,
        n.created_by
    FROM public.notifications n
    WHERE auth.uid() IS NOT NULL
      AND n.org_id = public.get_my_org_id()
      AND COALESCE(n.target_user_id, n.user_id) = auth.uid()
      AND (
          p_before_created_at IS NULL
          OR n.created_at < p_before_created_at
          OR (n.created_at = p_before_created_at AND p_before_id IS NOT NULL AND n.id < p_before_id)
      )
    ORDER BY n.created_at DESC, n.id DESC
    LIMIT GREATEST(1, LEAST(COALESCE(p_limit, 50), 100));
$function$;
