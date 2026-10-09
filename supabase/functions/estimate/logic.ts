// Plain logic for the estimate function, with no Deno or network imports, so it can be tested anywhere.

export interface Estimate {
  low: number;
  typical: number;
  high: number;
}

/** Something that can estimate a restaurant dish. Swap the AI provider by adding another one of these. */
export interface EstimateProvider {
  readonly name: string;
  estimate(dish: string): Promise<Estimate>;
}

export const MAX_DISH_LENGTH = 200;

/** Same rule as the app (EstimateRules.dishKey): case, accents, punctuation and spacing don't matter. */
export function dishKey(dish: string): string {
  return dish
    .toLowerCase()
    .normalize("NFD")
    .replace(/\p{M}+/gu, "")
    .replace(/[^\p{L}\p{N}%]+/gu, " ")
    .trim()
    .replace(/\s+/g, " ")
    .slice(0, MAX_DISH_LENGTH);
}

/** Rounds to 10 kcal and checks the answer can be one restaurant meal. Throws when it can't. */
export function cleanEstimate(raw: unknown): Estimate {
  const r = raw as Record<string, unknown>;
  const round = (v: unknown) => Math.round(Number(v) / 10) * 10;
  const e = { low: round(r?.low), typical: round(r?.typical), high: round(r?.high) };
  const ok = [e.low, e.typical, e.high].every(Number.isFinite) &&
    e.low > 0 && e.low <= e.typical && e.typical <= e.high && e.high <= 5000;
  if (!ok) throw new Error(`unusable estimate: ${JSON.stringify(raw)}`);
  return e;
}

export const SYSTEM_PROMPT =
  "You estimate the energy of restaurant meals in the Netherlands. " +
  "Given a dish as a diner would describe it, think of how restaurants, cafés and takeaways in the " +
  "Netherlands typically serve it (usual portion, cooking fat, sauce and standard sides that come with it). " +
  "Return kcal for the whole portion as eaten: low (a small or light version), typical (the most common " +
  "version) and high (a large or rich version). If the text mentions a size, extras or sides, include them. " +
  "If it is not food or drink, still answer with your best guess for a typical restaurant meal.";

/** JSON schema for structured output, so the answer always has these three whole numbers. */
export const ESTIMATE_SCHEMA = {
  type: "object",
  properties: {
    low: { type: "integer", description: "kcal, small or light version" },
    typical: { type: "integer", description: "kcal, most common version" },
    high: { type: "integer", description: "kcal, large or rich version" },
  },
  required: ["low", "typical", "high"],
  additionalProperties: false,
} as const;
