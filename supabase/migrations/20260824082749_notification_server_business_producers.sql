-- Server-owned notification producers for authoritative business events and due-payment reminders.

CREATE TABLE IF NOT EXISTS notification_delivery.org_preferences (
    org_id uuid PRIMARY KEY REFERENCES public.organizations(id) ON DELETE CASCADE,
    timezone_id text NOT NULL DEFAULT 'Africa/Khartoum',
    currency_code text NULL,
    updated_at timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT notification_org_preferences_timezone_nonblank CHECK (btrim(timezone_id) <> '')
);

INSERT INTO notification_delivery.org_preferences(org_id, timezone_id, currency_code)
SELECT o.id, 'Africa/Khartoum', NULLIF(btrim(os.currency), '')
FROM public.organizations o
LEFT JOIN public.organization_settings os ON os.organization_id = o.id
ON CONFLICT (org_id) DO UPDATE
SET currency_code = COALESCE(notification_delivery.org_preferences.currency_code, EXCLUDED.currency_code);

REVOKE ALL ON notification_delivery.org_preferences FROM PUBLIC, anon, authenticated;

CREATE OR REPLACE FUNCTION notification_delivery.notify_invoice_business_event_v1()
RETURNS trigger
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path TO 'pg_catalog', 'public', 'notification_delivery'
AS $$
DECLARE
    v_type text;
    v_title text;
    v_body text;
    v_event_key text;
BEGIN
    IF COALESCE(NEW.voided, false) OR COALESCE(NEW.lifecycle_status, '') <> 'POSTED' THEN
        RETURN NEW;
    END IF;

    IF NEW.category = 'SALE' AND NEW.status = 'CLOSED_CREDIT' THEN
        v_type := 'CREDIT_SALE_INVOICE_CREATED';
        v_title := 'فاتورة بيع آجلة جديدة';
        v_body := format('تم إنشاء فاتورة بيع آجلة #%s بقيمة %s.', NEW.invoice_number, NEW.total_amount);
        v_event_key := format('invoice:%s:credit-sale-created', NEW.id);
    ELSIF NEW.category = 'PURCHASE' THEN
        v_type := 'PURCHASE_INVOICE_CREATED';
        v_title := 'فاتورة مشتريات جديدة';
        v_body := format('تم إنشاء فاتورة مشتريات #%s بقيمة %s.', NEW.invoice_number, NEW.total_amount);
        v_event_key := format('invoice:%s:purchase-created', NEW.id);
    ELSE
        RETURN NEW;
    END IF;

    INSERT INTO public.notifications(
        user_id, target_user_id, org_id, audience, type, title, body,
        related_entity_id, related_entity_type, data, created_by, event_key
    )
    SELECT u.id, u.id, NEW.organization_id, 'ALL_EMPLOYEES', v_type, v_title, v_body,
           NEW.id::text, 'INVOICE',
           jsonb_build_object(
               'navigation_route', format('invoice/%s?openCommission=false', NEW.id),
               'route', format('invoice/%s?openCommission=false', NEW.id),
               'invoice_id', NEW.id::text
           ),
           NEW.created_by, v_event_key
    FROM public.app_users u
    WHERE u.organization_id = NEW.organization_id
      AND COALESCE(u.is_active, true) = true
    ON CONFLICT (org_id, target_user_id, event_key) WHERE event_key IS NOT NULL DO NOTHING;

    RETURN NEW;
END;
$$;

DROP TRIGGER IF EXISTS trg_notify_invoice_business_event_v1 ON public.invoices;
CREATE TRIGGER trg_notify_invoice_business_event_v1
AFTER INSERT OR UPDATE OF lifecycle_status, status, category, voided
ON public.invoices
FOR EACH ROW EXECUTE FUNCTION notification_delivery.notify_invoice_business_event_v1();

CREATE OR REPLACE FUNCTION notification_delivery.notify_payment_recorded_v1()
RETURNS trigger
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path TO 'pg_catalog', 'public', 'notification_delivery'
AS $$
DECLARE
    v_invoice_number integer;
    v_invoice_org uuid;
