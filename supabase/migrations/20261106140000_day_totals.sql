-- Round 10: see each other's day (phone database version 6). Only adds things.
--   * day_totals: one kcal number per person per day, sent only while that person switches on
--     "Show my day to my partner". Nothing else from the diary is shared.
--   * Each person writes only their own rows; household members may read them.
--   * Visibility follows household membership, so joining another household needs no moving.

create table if not exists public.day_totals (
  id text primary key,
  user_id uuid not null references auth.users (id) on delete cascade,
  date date not null,
  kcal int not null,
  target_kcal int,
  updated_at bigint not null,
  deleted boolean not null default false,
  server_updated_at timestamptz not null default clock_timestamp(),
  unique (user_id, date)
);
create trigger sync_stamp before insert or update on public.day_totals for each row execute function public.sync_stamp();
create index on public.day_totals (server_updated_at);
alter table public.day_totals enable row level security;
revoke all on public.day_totals from anon;
grant select, insert, update on public.day_totals to authenticated;

create policy "own day totals" on public.day_totals for all to authenticated
  using (user_id = auth.uid()) with check (user_id = auth.uid());
create policy "household day totals" on public.day_totals for select to authenticated
  using (user_id in (select m.user_id from public.household_members m where m.household_id = public.my_household()));
