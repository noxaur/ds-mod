import { Effect, Schema } from "effect";
import { expect, test } from "vite-plus/test";

import { advance, sighting, spec, testApp, testClock } from "../test/harness.ts";
import { MaxBody } from "./api.ts";
import games from "./items/fixtures/game-items.json" with { type: "json" };
import { ItemSighting, MAX_DATA_CHARS, MAX_LINE_SPANS, MAX_SPAN_TEXT } from "./items/model.ts";
import config from "../void.config.ts";

const clock = await testClock("2026-11-22T12:00:00Z");
const { call } = testApp({
  clock,
  upstream: {
    get: () => Effect.succeed({ status: 404, body: { error: "No DragSim player found by that name.", status: 404 } }),
  },
});
const items = (who: string, sightings: unknown[]) =>
  call(who, "POST", "/v1/contrib/items", { modVersion: "1.0.0", items: sightings });
const unknowns = (who: string, sample: string, count = 100) =>
  call(who, "POST", "/v1/contrib/unknowns", {
    modVersion: "1.0.0",
    sightings: Array.from({ length: count }, (_, index) => ({
      kind: "k".repeat(64),
      signature: String(index).padEnd(256, "s"),
      sample,
    })),
  });
const padded = (length: number) => ({ ...spec, brief: { ...spec.brief, name: "x".repeat(length) } });
const offers = (who: string) => call(who, "GET", "/v1/contrib/requests");
const limitOf = (name: string) => config.cloudflare?.ratelimits?.find((rate) => rate.name === name)?.simple.limit ?? 0;
const RATES = { player: limitOf("RATE_PLAYER"), profile: limitOf("RATE_PROFILE"), items: limitOf("RATE_ITEMS") };
const nextWindow = () => advance(clock, 60 * 1000);

const largest = (index: number) => {
  const line = [{ text: "x".repeat(189) }];
  const item = sighting(`entry${String(index)}`, { lore: Array.from({ length: 80 }, () => line) });
  expect(new TextEncoder().encode(JSON.stringify(item)).length).toBeLessThanOrEqual(16_384);
  return item;
};

test("a JSON endpoint takes 64 KiB unless it says otherwise", async () => {
  expect(Number(MaxBody.defaultValue())).toBe(64 * 1024);
  expect((await call("agent", "POST", "/v1/admin/requests", padded(60_000))).status).toBe(200);
  expect((await call("agent", "POST", "/v1/admin/requests", padded(70_000))).status).toBe(413);
});

test("the mod's largest batch of items fits, and a body over 1 MiB does not", async () => {
  expect(
    (
      await items(
        "ann",
        Array.from({ length: 50 }, (_, index) => largest(index)),
      )
    ).status,
  ).toBe(204);
  const over = Array.from({ length: 50 }, (_, index) => ({ ...largest(index), data: { pad: "x".repeat(10_000) } }));
  expect((await items("bob", over)).status).toBe(413);
});

test("the most unknowns the contract allows fit, even in three-byte characters, and a body over 2 MiB does not", async () => {
  expect((await unknowns("ann", "€".repeat(4096))).status).toBe(204);
  expect((await unknowns("bob", "\u0000".repeat(4096))).status, "each escapes to six bytes").toBe(413);
});

test("every item the fixtures hold fits a sighting's bounds", () => {
  const decode = Schema.decodeUnknownSync(ItemSighting);
  for (const game of games) {
    const lines = [game.name, ...game.lore].map(({ spans }) => spans);
    expect(() =>
      decode({
        entry: "entry",
        material: game.id,
        name: lines[0],
        lore: lines.slice(1),
        glint: false,
        data: game.data,
      }),
    ).not.toThrow();
  }
});

test("a sighting with a span, a line or data past its bound is refused", async () => {
  let player = 0;
  const status = async (extra: Partial<ItemSighting>) =>
    (await items(`cat${String(player++)}`, [sighting("scylla", extra)])).status;
  expect(await status({ name: [{ text: "x".repeat(MAX_SPAN_TEXT) }] })).toBe(204);
  expect(await status({ name: [{ text: "x".repeat(MAX_SPAN_TEXT + 1) }] })).toBe(400);
  expect(await status({ lore: [Array.from({ length: MAX_LINE_SPANS }, () => ({ text: "x" }))] })).toBe(204);
  expect(await status({ lore: [Array.from({ length: MAX_LINE_SPANS + 1 }, () => ({ text: "x" }))] })).toBe(400);
  expect(await status({ data: { ["k".repeat(64)]: 1 } })).toBe(204);
  expect(await status({ data: { ["k".repeat(65)]: 1 } })).toBe(400);
  expect(await status({ data: { pad: "x".repeat(MAX_DATA_CHARS - 10) } })).toBe(204);
  expect(await status({ data: { pad: "x".repeat(MAX_DATA_CHARS) } })).toBe(400);
});

test("a player who calls too often is answered 429 until the next minute, and nobody else is", async () => {
  await nextWindow();
  for (let index = 0; index < RATES.player; index++) {
    expect((await offers("dan")).status).toBe(200);
  }
  expect(await offers("dan")).toMatchObject({ status: 429, value: { _tag: "TooManyRequests" } });
  expect((await offers("eve")).status).toBe(200);
  await nextWindow();
  expect((await offers("dan")).status).toBe(200);
});

test("profile and items each have a tighter rate of their own", async () => {
  await nextWindow();
  const profile = () => call("fay", "GET", "/v1/players/Nobody/profile");
  for (let index = 0; index < RATES.profile; index++) {
    expect((await profile()).status).toBe(404);
  }
  expect((await profile()).status).toBe(429);
  expect((await call("fay", "GET", "/v1/contrib/requests")).status, "the player's own rate is not spent").toBe(200);

  for (let index = 0; index < RATES.items; index++) {
    expect((await items("gus", [sighting("scylla")])).status).toBe(204);
  }
  expect((await items("gus", [sighting("scylla")])).status).toBe(429);
});
