// The AI behind "Eat out" estimates. Swap providers by adding another EstimateProvider.

import Anthropic from "npm:@anthropic-ai/sdk@0.130.0";

export type Estimate = { low: number; typical: number; high: number };

export interface EstimateProvider {
  /** Stored with each cached answer, e.g. "anthropic:claude-haiku-5-5". */
  name: string;
  estimate(dish: string): Promise<Estimate>;
}

// The plan's choice: the smallest, cheapest Claude model.
const MODEL = "claude-haiku-5-5";

const SYSTEM =
  "You estimate the energy content of restaurant and takeaway dishes for a personal food diary " +
  "used in the Netherlands. Given a dish name, estimate the kcal of one typical restaurant serving " +
  "as eaten (including usual sides and sauces that come with that dish). Give a realistic range: " +
  "low and high cover most restaurants, typical is the most likely value. If the text is not a " +
  "food or drink, answer 0 for all three.";

const SCHEMA = {
  type: "object",
  properties: {
    low: { type: "integer", description: "kcal, lower end" },
    typical: { type: "integer", description: "kcal, most likely" },
    high: { type: "integer", description: "kcal, upper end" },
  },
  required: ["low", "typical", "high"],
  additionalProperties: false,
};

/** Checks the answer is a sensible range; throws otherwise so nothing wrong gets cached. */
export function checkEstimate(value: unknown): Estimate {
  const v = value as Partial<Estimate> | null;
  const ok = (n: unknown): n is number => typeof n === "number" && Number.isFinite(n);
  if (!v || !ok(v.low) || !ok(v.typical) || !ok(v.high)) throw new Error("not an estimate");
  const [low, typical, high] = [v.low, v.typical, v.high].map(Math.round);
  if (low <= 0 || low > typical || typical > high || high > 5000) throw new Error(`bad range ${low}/${typical}/${high}`);
  return { low, typical, high };
}

export function anthropicProvider(apiKey: string): EstimateProvider {
  const client = new Anthropic({ apiKey, timeout: 30_000, maxRetries: 1 });
  return {
    name: `anthropic:${MODEL}`,
    async estimate(dish: string): Promise<Estimate> {
      const response = await client.messages.create({
        model: MODEL,
        max_tokens: 2000,
        system: SYSTEM,
        output_config: { effort: "low", format: { type: "json_schema", schema: SCHEMA } },
        messages: [{ role: "user", content: `Dish: ${dish}` }],
      });
      if (response.stop_reason !== "end_turn") throw new Error(`stopped: ${response.stop_reason}`);
      const text = response.content.find((block) => block.type === "text");
      if (!text || text.type !== "text") throw new Error("no text in answer");
      return checkEstimate(JSON.parse(text.text));
    },
  };
}
