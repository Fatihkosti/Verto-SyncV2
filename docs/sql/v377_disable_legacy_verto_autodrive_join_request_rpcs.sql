-- V377: Verto no longer manages AutoDrive join requests.
-- AutoDrive v75 source-of-truth stays phone -> OTP -> invite code for new users.
revoke execute on function public.verto_autodrive_join_requests_v1(text, integer, timestamptz)
    from public, anon, authenticated;
revoke execute on function public.verto_approve_autodrive_join_request_v1(uuid, uuid, boolean)
    from public, anon, authenticated;
revoke execute on function public.verto_reject_autodrive_join_request_v1(uuid, text)
    from public, anon, authenticated;
