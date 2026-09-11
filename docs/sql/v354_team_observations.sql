-- Verto v354: free-form team observations from Home, reviewed manually by managers.
-- No AI classification. Employees submit raw text; admins set status/importance.

begin;

create table if not exists public.team_observations (
    organization_id uuid not null,
    observation_id uuid not null default gen_random_uuid(),
    text text not null check (length(btrim(text)) > 0),
    author_user_id uuid not null,
    author_name text not null check (length(btrim(author_name)) > 0),
    status text not null default 'NEW' check (status in ('NEW','REVIEWED','CLOSED')),
    is_important boolean not null default false,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    updated_by_user_id uuid not null,
    primary key (organization_id, observation_id)
);

create index if not exists team_observations_org_created_idx
    on public.team_observations (organization_id, created_at desc);
create index if not exists team_observations_org_status_important_idx
    on public.team_observations (organization_id, status, is_important desc, created_at desc);
create index if not exists team_observations_author_idx
    on public.team_observations (organization_id, author_user_id, created_at desc);

-- Reuses tenant/role helpers established by v121 educational-content security.

alter table public.team_observations enable row level security;
revoke all on public.team_observations from anon;
grant select, insert, update on public.team_observations to authenticated;

drop policy if exists team_observations_select on public.team_observations;
drop policy if exists team_observations_insert on public.team_observations;
drop policy if exists team_observations_update on public.team_observations;

create policy team_observations_select
on public.team_observations
for select to authenticated
using (
    organization_id = public.verto_current_organization_id()
    and (
        author_user_id = auth.uid()
        or public.verto_current_role() = 'admin'
    )
);

create policy team_observations_insert
on public.team_observations
for insert to authenticated
with check (
    organization_id = public.verto_current_organization_id()
    and author_user_id = auth.uid()
    and updated_by_user_id = auth.uid()
    and status = 'NEW'
    and is_important = false
);

-- Authors may safely retry their own upsert. The app exposes review/importance controls only to admins.
create policy team_observations_update
on public.team_observations
for update to authenticated
using (
    organization_id = public.verto_current_organization_id()
    and (
        public.verto_current_role() = 'admin'
        or (
            author_user_id = auth.uid()
            and status = 'NEW'
            and is_important = false
        )
    )
)
with check (
    organization_id = public.verto_current_organization_id()
    and (
        public.verto_current_role() = 'admin'
        or (
            author_user_id = auth.uid()
            and updated_by_user_id = auth.uid()
            and status = 'NEW'
            and is_important = false
        )
    )
);

commit;
