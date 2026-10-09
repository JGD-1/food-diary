// Stream 1: "Eat out" estimates.
//
// POST { "dish": "pizza margherita" }  ->  { "low": 700, "typical": 850, "high": 1050, "cached": false }
// Text only: the dish name is the only thing sent to the AI. Answers are cached per dish in
// public.estimate_cache (shared), so a dish is only paid for once. The AI key lives only in the
// function secret ANTHROPIC_API_KEY. Errors: 400 bad input, 503 not set up / daily limit, 502 AI failed.

import { createClient } from "npm:@supabase/supabase-js@2.117.2";
import { type Estimate, type EstimateProvider, anthropicProvider } from "./provider.ts";

// Safety net on top of the Anthropic spending limit: new (uncached) dishes per day, for everyone.
const MAX_NEW_DISHES_PER_DAY = 200;

/** Same rule as the app (feature/food dishKey): lower case, words only, single spaces. */
export function dishKey(dish: string): string {
  return dish.toLowerCase().replace(/[^\p{L}\p{N}]+/gu, " ").trim();
}

const json = (status: number, body: unknown) =>
  new Response(JSON.stringify(body), { status, headers: { "Content-Type": "application/json" } });

Deno.serve(async (req) => {
  if (req.method !== "POST") return json(405, { error: "method" });

  let dish: unknown;
  try {
    dish = (await req.json())?.dish;
  } catch {
    return json(400, { error: "bad_json" });
  }
  if (typeof dish !== "string") return json(400, { error: "no_dish" });
  dish = dish.trim();
  const key = dishKey(dish as string);
  if (key.length < 2 || (dish as string).length > 120) return json(400, { error: "bad_dish" });

  const db = createClient(Deno.env.get("SUPABASE_URL")!, Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")!, {
    auth: { persistSession: false },
  });

  const { data: hit } = await db
    .from("estimate_cache")
    .select("low_kcal, typical_kcal, high_kcal")
    .eq("dish_key", key)
    .maybeSingle();
  if (hit) return json(200, { low: hit.low_kcal, typical: hit.typical_kcal, high: hit.high_kcal, cached: true });

  const apiKey = Deno.env.get("ANTHROPIC_API_KEY");
  if (!apiKey) return json(503, { error: "not_configured" });

  const since = new Date(Date.now() - 24 * 60 * 60 * 1000).toISOString();
  const { count } = await db
    .from("estimate_cache")
    .select("dish_key", { count: "exact", head: true })
    .gte("created_at", since);
  if ((count ?? 0) >= MAX_NEW_DISHES_PER_DAY) return json(503, { error: "daily_limit" });

  const provider: EstimateProvider = anthropicProvider(apiKey);
  let estimate: Estimate;
  try {
    estimate = await provider.estimate(dish as string);
  } catch (e) {
    console.error("estimate failed", e);
    return json(502, { error: "provider_failed" });
  }

  const { error } = await db.from("estimate_cache").upsert(
    {
      dish_key: key,
      dish,
      low_kcal: estimate.low,
      typical_kcal: estimate.typical,
      high_kcal: estimate.high,
      provider: provider.name,
    },
    { onConflict: "dish_key", ignoreDuplicates: true },
  );
  if (error) console.error("cache write failed", error);

  return json(200, { ...estimate, cached: false });
});
