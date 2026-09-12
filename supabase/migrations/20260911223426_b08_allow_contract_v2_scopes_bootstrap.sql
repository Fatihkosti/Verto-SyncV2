ALTER TABLE public.verto_sync_scopes DROP CONSTRAINT IF EXISTS verto_sync_scope_contract;
ALTER TABLE public.verto_sync_scopes ADD CONSTRAINT verto_sync_scope_contract CHECK (contract_family='verto-unified-sync' AND contract_version IN (1,2) AND scope_definition_version=1);
ALTER TABLE public.verto_sync_bootstrap_sessions DROP CONSTRAINT IF EXISTS verto_sync_bootstrap_contract;
ALTER TABLE public.verto_sync_bootstrap_sessions ADD CONSTRAINT verto_sync_bootstrap_contract CHECK (contract_family='verto-unified-sync' AND contract_version IN (1,2) AND scope_definition_version=1);