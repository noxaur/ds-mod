import { existsSync, mkdirSync, writeFileSync } from "node:fs";
import { join } from "node:path";

// `void prepare` writes .void/tsconfig.json as its last step, but first loads auth.ts through tsconfig.json, which
// extends that file, so on a fresh checkout it stops at "Tsconfig not found". An empty one lets it start.
const generated = join(import.meta.dirname, "../.void/tsconfig.json");

if (!existsSync(generated)) {
  mkdirSync(join(generated, ".."), { recursive: true });
  writeFileSync(generated, "{}\n");
}
