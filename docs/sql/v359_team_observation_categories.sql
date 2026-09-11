-- Verto v359: persist the Home information-capture category.
-- Existing observations are preserved as IDEA for backward compatibility.

begin;

alter table public.team_observations
    add column if not exists category text not null default 'IDEA';

do $$
begin
    if not exists (
        select 1 from pg_constraint
        where conname = 'team_observations_category_check'
          and conrelid = 'public.team_observations'::regclass
    ) then
        alter table public.team_observations
            add constraint team_observations_category_check
            check (category in ('IDEA','MARKET_INFO','COMPLAINT'));
    end if;
end $$;

commit;
