-- Findings 1-19 (phone database version 3). Only adds things; existing rows keep their values.
--   * fibre, sugar, salt on foods and diary lines (finding 19)
--   * serving and pack size on foods (finding 2)
--   * optional daily protein goal (finding 16)
--   * "Finished" batches (finding 14)
--   * pinned foods: favourites.recipe_id may be empty, food_id is new (finding 10)
--   * batch_portions: grams each person took from a batch, shared in the household (finding 14)

alter table public.foods
  add column if not exists fibre float8,
  add column if not exists sugar float8,
  add column if not exists salt float8,
  add column if not exists serving_g float8,
  add column if not exists package_g float8;

alter table public.log_entries
  add column if not exists fibre float8,
  add column if not exists sugar float8,
  add column if not exists salt float8;

alter table public.profiles add column if not exists protein_goal_g int;

alter table public.batches add column if not exists finished_on date;

alter table public.favourites
  alter column recipe_id drop not null,
  add column if not exists food_id text;

-- Grams only (no kcal, no diary), so the household sees what is left in the pot
-- without seeing each other's diaries.
create table if not exists public.batch_portions (
  id text primary key,
  batch_id text not null references public.batches (id) on delete cascade,
  household_id uuid not null references public.households (id) on delete cascade,
  user_id uuid not null references auth.users (id) on delete cascade,
  grams float8 not null,
  log_entry_id text,
  updated_at bigint not null,
  deleted boolean not null default false,
  server_updated_at timestamptz not null default clock_timestamp()
);
create trigger sync_stamp before insert or update on public.batch_portions for each row execute function public.sync_stamp();
create index on public.batch_portions (server_updated_at);
create index on public.batch_portions (batch_id);
alter table public.batch_portions enable row level security;
revoke all on public.batch_portions from anon;
grant select, insert, update on public.batch_portions to authenticated;
-- Shared within the household, like batches.
create policy "household portions" on public.batch_portions for all to authenticated
  using (household_id = public.my_household()) with check (household_id = public.my_household());

-- Joining a household: portions move along with the batches (same as before otherwise).
create or replace function public.join_household(code text)
returns table (household_id uuid, invite_code text, member_count int, moved_from uuid)
language plpgsql security definer set search_path = public as $$
declare
  old_hid uuid;
  new_hid uuid;
  now_ms bigint := (extract(epoch from clock_timestamp()) * 1000)::bigint;
begin
  if auth.uid() is null then raise exception 'not signed in'; end if;
  select h.id into new_hid from public.households h where h.invite_code = upper(trim(code));
  if new_hid is null then raise exception 'code not found' using errcode = 'P0002'; end if;
  select m.household_id into old_hid from public.household_members m where m.user_id = auth.uid();

  if old_hid is null then
    insert into public.household_members (user_id, household_id) values (auth.uid(), new_hid);
  elsif old_hid = new_hid then
    old_hid := null;
  else
    if (select count(*) from public.household_members m where m.household_id = old_hid) = 1 then
      update public.recipes set household_id = new_hid, updated_at = greatest(updated_at + 1, now_ms) where recipes.household_id = old_hid;
      update public.batches set household_id = new_hid, updated_at = greatest(updated_at + 1, now_ms) where batches.household_id = old_hid;
      update public.batch_portions set household_id = new_hid, updated_at = greatest(updated_at + 1, now_ms) where batch_portions.household_id = old_hid;
    else
      old_hid := null; -- nothing moved
    end if;
    update public.household_members m set household_id = new_hid where m.user_id = auth.uid();
  end if;

  return query select i.household_id, i.invite_code, i.member_count, old_hid from public.household_info(new_hid) i;
end
$$;
revoke execute on function public.join_household(text) from public, anon;
grant execute on function public.join_household(text) to authenticated;
