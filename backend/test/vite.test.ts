import { readFileSync } from "node:fs";
import { expect, test } from "vitest";
import pkg from "../package.json" with { type: "json" };

// Void and Vite+ must share one Vite. When `vite` names another, `vp check` fails with "Excessive stack depth" in
// vite.config.ts, which says nothing of the cause.
test("vite is the Vite+ core of the same version as vite-plus, in package.json and in the overrides", () => {
  const core = `npm:@voidzero-dev/vite-plus-core@${pkg.devDependencies["vite-plus"]}`;
  const workspace = readFileSync(new URL("../pnpm-workspace.yaml", import.meta.url).pathname, "utf8");
  const fix = `Set vite to ${core} in package.json and in the overrides of pnpm-workspace.yaml.`;
  expect(pkg.devDependencies.vite, fix).toBe(core);
  expect(/^\s+vite: (.+)$/m.exec(workspace)?.[1], fix).toBe(core);
});
