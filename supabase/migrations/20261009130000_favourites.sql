-- Stream 7: per-person favourite recipes (Room table "favourite", version 2). Private to their owner.
create table public.favourites (
  id text primary key,
  user_id uuid not null references auth.users (id) on delete cascade,
  recipe_id text not null,
  usual_portion_json text,
  updated_at bigint not null,
  deleted boolean not null default false,
  server_updated_at timestamptz not null default clock_timestamp()
);
create trigger sync_stamp before insert or update on public.favourites for each row execute function public.sync_stamp();
create index on public.favourites (server_updated_at);
alter table public.favourites enable row level security;
revoke all on public.favourites from anon;
grant select, insert, update on public.favourites to authenticated;
create policy "own favourites" on public.favourites for all to authenticated
  using (user_id = auth.uid()) with check (user_id = auth.uid());
