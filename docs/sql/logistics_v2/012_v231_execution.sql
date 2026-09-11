-- Verto Logistics V2 v231 execution contract. Remote runtime remains OFF.
-- Additive only: customs lifecycle facts and centralized document actor metadata.

alter table public.logistics_milestones
  add column if not exists customs_started_at bigint,
  add column if not exists customs_completed_at bigint;

alter table public.logistics_documents
  add column if not exists employee_id text,
  add column if not exists employee_name_snapshot text;

alter table public.logistics_milestones
  drop constraint if exists logistics_customs_time_order,
  add constraint logistics_customs_time_order check (
    customs_completed_at is null or
    (customs_started_at is not null and customs_completed_at >= customs_started_at)
  );

comment on column public.logistics_milestones.customs_started_at is
  'Occurred-at timestamp for confirmed customs broker pickup/start.';
comment on column public.logistics_milestones.customs_completed_at is
  'Occurred-at timestamp for customs completion; custody remains with broker until explicit next handoff.';
comment on column public.logistics_documents.employee_id is
  'Employee actor who attached the document, captured centrally with the attachment metadata.';