BEGIN
    IF NEW.invoice_id IS NULL OR COALESCE(NEW.amount, 0) <= 0 THEN
        RETURN NEW;
    END IF;

    SELECT i.invoice_number, i.organization_id
      INTO v_invoice_number, v_invoice_org
      FROM public.invoices i
     WHERE i.id = NEW.invoice_id
       AND COALESCE(i.voided, false) = false
     LIMIT 1;

    IF v_invoice_org IS NULL OR v_invoice_org <> NEW.organization_id THEN
        RETURN NEW;
    END IF;

    INSERT INTO public.notifications(
        user_id, target_user_id, org_id, audience, type, title, body,
        related_entity_id, related_entity_type, data, created_by, event_key
    )
    SELECT u.id, u.id, NEW.organization_id, 'ALL_EMPLOYEES', 'PAYMENT_RECORDED',
           'تم تسجيل سداد',
           format('تم تسجيل سداد بقيمة %s على فاتورة #%s.', NEW.amount, COALESCE(v_invoice_number, 0)),
           NEW.id::text, 'PAYMENT',
           jsonb_build_object(
               'navigation_route', format('invoice/%s?openCommission=false', NEW.invoice_id),
               'route', format('invoice/%s?openCommission=false', NEW.invoice_id),
               'invoice_id', NEW.invoice_id::text,
               'payment_id', NEW.id::text
           ),
           NEW.created_by,
           format('payment:%s:recorded', NEW.id)
    FROM public.app_users u
    WHERE u.organization_id = NEW.organization_id
      AND COALESCE(u.is_active, true) = true
    ON CONFLICT (org_id, target_user_id, event_key) WHERE event_key IS NOT NULL DO NOTHING;

    RETURN NEW;
END;
$$;

DROP TRIGGER IF EXISTS trg_notify_payment_recorded_v1 ON public.payments;
CREATE TRIGGER trg_notify_payment_recorded_v1
AFTER INSERT ON public.payments
FOR EACH ROW EXECUTE FUNCTION notification_delivery.notify_payment_recorded_v1();

CREATE OR REPLACE FUNCTION notification_delivery.notify_low_stock_v1()
RETURNS trigger
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path TO 'pg_catalog', 'public', 'notification_delivery'
AS $$
DECLARE
    v_now_low boolean;
    v_was_low boolean := false;
    v_event_key text;
BEGIN
    IF COALESCE(NEW.is_service, false)
       OR COALESCE(NEW.is_archived, false)
       OR COALESCE(NEW.min_quantity, 0) <= 0 THEN
        RETURN NEW;
    END IF;

    v_now_low := COALESCE(NEW.quantity, 0) <= COALESCE(NEW.min_quantity, 0);
    IF NOT v_now_low THEN RETURN NEW; END IF;

    IF TG_OP = 'UPDATE' THEN
        v_was_low := NOT COALESCE(OLD.is_service, false)
                     AND NOT COALESCE(OLD.is_archived, false)
                     AND COALESCE(OLD.min_quantity, 0) > 0
                     AND COALESCE(OLD.quantity, 0) <= COALESCE(OLD.min_quantity, 0);
        IF v_was_low THEN RETURN NEW; END IF;
    END IF;

    v_event_key := format(
        'inventory:%s:low:%s',
        NEW.id,
        to_char(COALESCE(NEW.updated_at, NEW.created_at, clock_timestamp()), 'YYYYMMDDHH24MISS.US')
    );

    INSERT INTO public.notifications(
        user_id, target_user_id, org_id, audience, type, title, body,
        related_entity_id, related_entity_type, data, created_by, event_key
    )
    SELECT u.id, u.id, NEW.organization_id, 'ALL_EMPLOYEES', 'LOW_STOCK',
           'نقص صنف',
           format('الصنف %s وصل إلى %s من الحد الأدنى %s.', NEW.name, NEW.quantity, NEW.min_quantity),
           NEW.id::text, 'INVENTORY_ITEM',
           jsonb_build_object('navigation_route', 'inventory', 'route', 'inventory', 'item_id', NEW.id::text),
           NEW.created_by, v_event_key
    FROM public.app_users u
    WHERE u.organization_id = NEW.organization_id
      AND COALESCE(u.is_active, true) = true
    ON CONFLICT (org_id, target_user_id, event_key) WHERE event_key IS NOT NULL DO NOTHING;

    RETURN NEW;
END;
$$;

