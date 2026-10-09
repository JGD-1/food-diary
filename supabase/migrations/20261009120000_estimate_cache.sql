-- Restaurant estimates, cached per dish and shared by everyone, so a dish is only paid for once.
-- Only the "estimate" server function (service role) reads and writes this table:
-- row level security is on and there are no policies, so the app can't touch it directly.
create table if not exists public.estimate_cache (
  dish_key   text primary key,
  dish       text not null,
  low        integer not null check (low > 0),
  typical    integer not null check (typical >= low),
  high       integer not null check (high >= typical and high <= 5000),
  provider   text not null,
  created_at timestamptz not null default now()
);

create index if not exists estimate_cache_created_at on public.estimate_cache (created_at);

alter table public.estimate_cache enable row level security;
