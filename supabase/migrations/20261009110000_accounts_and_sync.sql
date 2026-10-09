-- Stream 7: accounts, households and sync.
--
-- The phone database is the source of truth. These tables are a copy of each person's rows,
-- so a new phone can be filled and recipes can be shared within a household.
-- Rules (enforced by the access rules below):
--   * diary, weigh-ins, profile and own foods: only the owner can see them;
--   * recipes, variants and batch meals: everyone in the same household.
-- Every row carries the phone's `updated_at` (ms). The newest change wins; an older or equal
-- change is ignored. `server_updated_at` is set here and is what phones use to fetch changes.

-- Households ---------------------------------------------------------------------------------

create table public.households (
  id uuid primary key default gen_random_uuid(),
  invite_code text not null unique default upper(substr(md5(gen_random_uuid()::text), 1, 8)),
  created_at timestamptz not null default now()
);

-- One household per person.
create table public.household_members (
  user_id uuid primary key references auth.users (id) on delete cascade,
  household_id uuid not null references public.households (id) on delete cascade,
  joined_at timestamptz not null default now()
);
create index on public.household_members (household_id);

create or replace function public.my_household() returns uuid
language sql stable security definer set search_path = public as $$
  select household_id from public.household_members where user_id = auth.uid()
$$;

alter table public.households enable row level security;
alter table public.household_members enable row level security;
create policy "see my household" on public.households for select to authenticated
  using (id = public.my_household());
create policy "see my household members" on public.household_members for select to authenticated
  using (household_id = public.my_household());
-- No insert/update/delete policies: households change only through the functions below.

-- Sync stamp: newest change wins ---------------------------------------------------------------

create or replace function public.sync_stamp() returns trigger
language plpgsql set search_path = public as $$
begin
  if tg_op = 'UPDATE' and new.updated_at <= old.updated_at then
    return null; -- an older (or the same) change: keep what we have
  end if;
  new.server_updated_at := clock_timestamp();
  return new;
end
$$;

-- Private tables -------------------------------------------------------------------------------

create table public.profiles (
  id uuid primary key references auth.users (id) on delete cascade,
  name text not null,
  birth_year int not null,
  sex text not null,
  height_cm int not null,
  activity text not null,
  start_weight_kg float8 not null,
  target_weight_kg float8 not null,
  weekly_pace_kg float8 not null,
  manual_target_kcal int,
  updated_at bigint not null,
  deleted boolean not null default false,
  server_updated_at timestamptz not null default clock_timestamp()
);

create table public.log_entries (
  id text primary key,
  user_id uuid not null references auth.users (id) on delete cascade,
  date date not null,
  meal text not null,
  what_json text not null,
  portion_grams float8 not null,
  portion_label text,
  kcal float8 not null,
  protein float8 not null,
  carbs float8 not null,
  fat float8 not null,
  is_estimate boolean not null,
  created_at bigint not null,
  updated_at bigint not null,
  deleted boolean not null default false,
  server_updated_at timestamptz not null default clock_timestamp()
);

create table public.weigh_ins (
  id text primary key,
  user_id uuid not null references auth.users (id) on delete cascade,
  date date not null,
  kg float8 not null,
  updated_at bigint not null,
  deleted boolean not null default false,
  server_updated_at timestamptz not null default clock_timestamp()
);

-- Foods someone made themselves (label scans, typed foods). NEVO and Open Food Facts are not synced.
create table public.foods (
  id text primary key,
  owner_id uuid not null references auth.users (id) on delete cascade,
  name text not null,
  brand text,
  barcode text,
  kcal float8 not null,
  protein float8 not null,
  carbs float8 not null,
  fat float8 not null,
  source text not null,
  is_drink boolean not null,
  updated_at bigint not null,
  deleted boolean not null default false,
  server_updated_at timestamptz not null default clock_timestamp()
);

-- Household tables -----------------------------------------------------------------------------

create table public.recipes (
  id text primary key,
  household_id uuid not null references public.households (id) on delete cascade,
  name text not null,
  ingredients_json text not null,
  pinned boolean not null,
  usual_portion_json text,
  updated_at bigint not null,
  deleted boolean not null default false,
  server_updated_at timestamptz not null default clock_timestamp()
);

