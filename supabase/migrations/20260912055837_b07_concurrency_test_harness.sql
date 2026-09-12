CREATE TABLE IF NOT EXISTS public.verto_b07_test_harness (
  test_key text PRIMARY KEY,
  user_id uuid NOT NULL,
  organization_id uuid NOT NULL,
  wire_json text NOT NULL,
  wire_sha256 text NOT NULL,
  request_ids bigint[] NOT NULL DEFAULT ARRAY[]::bigint[],
  created_at timestamptz NOT NULL DEFAULT now()
);
ALTER TABLE public.verto_b07_test_harness ENABLE ROW LEVEL SECURITY;
REVOKE ALL ON public.verto_b07_test_harness FROM PUBLIC, anon, authenticated;

CREATE OR REPLACE FUNCTION public.verto_b07_test_invoke(p_key text)
RETURNS jsonb
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path TO 'public'
AS $fn$
DECLARE r public.verto_b07_test_harness%ROWTYPE;
BEGIN
  SELECT * INTO r FROM public.verto_b07_test_harness h WHERE h.test_key=p_key;
  IF NOT FOUND OR r.created_at < now() - interval '10 minutes' THEN
    RAISE EXCEPTION 'TEST_KEY_INVALID' USING ERRCODE='22023';
  END IF;
  PERFORM set_config('request.jwt.claim.sub', r.user_id::text, true);
  RETURN public.verto_apply_sync_batch_v2(r.wire_json,r.wire_sha256);
END $fn$;
REVOKE ALL ON FUNCTION public.verto_b07_test_invoke(text) FROM PUBLIC, authenticated;
GRANT EXECUTE ON FUNCTION public.verto_b07_test_invoke(text) TO anon;