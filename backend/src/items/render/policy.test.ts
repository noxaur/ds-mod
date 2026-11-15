import { expect, test } from "vite-plus/test";

import type { ItemHints } from "../store.ts";
import { glints, safeTexture, skullTexture } from "./policy.ts";
import { parseColor } from "./read.ts";

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
    expect(glints(material, false, "mixed")).toBe(true);
    expect(glints(material, false, "never")).toBe(false);
  }
});

test("a glowing catalog entry on a self-glinting material still glints against a never hint", () => {
  expect(glints("minecraft:nether_star", true, "never")).toBe(true);
});

test("a texture on Mojang's texture server is accepted, over http or https", () => {
  const plain = skin("78bd1889bf8a635ec71fc05b1ed548763778e3d371926d39a62c29d350c48b49");
  expect(safeTexture(plain)).toBe(plain);
  const secure = texture("https://textures.minecraft.net/texture/ABCDEF0123");
  expect(safeTexture(secure)).toBe(secure);
});

test("a texture is dropped unless it decodes and names only Mojang's texture server", () => {
  const rejected: unknown[] = [
    undefined,
    null,
    42,
    "",
    "not base64 at all",
    btoa("not json"),
    btoa(JSON.stringify({ textures: {} })),
    btoa(JSON.stringify({ nothing: true })),
    btoa(JSON.stringify({ textures: { SKIN: {} } })),
    btoa(JSON.stringify({ textures: { SKIN: { url: 5 } } })),
    texture("http://evil.example/texture/abc123"),
    texture("http://textures.minecraft.net.evil.example/texture/abc123"),
    texture("http://textures.minecraft.net@evil.example/texture/abc123"),
    texture("http://evil.example/?http://textures.minecraft.net/texture/abc123"),
    texture("http://textures.minecraft.net/texture/abc123/../../x"),
    texture("http://textures.minecraft.net/texture/"),
    texture("http://textures.minecraft.net/texture/xyz"),
    texture("http://textures.minecraft.net/texture/abc123?x=1"),
    texture("ftp://textures.minecraft.net/texture/abc123"),
    texture("file:///etc/passwd"),
    texture("//textures.minecraft.net/texture/abc123"),
    texture("http://textures.minecraft.net/texture/abc123\nhttp://evil.example"),
    "a".repeat(9000),
  ];
  for (const value of rejected) {
    expect(safeTexture(value)).toBeUndefined();
  }
});

test("one bad URL among good ones drops the whole texture", () => {
  const mixed = btoa(
    JSON.stringify({
      textures: {
        SKIN: { url: "http://textures.minecraft.net/texture/abc123" },
        CAPE: { url: "http://evil.example/cape" },
      },
    }),
  );
  expect(safeTexture(mixed)).toBeUndefined();
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

test("a color is read from #RRGGBB, from the catalog's r,g,b, or from a number", () => {
  expect(parseColor("#1589B6")).toBe(0x1589b6);
  expect(parseColor("21,137,182")).toBe(0x1589b6);
  expect(parseColor(" 255, 255 ,255 ")).toBe(0xffffff);
  expect(parseColor(0x123456)).toBe(0x123456);
  for (const bad of ["", "#12345", "#GGGGGG", "256,0,0", "1,2", "1,2,3,4", "a,b,c", -1, 0x1000000, 1.5, null, {}]) {
    expect(parseColor(bad)).toBeUndefined();
  }
});
