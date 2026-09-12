CREATE TABLE public.verto_b07_final_concurrency_harness (
  test_key text PRIMARY KEY,
  user_id uuid NOT NULL,
  organization_id uuid NOT NULL,
  wire_json text NOT NULL,
  wire_sha256 text NOT NULL,
  created_at timestamptz NOT NULL DEFAULT now()
);

ALTER TABLE public.verto_b07_final_concurrency_harness ENABLE ROW LEVEL SECURITY;
REVOKE ALL ON public.verto_b07_final_concurrency_harness FROM PUBLIC, anon, authenticated;

CREATE FUNCTION public.verto_b07_final_concurrency_invoke(p_key text)
RETURNS jsonb
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path TO 'public'
AS $function$
DECLARE
  v_row public.verto_b07_final_concurrency_harness%ROWTYPE;
BEGIN
  SELECT * INTO v_row
  FROM public.verto_b07_final_concurrency_harness
  WHERE test_key = p_key;

  IF NOT FOUND OR v_row.created_at < now() - interval '15 minutes' THEN
    RAISE EXCEPTION 'TEST_KEY_INVALID' USING ERRCODE = '22023';
  END IF;

  PERFORM set_config('request.jwt.claim.sub', v_row.user_id::text, true);
  RETURN public.verto_apply_sync_batch_v2(v_row.wire_json, v_row.wire_sha256);
END
$function$;

REVOKE ALL ON FUNCTION public.verto_b07_final_concurrency_invoke(text)
FROM PUBLIC, anon, authenticated;
