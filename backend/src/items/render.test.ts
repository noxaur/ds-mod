import { Schema } from "effect";
import { expect, test } from "vite-plus/test";

import catalog from "./fixtures/catalog.json" with { type: "json" };
import upstream from "./fixtures/upstream-items.json" with { type: "json" };
import { entriesOf, makeRenderer } from "./render.ts";
import { decodeCatalog } from "./render/catalog.ts";
import type { ItemHints } from "./store.ts";

const items = Schema.decodeUnknownSync(Schema.Record(Schema.String, Schema.Record(Schema.String, Schema.Unknown)))(
  upstream,
);
const item = (entry: string): Record<string, unknown> => items[entry] ?? {};
const renderer = makeRenderer(catalog, new Map());
const draw = (entry: string) => {
  const drawn = renderer.render(item(entry));
  if (drawn === null) {
    throw new Error(`${entry} drew nothing`);
  }
  return drawn;
};
const plain = (line: readonly { readonly text: string }[]): string => line.map((span) => span.text).join("");
const lines = (entry: string): string[] => draw(entry).lore.map(plain);

test("entries are the distinct non-empty entries of the slots", () => {
  expect(
    entriesOf([{ entry: "scylla" }, { entry: "scylla" }, { entry: "" }, {}, null, "x", { entry: 4 }, { entry: "a" }]),
  ).toEqual(["scylla", "a"]);
  expect(entriesOf([])).toEqual([]);
});

test("odd data draws nothing or something, and never throws", () => {
  for (const odd of [
    null,
    undefined,
    4,
    "x",
    [],
    {},
    { material: "minecraft:air" },
    { material: "" },
    { material: 3 },
  ]) {
    expect(renderer.render(odd)).toBeNull();
  }
  expect(renderer.render({ ...item("scylla"), count: 0 })).toBeNull();
  expect(() => renderer.render({ ...item("scylla"), item_data: "nope", item_stats: [], abilities: 5 })).not.toThrow();
});

test("a count is kept between 1 and 99", () => {
  expect(renderer.render({ ...item("scylla"), count: 500 })?.count).toBe(99);
  expect(renderer.render({ ...item("scylla"), count: 3.7 })?.count).toBe(3);
});

test("the name has the reforge, the rarity color and the stars", () => {
  const { name, rarity } = draw("scylla");
  expect(rarity).toBe("MYTHIC");
  expect(plain(name)).toBe("Arcane Scylla ✦".replace("✦", "") + "✪✪✪✪✪➊");
  expect(name[0]?.color).toBe("#FF55FF");
});

test("a stat line shows the reforge, the books and the dungeon-scaled value", () => {
  expect(lines("scylla")[0]).toBe(" Damage: +585 (+35) (+731.25)");
  expect(lines("scylla")[3]).toBe(" Crit Damage: +220% (+275%)");
});

test("an ability header is built with the layout glyphs and the footer shows a recombobulated rarity", () => {
  const all = lines("scylla");
  expect(all.some((line) => line.startsWith("Wither Impact"))).toBe(true);
  expect(all.some((line) => line.startsWith("Six Heads"))).toBe(true);
  expect(all).toContain("Mana Cost: 200");
  expect(all).toContain("Cooldown: 2s");
  const footer = draw("scylla").lore.at(-1) ?? [];
  expect(footer.map((span) => span.text)).toContain("0");
  expect(footer.some((span) => span.obfuscated === true)).toBe(true);
  expect(plain(footer)).toContain(" MYTHIC ");
  expect(plain(footer)).toContain("DUNGEON SWORD");
});

test("a full set bonus names its pieces", () => {
  expect(lines("maxor_chestplate")).toContain("Full Set Bonus: Mage Lord");
  expect(lines("sorrow_helmet").some((line) => line.startsWith("Piece Bonus: Sorrow"))).toBe(true);
});

test("a skull whose item data says it is no skull is still a skull with a skin id's texture", () => {
  const seen = btoa(JSON.stringify({ textures: { SKIN: { url: "http://textures.minecraft.net/texture/abc123" } } }));
  const hints = new Map([["sorrow_helmet", { glint: null, textures: new Map([["superior_baby", seen]]) }]]);
  const drawn = makeRenderer(catalog, hints).render(item("sorrow_helmet"));
  expect(drawn?.material).toBe("minecraft:player_head");
  expect(drawn?.skullTexture).toBe(seen);
  expect(draw("sorrow_helmet").skullTexture).toBe(decodeCatalog(catalog).items.get("sorrow_helmet")?.skin);
});

test("a skull falls back to what players saw, then the catalog", () => {
  const seen = btoa(JSON.stringify({ textures: { SKIN: { url: "http://textures.minecraft.net/texture/abc123" } } }));
  const hints: ItemHints = { glint: null, textures: new Map([["", seen]]) };
  const without = { ...item("gold_necron_head") };
  delete without["item_skin"];
  const drawn = makeRenderer(catalog, new Map([["gold_necron_head", hints]])).render(without);
  expect(drawn?.skullTexture).toBe(seen);
});

test("a material that is no skull gets no texture", () => {
  expect(draw("scylla").skullTexture).toBeUndefined();
});

test("a dyed item has its color", () => {
  expect(draw("maxor_chestplate").color).toBe(1411510);
  expect(draw("scylla").color).toBeUndefined();
});

const hints = (glint: ItemHints["glint"]) => new Map([["scylla", { glint, textures: new Map() }]]);

test("glint follows the hints", () => {
  expect(makeRenderer(catalog, hints("always")).render(item("scylla"))?.glint).toBe(true);
  expect(makeRenderer(catalog, hints("never")).render(item("scylla"))?.glint).toBe(false);
  expect(makeRenderer(catalog, hints("mixed")).render(item("scylla"))?.glint).toBe(false);
  expect(draw("sorrow_helmet").glint).toBe(true);
});

test("a catalog is decoded once however many renderers it serves", () => {
  expect(decodeCatalog(catalog)).toBe(decodeCatalog(catalog));
  expect(decodeCatalog({})).not.toBe(decodeCatalog(catalog));
});

test("a catalog that is garbage still draws an item from its own data", () => {
  for (const bad of [null, 4, "x", [], { items: 3, reforges: "x", itemRules: [] }]) {
    expect(makeRenderer(bad, new Map()).render(item("scylla"))?.material).toBe("minecraft:iron_sword");
  }
});

test("a whole inventory draws fast once warm", () => {
  const all = Object.values(items);
  const run = (): number => {
    const start = performance.now();
    for (let i = 0; i < 100; i += 1) {
      for (const one of all) {
        renderer.render(one);
      }
    }
    return performance.now() - start;
  };
  run();
  expect(run() / (100 * all.length)).toBeLessThan(1);
});