DROP TRIGGER IF EXISTS trg_notify_low_stock_v1 ON public.inventory_items;
CREATE TRIGGER trg_notify_low_stock_v1
AFTER INSERT OR UPDATE OF quantity, min_quantity, is_archived, is_service
ON public.inventory_items
FOR EACH ROW EXECUTE FUNCTION notification_delivery.notify_low_stock_v1();

CREATE OR REPLACE FUNCTION notification_delivery.notify_goods_received_v1()
RETURNS trigger
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path TO 'pg_catalog', 'public', 'notification_delivery'
AS $$
DECLARE
    v_title text;
    v_shipment_title text;
    v_body text;
    v_state text;
BEGIN
    IF TG_OP = 'UPDATE' AND OLD.is_complete IS NOT DISTINCT FROM NEW.is_complete THEN RETURN NEW; END IF;

    SELECT COALESCE(NULLIF(btrim(s.title), ''), NULLIF(btrim(s.shipment_number), ''), s.id::text)
      INTO v_shipment_title
      FROM public.shipments s
     WHERE s.id = NEW.shipment_id
       AND s.organization_id = NEW.organization_id
     LIMIT 1;
    IF v_shipment_title IS NULL THEN RETURN NEW; END IF;

    v_title := 'وصول بضاعة جديدة';
    v_state := CASE WHEN COALESCE(NEW.is_complete, false) THEN 'complete' ELSE 'partial' END;
    v_body := CASE WHEN COALESCE(NEW.is_complete, false)
        THEN format('تم تأكيد وصول بضاعة شحنة %s بالكامل.', v_shipment_title)
        ELSE format('تم تأكيد وصول بضاعة شحنة %s جزئيًا.', v_shipment_title)
    END;

    INSERT INTO public.notifications(
        user_id, target_user_id, org_id, audience, type, title, body,
        related_entity_id, related_entity_type, data, created_by, event_key
    )
    SELECT u.id, u.id, NEW.organization_id, 'ALL_EMPLOYEES', 'GOODS_RECEIVED',
           v_title, v_body, NEW.shipment_id::text, 'SHIPMENT',
           jsonb_build_object(
               'navigation_route', format('logistics_v2_detail/%s', NEW.shipment_id),
               'route', format('logistics_v2_detail/%s', NEW.shipment_id),
               'shipment_id', NEW.shipment_id::text,
               'receipt_id', NEW.id::text,
               'is_complete', COALESCE(NEW.is_complete, false)
           ),
           NULL,
           format('shipment-receipt:%s:%s', NEW.id, v_state)
    FROM public.app_users u
    WHERE u.organization_id = NEW.organization_id
      AND COALESCE(u.is_active, true) = true
    ON CONFLICT (org_id, target_user_id, event_key) WHERE event_key IS NOT NULL DO NOTHING;
    RETURN NEW;
END;
$$;

DROP TRIGGER IF EXISTS trg_notify_goods_received_v1 ON public.shipment_receipts;
CREATE TRIGGER trg_notify_goods_received_v1
AFTER INSERT OR UPDATE OF is_complete
ON public.shipment_receipts
FOR EACH ROW EXECUTE FUNCTION notification_delivery.notify_goods_received_v1();

CREATE OR REPLACE FUNCTION notification_delivery.generate_payment_due_notifications_v1(p_now timestamptz DEFAULT now())
RETURNS jsonb
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path TO 'pg_catalog', 'public', 'notification_delivery'
AS $$
DECLARE
    r record;
    v_inserted integer;
    v_employee_count integer := 0;
    v_manager_count integer := 0;
    v_days_csv text;
    v_overdue_days integer;
    v_repeat integer;
    v_currency_suffix text;
