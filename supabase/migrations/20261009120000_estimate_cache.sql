-- Stream 1: restaurant estimates ("Eat out").
--
-- One row per dish, shared by everyone, so the same dish is never paid for twice.
-- Only the "estimate" server function (service role) reads and writes it; phones never touch it
-- directly, so row level security is on with no policies.

create table public.estimate_cache (
  dish_key text primary key,          -- lower case, words only, single spaces (same rule as the app)
  dish text not null,                 -- as first typed
  low_kcal integer not null check (low_kcal >= 0),
  typical_kcal integer not null check (typical_kcal >= low_kcal),
  high_kcal integer not null check (high_kcal >= typical_kcal),
  provider text not null,             -- e.g. "anthropic:claude-haiku-5-5", so estimates can be redone later
  created_at timestamptz not null default now()
);

create index estimate_cache_created_at on public.estimate_cache (created_at);

alter table public.estimate_cache enable row level security;
revoke all on public.estimate_cache from anon, authenticated;
