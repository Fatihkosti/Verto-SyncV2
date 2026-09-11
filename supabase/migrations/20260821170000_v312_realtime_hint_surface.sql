-- Verto v312: minimal, optional, tenant/visibility-safe Realtime hint surface.
-- Realtime is acceleration only; verto_sync_change_log + pull cursor remain correctness authority.

CREATE TABLE IF NOT EXISTS public.verto_sync_realtime_hints (
    revision bigint PRIMARY KEY,
    organization_id uuid NOT NULL,
    aggregate_type text NOT NULL,
    aggregate_id text NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT verto_sync_realtime_hint_revision_positive CHECK (revision > 0)
);

CREATE INDEX IF NOT EXISTS verto_sync_realtime_hints_org_revision_idx
    ON public.verto_sync_realtime_hints (organization_id, revision);

ALTER TABLE public.verto_sync_realtime_hints ENABLE ROW LEVEL SECURITY;

CREATE OR REPLACE FUNCTION public.verto_can_read_sync_realtime_hint(
    p_revision bigint,
    p_organization_id uuid
) RETURNS boolean
LANGUAGE sql
STABLE
SECURITY DEFINER
SET search_path TO 'public'
AS $$
    SELECT EXISTS (
        SELECT 1
          FROM public.verto_sync_change_log cl
          JOIN public.app_users au
            ON au.id = auth.uid()
           AND au.organization_id = cl.organization_id
           AND COALESCE(au.is_active, true)
         WHERE cl.revision = p_revision
           AND cl.organization_id = p_organization_id
           AND (cl.visibility_principal_id IS NULL OR cl.visibility_principal_id = auth.uid())
           AND (cl.required_permission IS NULL OR public.has_perm(cl.required_permission))
    );
$$;

REVOKE ALL ON FUNCTION public.verto_can_read_sync_realtime_hint(bigint, uuid)
    FROM PUBLIC, anon;
GRANT EXECUTE ON FUNCTION public.verto_can_read_sync_realtime_hint(bigint, uuid)
    TO authenticated;

DROP POLICY IF EXISTS verto_sync_realtime_hints_select ON public.verto_sync_realtime_hints;
CREATE POLICY verto_sync_realtime_hints_select
    ON public.verto_sync_realtime_hints
    FOR SELECT
    TO authenticated
    USING (public.verto_can_read_sync_realtime_hint(revision, organization_id));

REVOKE ALL ON TABLE public.verto_sync_realtime_hints FROM PUBLIC, anon, authenticated;
GRANT SELECT (revision, organization_id, aggregate_type, aggregate_id)
    ON public.verto_sync_realtime_hints TO authenticated;

CREATE OR REPLACE FUNCTION public.verto_publish_sync_realtime_hint()
RETURNS trigger
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path TO 'public'
AS $$
BEGIN
    INSERT INTO public.verto_sync_realtime_hints (
        revision, organization_id, aggregate_type, aggregate_id
    ) VALUES (
        NEW.revision, NEW.organization_id, NEW.aggregate_type, NEW.aggregate_id
    )
    ON CONFLICT (revision) DO NOTHING;
    RETURN NEW;
END;
$$;

REVOKE ALL ON FUNCTION public.verto_publish_sync_realtime_hint()
    FROM PUBLIC, anon, authenticated;

DROP TRIGGER IF EXISTS verto_sync_change_realtime_hint_after_insert
    ON public.verto_sync_change_log;
CREATE TRIGGER verto_sync_change_realtime_hint_after_insert
AFTER INSERT ON public.verto_sync_change_log
FOR EACH ROW
EXECUTE FUNCTION public.verto_publish_sync_realtime_hint();

-- Explicit allowlist only. Never create/reset a publication and never use a wildcard publication.
DO $$
BEGIN
    IF EXISTS (
        SELECT 1 FROM pg_publication WHERE pubname = 'supabase_realtime'
    ) AND NOT EXISTS (
        SELECT 1
          FROM pg_publication_tables
         WHERE pubname = 'supabase_realtime'
           AND schemaname = 'public'
           AND tablename = 'verto_sync_realtime_hints'
    ) THEN
        ALTER PUBLICATION supabase_realtime
            ADD TABLE public.verto_sync_realtime_hints;
    END IF;
END
$$;

COMMENT ON TABLE public.verto_sync_realtime_hints IS
    'v312 minimal optional Realtime acceleration hints. No business payload; revision is advisory to clients and never a cursor assignment.';