BEGIN
    FOR r IN
        SELECT i.id, i.organization_id, i.created_by, i.invoice_number, i.due_date,
               i.notify_days_before, i.notify_repeat_days, i.total_amount,
               i.transaction_currency_code, c.name AS client_name,
               COALESCE(pay.paid_amount, 0) AS paid_amount,
               i.total_amount - COALESCE(pay.paid_amount, 0) AS outstanding_amount,
               COALESCE(pref.timezone_id, 'Africa/Khartoum') AS timezone_id,
               COALESCE(NULLIF(btrim(i.transaction_currency_code), ''), NULLIF(btrim(pref.currency_code), '')) AS currency_code,
               ((i.due_date AT TIME ZONE COALESCE(pref.timezone_id, 'Africa/Khartoum'))::date
                - (p_now AT TIME ZONE COALESCE(pref.timezone_id, 'Africa/Khartoum'))::date) AS days_until,
               (i.due_date AT TIME ZONE COALESCE(pref.timezone_id, 'Africa/Khartoum'))::date AS due_local_date
        FROM public.invoices i
        LEFT JOIN public.clients c ON c.id = i.client_id AND c.organization_id = i.organization_id
        LEFT JOIN LATERAL (
            SELECT COALESCE(SUM(p.amount), 0) AS paid_amount
            FROM public.payments p
            WHERE p.invoice_id = i.id AND p.organization_id = i.organization_id
        ) pay ON true
        LEFT JOIN notification_delivery.org_preferences pref ON pref.org_id = i.organization_id
        WHERE i.category = 'SALE'
          AND i.status = 'CLOSED_CREDIT'
          AND i.lifecycle_status = 'POSTED'
          AND COALESCE(i.voided, false) = false
          AND i.due_date IS NOT NULL
          AND COALESCE(i.notifications_enabled, true) = true
          AND i.total_amount - COALESCE(pay.paid_amount, 0) > 0
    LOOP
        v_days_csv := regexp_replace(COALESCE(r.notify_days_before, ''), '\s+', '', 'g');
        v_repeat := GREATEST(COALESCE(r.notify_repeat_days, 3), 1);
        v_currency_suffix := CASE WHEN NULLIF(btrim(COALESCE(r.currency_code, '')), '') IS NULL
                                  THEN '' ELSE ' ' || btrim(r.currency_code) END;

        IF r.days_until >= 0
           AND (r.days_until = 0 OR r.days_until::text = ANY(string_to_array(v_days_csv, ','))) THEN
            INSERT INTO public.notifications(
                user_id, target_user_id, org_id, audience, type, title, body,
                related_entity_id, related_entity_type, data, created_by, event_key
            )
            SELECT u.id, u.id, r.organization_id, 'DIRECT_EMPLOYEE', 'PAYMENT_DUE_REMINDER',
                   CASE WHEN r.days_until = 0 THEN 'دفعة مستحقة اليوم' ELSE 'تذكير بدفعة قادمة' END,
                   CASE WHEN r.days_until = 0
                       THEN format('فاتورة #%s للعميل %s مستحقة اليوم. المتبقي %s%s.', r.invoice_number, COALESCE(r.client_name, 'عميل'), r.outstanding_amount, v_currency_suffix)
                       ELSE format('فاتورة #%s للعميل %s تستحق بعد %s يوم. المتبقي %s%s.', r.invoice_number, COALESCE(r.client_name, 'عميل'), r.days_until, r.outstanding_amount, v_currency_suffix)
                   END,
                   r.id::text, 'INVOICE',
                   jsonb_build_object(
                       'navigation_route', format('invoice/%s?openCommission=false', r.id),
                       'route', format('invoice/%s?openCommission=false', r.id),
                       'invoice_id', r.id::text,
                       'due_date', r.due_local_date::text,
                       'days_until_due', r.days_until::text
                   ),
                   NULL,
                   format('invoice:%s:due:%s:d%s', r.id, r.due_local_date, r.days_until)
            FROM public.app_users u
            WHERE u.id = r.created_by
              AND u.organization_id = r.organization_id
              AND COALESCE(u.is_active, true) = true
            ON CONFLICT (org_id, target_user_id, event_key) WHERE event_key IS NOT NULL DO NOTHING;

            GET DIAGNOSTICS v_inserted = ROW_COUNT;
            v_employee_count := v_employee_count + v_inserted;

            IF v_inserted = 0 THEN
                INSERT INTO public.notifications(
                    user_id, target_user_id, org_id, audience, type, title, body,
                    related_entity_id, related_entity_type, data, created_by, event_key
                )
                SELECT u.id, u.id, r.organization_id, 'MANAGER_ONLY', 'PAYMENT_DUE_REMINDER',
                       CASE WHEN r.days_until = 0 THEN 'دفعة مستحقة اليوم بلا مسؤول نشط' ELSE 'دفعة قادمة بلا مسؤول نشط' END,
                       format('فاتورة #%s للعميل %s. المتبقي %s%s.', r.invoice_number, COALESCE(r.client_name, 'عميل'), r.outstanding_amount, v_currency_suffix),
                       r.id::text, 'INVOICE',
                       jsonb_build_object(
                           'navigation_route', format('invoice/%s?openCommission=false', r.id),
                           'route', format('invoice/%s?openCommission=false', r.id),
                           'invoice_id', r.id::text,
                           'due_date', r.due_local_date::text,
                           'days_until_due', r.days_until::text
                       ),
                       NULL,
                       format('invoice:%s:due-manager-fallback:%s:d%s', r.id, r.due_local_date, r.days_until)
                FROM public.app_users u
                WHERE u.organization_id = r.organization_id
                  AND u.role = 'admin'
                  AND COALESCE(u.is_active, true) = true
                ON CONFLICT (org_id, target_user_id, event_key) WHERE event_key IS NOT NULL DO NOTHING;
                GET DIAGNOSTICS v_inserted = ROW_COUNT;
                v_manager_count := v_manager_count + v_inserted;
            END IF;
        END IF;

        IF r.days_until < 0 THEN
            v_overdue_days := -r.days_until;
            IF v_overdue_days = 1 OR mod(v_overdue_days - 1, v_repeat) = 0 THEN
                INSERT INTO public.notifications(
                    user_id, target_user_id, org_id, audience, type, title, body,
                    related_entity_id, related_entity_type, data, created_by, event_key
                )
                SELECT u.id, u.id, r.organization_id, 'MANAGER_ONLY', 'PAYMENT_OVERDUE',
                       'دفعة متأخرة',
                       format('فاتورة #%s للعميل %s متأخرة %s يوم. المتبقي %s%s.', r.invoice_number, COALESCE(r.client_name, 'عميل'), v_overdue_days, r.outstanding_amount, v_currency_suffix),
                       r.id::text, 'INVOICE',
                       jsonb_build_object(
                           'navigation_route', format('invoice/%s?openCommission=false', r.id),
                           'route', format('invoice/%s?openCommission=false', r.id),
                           'invoice_id', r.id::text,
                           'due_date', r.due_local_date::text,
                           'days_overdue', v_overdue_days::text
                       ),
                       NULL,
                       format('invoice:%s:overdue:%s:d%s', r.id, r.due_local_date, v_overdue_days)
                FROM public.app_users u
                WHERE u.organization_id = r.organization_id
                  AND u.role = 'admin'
                  AND COALESCE(u.is_active, true) = true
                ON CONFLICT (org_id, target_user_id, event_key) WHERE event_key IS NOT NULL DO NOTHING;
                GET DIAGNOSTICS v_inserted = ROW_COUNT;
                v_manager_count := v_manager_count + v_inserted;
            END IF;
        END IF;
    END LOOP;

    RETURN jsonb_build_object(
        'employee_due_notifications_created', v_employee_count,
        'manager_due_notifications_created', v_manager_count,
        'generated_at', p_now
    );
