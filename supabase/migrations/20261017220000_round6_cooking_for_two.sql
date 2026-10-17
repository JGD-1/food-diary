-- Round 6: cooking for two or more (phone database version 5). Only adds things; existing rows keep their values.
--   * batch_portions: who dished a plate up (served_by) and which container it came from (container_id)
--   * batch_containers: fridge and freezer containers filled at the dish-up, shared in the household
--   * household_members.display_name: the Google name, so the app can say "Guido's plate"
--   * a member may save a plate for another member of the same household (it waits until they add it)

alter table public.batch_portions
  add column if not exists served_by uuid references auth.users (id) on delete set null,
  add column if not exists container_id text;

-- A portion may be for anyone in the household (a plate waiting for them), but only for someone in it.
-- The same rule covers changing it later: that person adds it (sets log_entry_id), either removes it.
alter policy "household portions" on public.batch_portions
  using (household_id = public.my_household())
  with check (
    household_id = public.my_household()
    and exists (select 1 from public.household_members m where m.user_id = batch_portions.user_id and m.household_id = batch_portions.household_id)
    and (served_by is null or exists (
      select 1 from public.household_members m where m.user_id = batch_portions.served_by and m.household_id = batch_portions.household_id
    ))
  );

create table if not exists public.batch_containers (
  id text primary key,
  batch_id text not null references public.batches (id) on delete cascade,
  household_id uuid not null references public.households (id) on delete cascade,
  grams float8 not null,
  place text not null default 'FRIDGE' check (place in ('FRIDGE', 'FREEZER')),
  number int not null default 1,
  updated_at bigint not null,
  deleted boolean not null default false,
  server_updated_at timestamptz not null default clock_timestamp()
);
create trigger sync_stamp before insert or update on public.batch_containers for each row execute function public.sync_stamp();
create index on public.batch_containers (server_updated_at);
create index on public.batch_containers (batch_id);
create index on public.batch_containers (household_id);
alter table public.batch_containers enable row level security;
revoke all on public.batch_containers from anon;
grant select, insert, update on public.batch_containers to authenticated;
-- Shared within the household, like batches.
create policy "household containers" on public.batch_containers for all to authenticated
  using (household_id = public.my_household()) with check (household_id = public.my_household());

-- Names: only the display name is shared (nothing from profiles). The household can already read
-- household_members ("see my household members"); people change only their own name, through this function.
alter table public.household_members add column if not exists display_name text;

create or replace function public.set_display_name(name text)
returns void
language plpgsql security definer set search_path = public as $$
begin
  if auth.uid() is null then raise exception 'not signed in'; end if;
  update public.household_members m
    set display_name = nullif(left(trim(name), 80), '')
    where m.user_id = auth.uid();
end
$$;
revoke execute on function public.set_display_name(text) from public, anon;
grant execute on function public.set_display_name(text) to authenticated;

-- Joining a household: containers move along with the batches (same as before otherwise).
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
      update public.batch_containers set household_id = new_hid, updated_at = greatest(updated_at + 1, now_ms) where batch_containers.household_id = old_hid;
      update public.pans set household_id = new_hid, updated_at = greatest(updated_at + 1, now_ms) where pans.household_id = old_hid;
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
