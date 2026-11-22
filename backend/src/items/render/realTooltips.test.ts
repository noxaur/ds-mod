import { Schema } from "effect";
import { expect, test } from "vite-plus/test";

import catalog from "../fixtures/catalog.json" with { type: "json" };
import games from "../fixtures/game-items.json" with { type: "json" };
import { makeRenderer } from "../render.ts";
import { toUpstream } from "./fromGame.testing.ts";
import { record } from "../../upstream/read.ts";

const Span = Schema.Record(Schema.String, Schema.Unknown);
type Span = typeof Span.Type;
const Real = Schema.Struct({
  id: Schema.String,
  count: Schema.Number,
  name: Schema.Struct({ spans: Schema.Array(Span) }),
  lore: Schema.Array(Schema.Struct({ spans: Schema.Array(Span) })),
  data: Schema.Record(Schema.String, Schema.Unknown),
});
type Real = typeof Real.Type;

const real = Schema.decodeUnknownSync(Schema.Array(Real))(games);
const renderer = makeRenderer(catalog, new Map());
const norm = (line: readonly Span[]): string =>
  JSON.stringify(
    line.map((span) => Object.fromEntries(Object.entries(span).toSorted(([a], [b]) => a.localeCompare(b)))),
  );
const same = (a: readonly Span[], b: readonly Span[]): boolean => norm(a) === norm(b);

const idOf = (item: Real): string => String(record(record(item.data)["item_data"])["item_id"]);

const EXACT = [
  "golden_eye",
  "dragon_scale",
  "protector_fragment",
  "arcane_dragon_chestplate",
  "arcane_fragment",
  "protector_dragon_boots",
  "strong_fragment",
  "zombie_talisman",
  "old_fragment",
  "aspect_of_the_void",
  "golden_gun",
  "endstone_rose",
  "obsidian",
  "golden_hot_potato_book",
  "edible_mace",
  "void_canister",
  "ender_stone",
  "promising_pickaxe",
  "enchanted_end_stone",
  "infinieye",
];

test("every real item's name is rebuilt span for span", () => {
  for (const item of real) {
    const drawn = renderer.render(toUpstream(item));
    expect(drawn, idOf(item)).not.toBeNull();
    expect(same(drawn?.name ?? [], item.name.spans), idOf(item)).toBe(true);
  }
});

test("these real tooltips are rebuilt line for line", () => {
  const want = new Map(real.map((item) => [idOf(item), item]));
  for (const id of EXACT) {
    const item = want.get(id);
    if (item === undefined) {
      throw new Error(`${id} is not in the fixtures`);
    }
    const drawn = renderer.render(toUpstream(item));
    const got = drawn === null ? [] : drawn.lore;
    const lines = item.lore.map((line) => line.spans);
    expect(got.length, id).toBe(lines.length);
    lines.forEach((line, index) => {
      expect(same(got[index] ?? [], line), `${id} line ${index}`).toBe(true);
    });
  }
});

test("the exact rate does not fall", () => {
  const exact = real.filter((item) => {
    const drawn = renderer.render(toUpstream(item));
    const lines = item.lore.map((line) => line.spans);
    return (
      drawn !== null && drawn.lore.length === lines.length && lines.every((line, i) => same(drawn.lore[i] ?? [], line))
    );
  });
  expect(exact.length).toBeGreaterThanOrEqual(20);
});
