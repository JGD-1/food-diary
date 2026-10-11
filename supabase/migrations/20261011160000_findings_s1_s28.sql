-- Findings S1-S28 (phone database version 4). Only adds things; existing rows keep their values.
--   * "Makes ... pieces" on recipes and batches (S11)
--   * weight after cooking on recipes (S12)
--   * "Frozen" batches (S15)
--   * pans: a household's pans with their empty weight (S9)
-- Ingredient amount labels (S13) live inside ingredients_json, so no column for them.

alter table public.recipes
  add column if not exists pieces int,
  add column if not exists cooked_weight_g float8;

alter table public.batches
  add column if not exists pieces int,
  add column if not exists frozen boolean not null default false;

create table if not exists public.pans (
  id text primary key,
  household_id uuid not null references public.households (id) on delete cascade,
  name text not null,
  grams float8 not null,
  updated_at bigint not null,
  deleted boolean not null default false,
  server_updated_at timestamptz not null default clock_timestamp()
);
create trigger sync_stamp before insert or update on public.pans for each row execute function public.sync_stamp();
create index on public.pans (server_updated_at);
create index on public.pans (household_id);
alter table public.pans enable row level security;
revoke all on public.pans from anon;
grant select, insert, update on public.pans to authenticated;
-- Shared within the household, like batches.
create policy "household pans" on public.pans for all to authenticated
  using (household_id = public.my_household()) with check (household_id = public.my_household());

-- Joining a household: pans move along with the recipes and batches (same as before otherwise).
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
