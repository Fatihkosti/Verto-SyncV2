-- Verto v121: tenant-scoped educational topics and audience targeting.
-- Apply with the Supabase migration role. No seed/fallback educational content is inserted.

begin;

create table if not exists public.educational_topics (
    organization_id uuid not null,
    topic_id uuid not null default gen_random_uuid(),
    title text not null check (length(btrim(title)) > 0),
    summary text not null check (length(btrim(summary)) > 0),
    full_content text not null check (length(btrim(full_content)) > 0),
    category text not null check (category in ('INVENTORY','SALES','PAYMENTS','CUSTOMERS','OPERATIONS')),
    is_active boolean not null default true,
    created_by_user_id uuid not null,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    primary key (organization_id, topic_id)
);

create table if not exists public.educational_topic_targets (
    organization_id uuid not null,
    topic_id uuid not null,
    target_type text not null check (target_type in ('ALL','ROLE','USER')),
    target_value text not null check (length(btrim(target_value)) > 0),
    primary key (organization_id, topic_id, target_type, target_value),
    constraint educational_topic_targets_topic_fk
        foreign key (organization_id, topic_id)
        references public.educational_topics (organization_id, topic_id)
        on update cascade on delete cascade,
    constraint educational_topic_targets_all_value_check
        check (target_type <> 'ALL' or target_value = '*')
);

create index if not exists educational_topics_organization_idx
    on public.educational_topics (organization_id);
create index if not exists educational_topics_org_active_idx
    on public.educational_topics (organization_id, is_active, updated_at desc);
create index if not exists educational_topic_targets_topic_idx
    on public.educational_topic_targets (organization_id, topic_id);
create index if not exists educational_topic_targets_type_idx
    on public.educational_topic_targets (target_type);
create index if not exists educational_topic_targets_value_idx
    on public.educational_topic_targets (target_value);
create index if not exists educational_topic_targets_lookup_idx
    on public.educational_topic_targets (organization_id, target_type, target_value, topic_id);

create or replace function public.verto_current_organization_id()
returns uuid
language sql
stable
security definer
set search_path = public
as $$
    select u.organization_id
    from public.app_users u
    where u.id = auth.uid()
      and coalesce(u.is_active, true)
    limit 1
$$;

create or replace function public.verto_current_role()
returns text
language sql
stable
security definer
set search_path = public
as $$
    select lower(coalesce(u.role, ''))
    from public.app_users u
    where u.id = auth.uid()
      and coalesce(u.is_active, true)
    limit 1
$$;

create or replace function public.verto_can_read_educational_topic(
    p_organization_id uuid,
    p_topic_id uuid,
    p_is_active boolean
)
returns boolean
language sql
stable
security definer
set search_path = public
as $$
    select
        p_organization_id = public.verto_current_organization_id()
        and (
            public.verto_current_role() = 'admin'
            or (
                p_is_active
                and exists (
                    select 1
                    from public.educational_topic_targets t
                    where t.organization_id = p_organization_id
                      and t.topic_id = p_topic_id
                      and (
                          (t.target_type = 'ALL' and t.target_value = '*')
                          or (t.target_type = 'ROLE' and lower(t.target_value) = public.verto_current_role())
                          or (t.target_type = 'USER' and t.target_value = auth.uid()::text)
                      )
                )
            )
        )
$$;

alter table public.educational_topics enable row level security;
alter table public.educational_topic_targets enable row level security;

revoke all on public.educational_topics from anon;
revoke all on public.educational_topic_targets from anon;
grant select, insert, update, delete on public.educational_topics to authenticated;
grant select, insert, update, delete on public.educational_topic_targets to authenticated;

-- Idempotent policy replacement.
drop policy if exists educational_topics_select on public.educational_topics;
drop policy if exists educational_topics_admin_insert on public.educational_topics;
drop policy if exists educational_topics_admin_update on public.educational_topics;
drop policy if exists educational_topics_admin_delete on public.educational_topics;
drop policy if exists educational_targets_select on public.educational_topic_targets;
drop policy if exists educational_targets_admin_insert on public.educational_topic_targets;
drop policy if exists educational_targets_admin_update on public.educational_topic_targets;
drop policy if exists educational_targets_admin_delete on public.educational_topic_targets;

create policy educational_topics_select
on public.educational_topics
for select to authenticated
using (
    public.verto_can_read_educational_topic(organization_id, topic_id, is_active)
);

create policy educational_topics_admin_insert
on public.educational_topics
for insert to authenticated
with check (
    organization_id = public.verto_current_organization_id()
    and created_by_user_id = auth.uid()
    and public.verto_current_role() = 'admin'
);

create policy educational_topics_admin_update
on public.educational_topics
for update to authenticated
using (
    organization_id = public.verto_current_organization_id()
    and public.verto_current_role() = 'admin'
)
with check (
    organization_id = public.verto_current_organization_id()
    and public.verto_current_role() = 'admin'
);

create policy educational_topics_admin_delete
on public.educational_topics
for delete to authenticated
using (
    organization_id = public.verto_current_organization_id()
    and public.verto_current_role() = 'admin'
);

create policy educational_targets_select
on public.educational_topic_targets
for select to authenticated
using (
    exists (
        select 1
        from public.educational_topics topic
        where topic.organization_id = educational_topic_targets.organization_id
          and topic.topic_id = educational_topic_targets.topic_id
          and public.verto_can_read_educational_topic(
              topic.organization_id,
              topic.topic_id,
              topic.is_active
          )
    )
);

create policy educational_targets_admin_insert
on public.educational_topic_targets
for insert to authenticated
with check (
    organization_id = public.verto_current_organization_id()
    and public.verto_current_role() = 'admin'
);

create policy educational_targets_admin_update
on public.educational_topic_targets
for update to authenticated
using (
    organization_id = public.verto_current_organization_id()
    and public.verto_current_role() = 'admin'
)
with check (
    organization_id = public.verto_current_organization_id()
    and public.verto_current_role() = 'admin'
);

create policy educational_targets_admin_delete
on public.educational_topic_targets
for delete to authenticated
using (
    organization_id = public.verto_current_organization_id()
    and public.verto_current_role() = 'admin'
);

commit;