create table public.recipe_variants (
  id text primary key,
  base_recipe_id text not null references public.recipes (id) on delete cascade,
  name text not null,
  extras_json text not null,
  updated_at bigint not null,
  deleted boolean not null default false,
  server_updated_at timestamptz not null default clock_timestamp()
);

create table public.batches (
  id text primary key,
  household_id uuid not null references public.households (id) on delete cascade,
  recipe_id text,
  name text not null,
  ingredients_json text not null,
  cooked_weight_g float8 not null,
  cooked_on date not null,
  updated_at bigint not null,
  deleted boolean not null default false,
  server_updated_at timestamptz not null default clock_timestamp()
);

-- Stamps, indexes and access rules for all synced tables.
do $$
declare t text;
begin
  foreach t in array array['profiles', 'log_entries', 'weigh_ins', 'foods', 'recipes', 'recipe_variants', 'batches'] loop
    execute format('create trigger sync_stamp before insert or update on public.%I for each row execute function public.sync_stamp()', t);
    execute format('create index on public.%I (server_updated_at)', t);
    execute format('alter table public.%I enable row level security', t);
    execute format('revoke all on public.%I from anon', t);
    execute format('grant select, insert, update on public.%I to authenticated', t);
  end loop;
end
$$;

create policy "own profile" on public.profiles for all to authenticated
  using (id = auth.uid()) with check (id = auth.uid());
create policy "own diary" on public.log_entries for all to authenticated
  using (user_id = auth.uid()) with check (user_id = auth.uid());
create policy "own weigh-ins" on public.weigh_ins for all to authenticated
  using (user_id = auth.uid()) with check (user_id = auth.uid());
create policy "own foods" on public.foods for all to authenticated
  using (owner_id = auth.uid()) with check (owner_id = auth.uid());
create policy "household recipes" on public.recipes for all to authenticated
  using (household_id = public.my_household()) with check (household_id = public.my_household());
create policy "household batches" on public.batches for all to authenticated
  using (household_id = public.my_household()) with check (household_id = public.my_household());
create policy "household variants" on public.recipe_variants for all to authenticated
  using (exists (select 1 from public.recipes r where r.id = base_recipe_id and r.household_id = public.my_household()))
  with check (exists (select 1 from public.recipes r where r.id = base_recipe_id and r.household_id = public.my_household()));

-- Household functions --------------------------------------------------------------------------

create or replace function public.household_info(hid uuid)
returns table (household_id uuid, invite_code text, member_count int)
language sql stable security definer set search_path = public as $$
  select h.id, h.invite_code, (select count(*)::int from public.household_members m where m.household_id = h.id)
  from public.households h where h.id = hid
$$;
revoke execute on function public.household_info(uuid) from public, anon, authenticated;

-- Called after every sign-in: makes a household of one if the person has none yet.
create or replace function public.ensure_household()
returns table (household_id uuid, invite_code text, member_count int)
language plpgsql security definer set search_path = public as $$
declare hid uuid;
begin
  if auth.uid() is null then raise exception 'not signed in'; end if;
  select m.household_id into hid from public.household_members m where m.user_id = auth.uid();
  if hid is null then
    insert into public.households default values returning id into hid;
    insert into public.household_members (user_id, household_id) values (auth.uid(), hid);
  end if;
  return query select * from public.household_info(hid);
end
$$;

-- Join someone's household with their code. If you were on your own, your recipes and batches
-- come with you; otherwise they stay with the household you leave. (No deletes: an emptied
-- household simply stays behind, unused.)
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
    else
      old_hid := null; -- nothing moved
    end if;
    update public.household_members m set household_id = new_hid where m.user_id = auth.uid();
  end if;

  return query select i.household_id, i.invite_code, i.member_count, old_hid from public.household_info(new_hid) i;
end
$$;

revoke execute on function public.ensure_household() from public, anon;
revoke execute on function public.join_household(text) from public, anon;
grant execute on function public.ensure_household() to authenticated;
grant execute on function public.join_household(text) to authenticated;
revoke execute on function public.my_household() from public, anon;
