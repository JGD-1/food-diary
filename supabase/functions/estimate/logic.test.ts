// Run with: node --experimental-strip-types --test supabase/functions/estimate/logic.test.ts
// (or: deno test supabase/functions/estimate/)
import { strict as assert } from "node:assert";
import { test } from "node:test";
import { cleanEstimate, dishKey } from "./logic.ts";

test("dish key matches the app's rule", () => {
  assert.equal(dishKey("  Pizza   Margherita! "), "pizza margherita");
  assert.equal(dishKey("Crêpe"), dishKey("crepe"));
  assert.equal(dishKey("Kapsalon (groot)"), "kapsalon groot");
  assert.equal(dishKey("x".repeat(300)).length, 200);
});

test("estimates are rounded and checked", () => {
  assert.deepEqual(cleanEstimate({ low: 452, typical: 561, high: 678 }), { low: 450, typical: 560, high: 680 });
  assert.throws(() => cleanEstimate({ low: 700, typical: 500, high: 800 }));
  assert.throws(() => cleanEstimate({ low: 0, typical: 0, high: 0 }));
  assert.throws(() => cleanEstimate({ low: 100, typical: 200, high: 9000 }));
  assert.throws(() => cleanEstimate(null));
});
