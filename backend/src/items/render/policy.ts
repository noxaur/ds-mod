import { safeTexture } from "../../upstream/texture.ts";
import type { ItemHints } from "../store.ts";

/**
 * Items the game draws with a glint whatever the server sends, so the mod, which sets the glint explicitly, has to be
 * told they glint.
 */
const SELF_GLINTING = new Set([
  "minecraft:enchanted_golden_apple",
  "minecraft:nether_star",
  "minecraft:experience_bottle",
  "minecraft:enchanted_book",
  "minecraft:end_crystal",
  "minecraft:written_book",
]);

/**
 * Whether the item glints. The API sends no enchantments, so this is the best knowledge there is, in this order:
 * the catalog says it glows, a material the game glints by itself does unless enough players saw a copy without, and
 * otherwise what enough players agree on. `mixed` means the glint comes from enchantments the API does not send,
 * which is not something to draw on every copy, and null means too few players saw it to say.
 */
export const glints = (material: string, catalogGlowing: boolean, hint: ItemHints["glint"] | undefined): boolean => {
  if (catalogGlowing) {
    return true;
  }
  if (SELF_GLINTING.has(material)) {
    return hint !== "never" && hint !== "mixed";
  }
  return hint === "always";
};

/** The skull's texture: the item's own, what players saw for its skin, what they saw for it plain, then the catalog's. */
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
