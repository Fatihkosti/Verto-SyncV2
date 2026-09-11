-- Move internal-message push delivery to the same durable notification outbox.

CREATE OR REPLACE FUNCTION notification_delivery.notify_internal_message_v1()
RETURNS trigger
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path TO 'pg_catalog', 'public', 'notification_delivery'
AS $$
BEGIN
    IF NEW.sender_type = 'MARKETER' THEN
        INSERT INTO public.notifications(
            user_id, target_user_id, org_id, client_id, audience, type,
            title, body, related_entity_id, related_entity_type, data, created_by, event_key
        )
        SELECT u.id, u.id, NEW.org_id, NEW.client_id, 'ALL_EMPLOYEES', 'NEW_CHAT_MESSAGE',
               'رسالة من المسوّق', left(NEW.body, 200), NEW.id::text, 'INTERNAL_MESSAGE',
               jsonb_build_object(
                   'transport_type', 'chat',
                   'message_id', NEW.id::text,
                   'client_id', NEW.client_id::text,
                   'sender_type', NEW.sender_type
               ),
               NEW.sender_id,
               format('internal-message:%s', NEW.id)
        FROM public.app_users u
        WHERE u.organization_id = NEW.org_id
          AND COALESCE(u.is_active, true) = true
          AND u.id <> NEW.sender_id
        ON CONFLICT (org_id, target_user_id, event_key) WHERE event_key IS NOT NULL DO NOTHING;
    ELSE
        INSERT INTO public.notifications(
            user_id, target_user_id, org_id, client_id, audience, type,
            title, body, related_entity_id, related_entity_type, data, created_by, event_key
        )
        SELECT au.user_id, au.user_id, NEW.org_id, NEW.client_id, 'DIRECT_EMPLOYEE', 'NEW_CHAT_MESSAGE',
               'رسالة من الإدارة', left(NEW.body, 200), NEW.id::text, 'INTERNAL_MESSAGE',
               jsonb_build_object(
                   'transport_type', 'chat',
                   'message_id', NEW.id::text,
                   'client_id', NEW.client_id::text,
                   'sender_type', NEW.sender_type
               ),
               NEW.sender_id,
               format('internal-message:%s', NEW.id)
        FROM public.autodrive_users au
        WHERE au.org_id = NEW.org_id
          AND au.client_id = NEW.client_id
          AND au.user_id <> NEW.sender_id
        ON CONFLICT (org_id, target_user_id, event_key) WHERE event_key IS NOT NULL DO NOTHING;
    END IF;

    RETURN NEW;
END;
$$;

DROP TRIGGER IF EXISTS trg_fcm_internal_messages ON public.internal_messages;
DROP TRIGGER IF EXISTS trg_durable_internal_message_notification ON public.internal_messages;
CREATE TRIGGER trg_durable_internal_message_notification
AFTER INSERT ON public.internal_messages
FOR EACH ROW
EXECUTE FUNCTION notification_delivery.notify_internal_message_v1();

REVOKE ALL ON FUNCTION notification_delivery.notify_internal_message_v1() FROM PUBLIC, anon, authenticated;
