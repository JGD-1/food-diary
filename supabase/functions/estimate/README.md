# estimate

Gives a kcal range for a restaurant dish described in words (plan.md, "Eat out"). Owned by stream 1.

- App call: `POST /functions/v1/estimate` with `{"dish": "pizza margherita"}`, the Supabase key as `Authorization: Bearer`.
- Answer: `{"low": 450, "typical": 560, "high": 680, "cached": false}`.
- Each dish (lower case, accents and punctuation ignored) is asked of the AI only once. After that it comes from the
  `estimate_cache` table (`supabase/migrations/20261009120000_estimate_cache.sql`), for everyone.
- A safety net next to the Anthropic spending limit: at most 200 new estimates a day.
- Provider: `claude.ts` (Claude Haiku 5.5). To switch provider, add another `EstimateProvider` (see `logic.ts`) and
  return it from `provider()` in `index.ts`. The app doesn't change.

Needs the secret `ANTHROPIC_API_KEY` in the Supabase project (Edge Functions, then Secrets).
`SUPABASE_URL` and `SUPABASE_SERVICE_ROLE_KEY` are provided by Supabase automatically.

Tests: `node --experimental-strip-types --test supabase/functions/estimate/logic.test.ts`
