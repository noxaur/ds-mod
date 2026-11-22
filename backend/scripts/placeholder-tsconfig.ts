import { existsSync, mkdirSync, writeFileSync } from "node:fs";
import { join } from "node:path";

/** @keep `void prepare` writes .void/tsconfig.json last, but first loads auth.ts through the tsconfig.json that extends
 * it, so a fresh checkout stops at "Tsconfig not found". An empty one lets it start. */
const generated = join(import.meta.dirname, "../.void/tsconfig.json");

if (!existsSync(generated)) {
  mkdirSync(join(generated, ".."), { recursive: true });
  writeFileSync(generated, "{}\n");
}
