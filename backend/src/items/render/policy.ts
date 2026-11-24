import { safeTexture } from "../../upstream/texture.ts";
import type { ItemHints } from "../store.ts";

/** @keep The game glints these whatever the server sends, so the mod, which sets the glint explicitly, must be told. */
const SELF_GLINTING = new Set([
  "minecraft:enchanted_golden_apple",
  "minecraft:nether_star",
  "minecraft:experience_bottle",
  "minecraft:enchanted_book",
  "minecraft:end_crystal",
  "minecraft:written_book",
]);

/** @keep The API sends no enchantments, so a glint is inferred from the catalog, the material and what players saw. */
export const glints = (material: string, catalogGlowing: boolean, hint: ItemHints["glint"] | undefined): boolean => {
  if (catalogGlowing) {
    return true;
  }
  if (SELF_GLINTING.has(material)) {
    return hint !== "never" && hint !== "mixed";
  }
  return hint === "always";
};

export const skullTexture = (
  own: unknown,
  skinId: string | undefined,
  hint: ItemHints | undefined,
  catalog: string | undefined,
): string | undefined =>
  safeTexture(own) ??
  safeTexture(skinId === undefined ? undefined : hint?.textures.get(skinId)) ??
  safeTexture(hint?.textures.get("")) ??
  safeTexture(catalog);
