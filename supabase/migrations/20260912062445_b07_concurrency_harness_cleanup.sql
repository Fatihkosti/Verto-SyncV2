REVOKE ALL ON FUNCTION public.verto_b07_test_invoke(text) FROM PUBLIC,anon,authenticated;
DROP FUNCTION IF EXISTS public.verto_b07_test_invoke(text);
DROP TABLE IF EXISTS public.verto_b07_test_harness;