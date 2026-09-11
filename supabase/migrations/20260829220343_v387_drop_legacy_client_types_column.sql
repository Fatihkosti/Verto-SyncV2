-- v387: Party V2 is now the sole server-side classification authority.
alter table public.clients drop column if exists client_types;
