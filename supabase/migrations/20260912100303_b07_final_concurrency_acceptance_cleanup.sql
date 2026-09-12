REVOKE ALL ON FUNCTION public.verto_b07_final_concurrency_invoke(text)
FROM PUBLIC, anon, authenticated;
DROP FUNCTION public.verto_b07_final_concurrency_invoke(text);
DROP TABLE public.verto_b07_final_concurrency_harness;