END;
$$;

CREATE OR REPLACE FUNCTION public.run_notification_scheduled_jobs_v1(p_now timestamptz DEFAULT now())
RETURNS jsonb
LANGUAGE sql
SECURITY DEFINER
SET search_path TO 'pg_catalog', 'public', 'notification_delivery'
AS $$
    SELECT notification_delivery.generate_payment_due_notifications_v1(p_now);
$$;

REVOKE ALL ON FUNCTION public.run_notification_scheduled_jobs_v1(timestamptz) FROM PUBLIC, anon, authenticated;
GRANT EXECUTE ON FUNCTION public.run_notification_scheduled_jobs_v1(timestamptz) TO service_role;
REVOKE ALL ON FUNCTION notification_delivery.generate_payment_due_notifications_v1(timestamptz) FROM PUBLIC, anon, authenticated;
REVOKE ALL ON FUNCTION notification_delivery.notify_invoice_business_event_v1() FROM PUBLIC, anon, authenticated;
REVOKE ALL ON FUNCTION notification_delivery.notify_payment_recorded_v1() FROM PUBLIC, anon, authenticated;
REVOKE ALL ON FUNCTION notification_delivery.notify_low_stock_v1() FROM PUBLIC, anon, authenticated;
REVOKE ALL ON FUNCTION notification_delivery.notify_goods_received_v1() FROM PUBLIC, anon, authenticated;
