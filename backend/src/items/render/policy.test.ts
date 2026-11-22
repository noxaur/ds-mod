import { expect, test } from "vite-plus/test";

import type { ItemHints } from "../store.ts";
import { glints, skullTexture } from "./policy.ts";

const texture = (url: string): string => btoa(JSON.stringify({ textures: { SKIN: { url } } }));
const skin = (hex: string): string => texture(`http://textures.minecraft.net/texture/${hex}`);

const hints = (textures: Record<string, string>): ItemHints => ({
  glint: null,
  textures: new Map(Object.entries(textures)),
});

test("the catalog's glowing item glints whatever players saw", () => {
  for (const hint of ["always", "never", "mixed", null, undefined] as const) {
    expect(glints("minecraft:netherite_sword", true, hint)).toBe(true);
  }
});

test("an item the catalog does not say glows glints only when players always saw it glint", () => {
  expect(glints("minecraft:netherite_sword", false, "always")).toBe(true);
  expect(glints("minecraft:netherite_sword", false, "never")).toBe(false);
  expect(glints("minecraft:netherite_sword", false, "mixed")).toBe(false);
});

test("with too few players the catalog decides", () => {
  expect(glints("minecraft:netherite_sword", false, null)).toBe(false);
  expect(glints("minecraft:netherite_sword", false, undefined)).toBe(false);
});

test("a material the game glints by itself glints unless players saw it without", () => {
  for (const material of [
    "minecraft:enchanted_golden_apple",
    "minecraft:nether_star",
    "minecraft:experience_bottle",
    "minecraft:enchanted_book",
    "minecraft:end_crystal",
    "minecraft:written_book",
  ]) {
    expect(glints(material, false, null)).toBe(true);
    expect(glints(material, false, undefined)).toBe(true);
    expect(glints(material, false, "always")).toBe(true);
    expect(glints(material, false, "mixed")).toBe(false);
    expect(glints(material, false, "never")).toBe(false);
  }
});

test("a glowing catalog entry on a self-glinting material still glints against a never hint", () => {
  expect(glints("minecraft:nether_star", true, "never")).toBe(true);
});

test("the skull's texture is the item's own first", () => {
  const own = skin("aa");
  expect(skullTexture(own, "superior_baby", hints({ superior_baby: skin("bb"), "": skin("cc") }), skin("dd"))).toBe(
    own,
  );
});

test("then what players saw for the item's skin", () => {
  const seen = skin("bb");
  expect(skullTexture(undefined, "superior_baby", hints({ superior_baby: seen, "": skin("cc") }), skin("dd"))).toBe(
    seen,
  );
});

test("then what players saw for the item plain, then the catalog's", () => {
  const plain = skin("cc");
  expect(skullTexture(undefined, "superior_baby", hints({ "": plain }), skin("dd"))).toBe(plain);
  expect(skullTexture(undefined, undefined, hints({ "": plain }), skin("dd"))).toBe(plain);
  expect(skullTexture(undefined, "superior_baby", hints({}), skin("dd"))).toBe(skin("dd"));
  expect(skullTexture(undefined, undefined, undefined, skin("dd"))).toBe(skin("dd"));
});

test("a skin id players saw nothing for does not take another skin's texture", () => {
  expect(skullTexture(undefined, "other", hints({ superior_baby: skin("bb") }), undefined)).toBeUndefined();
});

test("a texture that fails the check falls through to the next", () => {
  const bad = texture("http://evil.example/texture/aa");
  expect(skullTexture(bad, "s", hints({ s: bad, "": skin("cc") }), skin("dd"))).toBe(skin("cc"));
  expect(skullTexture(bad, "s", hints({ s: bad, "": bad }), bad)).toBeUndefined();
});
