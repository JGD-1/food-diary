// "estimate": gives a kcal range for a restaurant dish described in words.
// The app calls POST /functions/v1/estimate with {"dish": "..."} and gets {"low","typical","high","cached"}.
// The AI key lives only here, as the secret ANTHROPIC_API_KEY. Answers are cached per dish for everyone.
import { createClient } from "npm:@supabase/supabase-js@2.117.1";
import { ClaudeProvider } from "./claude.ts";
import { dishKey, type EstimateProvider, MAX_DISH_LENGTH } from "./logic.ts";

/** Safety net next to the Anthropic spending limit: at most this many new (paid) estimates a day. */
const MAX_NEW_ESTIMATES_PER_DAY = 200;

const supabase = createClient(Deno.env.get("SUPABASE_URL")!, Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")!, {
  auth: { persistSession: false },
});

function provider(): EstimateProvider {
  const key = Deno.env.get("ANTHROPIC_API_KEY");
  if (!key) throw new Error("ANTHROPIC_API_KEY is not set");
  return new ClaudeProvider(key);
}

const json = (status: number, body: unknown) =>
  new Response(JSON.stringify(body), { status, headers: { "Content-Type": "application/json" } });

Deno.serve(async (req) => {
  if (req.method !== "POST") return json(405, { error: "use POST" });

  let dish: string;
  try {
    dish = String((await req.json())?.dish ?? "").trim().slice(0, MAX_DISH_LENGTH);
  } catch {
    return json(400, { error: "send {\"dish\": \"...\"}" });
  }
  const key = dishKey(dish);
  if (!key) return json(400, { error: "empty dish" });

  const { data: hit, error: readError } = await supabase
    .from("estimate_cache").select("low, typical, high").eq("dish_key", key).maybeSingle();
  if (readError) console.error("cache read", readError.message);
  if (hit) return json(200, { ...hit, cached: true });

  const since = new Date(Date.now() - 24 * 3600 * 1000).toISOString();
  const { count } = await supabase
    .from("estimate_cache").select("dish_key", { count: "exact", head: true }).gte("created_at", since);
  if ((count ?? 0) >= MAX_NEW_ESTIMATES_PER_DAY) return json(429, { error: "daily limit reached" });

  try {
    const p = provider();
    const e = await p.estimate(dish);
    const { error: writeError } = await supabase
      .from("estimate_cache").upsert({ dish_key: key, dish, ...e, provider: p.name });
    if (writeError) console.error("cache write", writeError.message);
    return json(200, { ...e, cached: false });
  } catch (err) {
    console.error("estimate failed", err instanceof Error ? err.message : err);
    return json(502, { error: "estimate failed" });
  }
});
