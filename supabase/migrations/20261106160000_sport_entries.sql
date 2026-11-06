-- Round 11: simple sport (phone database version 7). Only adds things.
--   * sport_entries: what someone did, the kcal it burned and optionally the minutes; typed in, or
--     later read from another app through Health Connect (external_id = its record id).
--   * Private, like the diary: each person reads and writes only their own rows.

create table if not exists public.sport_entries (
  id text primary key,
  user_id uuid not null references auth.users (id) on delete cascade,
  date date not null,
  name text not null,
  kcal int not null,
  created_at bigint not null,
  minutes int,
  external_id text,
  updated_at bigint not null,
  deleted boolean not null default false,
  server_updated_at timestamptz not null default clock_timestamp()
);
create trigger sync_stamp before insert or update on public.sport_entries for each row execute function public.sync_stamp();
create index on public.sport_entries (server_updated_at);
alter table public.sport_entries enable row level security;
revoke all on public.sport_entries from anon;
grant select, insert, update on public.sport_entries to authenticated;

create policy "own sport" on public.sport_entries for all to authenticated
  using (user_id = auth.uid()) with check (user_id = auth.uid());
