// The AI provider used today: Claude Haiku 5.5, Anthropic's smallest and cheapest model
// ($0.10 per million input tokens, $0.50 per million output tokens; one estimate costs well under 0.1 cent).
import Anthropic from "npm:@anthropic-ai/sdk@0.126.0";
import { cleanEstimate, ESTIMATE_SCHEMA, type Estimate, type EstimateProvider, SYSTEM_PROMPT } from "./logic.ts";

export class ClaudeProvider implements EstimateProvider {
  readonly name = "claude-haiku-5-5";
  private client: Anthropic;

  constructor(apiKey: string) {
    this.client = new Anthropic({ apiKey, maxRetries: 1, timeout: 30_000 });
  }

  async estimate(dish: string): Promise<Estimate> {
    const response = await this.client.messages.create({
      model: "claude-haiku-5-5",
      max_tokens: 2000,
      system: SYSTEM_PROMPT,
      output_config: { effort: "low", format: { type: "json_schema", schema: ESTIMATE_SCHEMA } },
      messages: [{ role: "user", content: `Dish: ${dish}` }],
    });
    if (response.stop_reason !== "end_turn") throw new Error(`no estimate: ${response.stop_reason}`);
    const text = response.content.find((b) => b.type === "text");
    if (!text || text.type !== "text") throw new Error("no text in answer");
    return cleanEstimate(JSON.parse(text.text));
  }
}
