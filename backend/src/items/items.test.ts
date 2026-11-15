import { Effect } from "effect";
import { expect, test } from "vite-plus/test";

import { makeApp } from "../app.ts";
import { memoryServices } from "../memory.ts";
import { MAX_ITEM_ROWS, MAX_SAMPLES } from "./items.ts";
import { type ItemStore, MIN_PLAYERS, memoryItemStore } from "./store.ts";

const texture = (url: string) => btoa(JSON.stringify({ textures: { SKIN: { url } } }));
const mojang = texture("http://textures.minecraft.net/texture/abc123");

const sighting = (entry: string, extra: Record<string, unknown> = {}) => ({
  entry,
  material: "minecraft:iron_sword",
  name: [{ text: "Scylla", color: "#FFAA00" }],
  lore: [],
  glint: true,
  data: {},
  ...extra,
});

/** A backend whose callers are whoever the cookie names, so a test is any number of players. */
const backend = (items: ItemStore["Service"] = memoryItemStore()) => {
  const app = makeApp(memoryServices({ items }), (request) => ({
    userId: request.headers.get("cookie"),
    adminToken: "secret",
  }));
  const send = (who: string, sightings: unknown[]) =>
    app(
      new Request("http://backend/v1/contrib/items", {
        method: "POST",
        headers: { "content-type": "application/json", cookie: who },
        body: JSON.stringify({ modVersion: "1.0.0", items: sightings }),
      }),
    );
  const admin = async () => {
    const response = await app(
      new Request("http://backend/v1/admin/items", { headers: { authorization: "Bearer secret" } }),
    );
    return { status: response.status, text: await response.text() };
  };
  const hints = (...entries: string[]) => Effect.runPromise(items.hints(entries));
  return { send, admin, hints };
};

test("nothing is agreed on until enough different players saw it", async () => {
  const { send, hints } = backend();
  expect((await send("ann", [sighting("scylla")])).status).toBe(204);
  expect((await hints("scylla")).get("scylla")?.glint).toBeNull();
  expect(MIN_PLAYERS).toBe(2);
  await send("bob", [sighting("scylla")]);
  expect((await hints("scylla")).get("scylla")?.glint).toBe("always");
  expect((await hints("never-seen")).has("never-seen")).toBe(false);
});

test("one player sending a thousand times counts once", async () => {
  const { send, hints, admin } = backend();
  for (let batch = 0; batch < 20; batch++) {
    await send(
      "ann",
      Array.from({ length: 50 }, () => sighting("scylla", { skullTexture: mojang })),
    );
  }
  const hint = (await hints("scylla")).get("scylla");
  expect(hint?.glint).toBeNull();
  expect(hint?.textures.size).toBe(0);
  expect(JSON.parse((await admin()).text)).toMatchObject([{ entry: "scylla", players: 1, glintPlayers: 1 }]);
});

test("glint is always, never or mixed by what the players saw", async () => {
  const { send, hints } = backend();
  for (const [who, entry, glint] of [
    ["ann", "always", true],
    ["bob", "always", true],
    ["ann", "never", false],
    ["bob", "never", false],
    ["ann", "mixed", true],
    ["bob", "mixed", false],
    ["ann", "both", true],
    ["bob", "both", true],
  ] as const) {
    await send(who, [sighting(entry, { glint })]);
  }
  // Bob saw a second copy of "both" with no glint, so not every player saw it glint every time.
  await send("bob", [sighting("both", { glint: false })]);
  const found = await hints("always", "never", "mixed", "both");
  expect([...found].map(([entry, hint]) => [entry, hint.glint])).toEqual([
    ["always", "always"],
    ["never", "never"],
    ["mixed", "mixed"],
    ["both", "mixed"],
  ]);
});

test("a texture counts when two players saw it on the same skin", async () => {
  const { send, hints } = backend();
  const other = texture("https://textures.minecraft.net/texture/zzz");
  await send("ann", [
    sighting("head", { skullTexture: mojang }),
    sighting("head", { skinId: "gold", skullTexture: other }),
  ]);
  await send("bob", [
    sighting("head", { skullTexture: mojang }),
    sighting("head", { skinId: "gold", skullTexture: mojang }),
  ]);
  await send("cat", [sighting("head", { skinId: "gold", skullTexture: mojang })]);
  expect((await hints("head")).get("head")?.textures).toEqual(
    new Map([
      ["", mojang],
      ["gold", mojang],
    ]),
  );
});

test("a texture from anywhere but textures.minecraft.net refuses the sighting", async () => {
  const { send, hints, admin } = backend();
  const bad = [
    texture("http://evil.example/texture/abc"),
    texture("http://textures.minecraft.net.evil.example/texture/abc"),
    texture("http://evil.example/@textures.minecraft.net/texture/abc"),
    texture("not a url"),
    btoa(JSON.stringify({ textures: {} })),
    btoa("not json"),
    "!!! not base64",
  ];
  for (const who of ["ann", "bob"]) {
    for (const skullTexture of bad) {
      expect((await send(who, [sighting("head", { skullTexture })])).status).toBe(204);
    }
  }
  expect([...(await hints("head"))]).toEqual([]);
  expect(JSON.parse((await admin()).text)).toEqual([]);
  await send("ann", [sighting("head", { skullTexture: bad[0] }), sighting("good")]);
  expect(JSON.parse((await admin()).text)).toMatchObject([{ entry: "good" }]);
});

const row = (entry: string) => ({ entry, skinId: "", texture: null, glint: true, sample: null });

test("the rows stop at the cap, and an item already seen is still counted", async () => {
  const store = memoryItemStore();
  const { hints } = backend(store);
  await Effect.runPromise(
    store.record("ann", "1", [{ entry: "first", skinId: "", texture: null, glint: true, sample: null }], {
      maxRows: 2,
      maxSamples: 1,
    }),
  );
  const limits = { maxRows: 2, maxSamples: 1 };
  await Effect.runPromise(store.record("bob", "1", [row("second"), row("third")], limits));
  await Effect.runPromise(store.record("ann", "1", [row("first")], limits));
  expect([...(await hints("first", "second", "third"))].map(([entry]) => entry)).toEqual(["first", "second"]);
});

test("a client cannot fill the table through the API", async () => {
  const { send, admin } = backend();
  // 50 a call is the API's limit, so the cap is reached in many calls from many players.
  const calls = Math.ceil((MAX_ITEM_ROWS + 100) / 50);
  for (let call = 0; call < calls; call++) {
    await send(
      "ann",
      Array.from({ length: 50 }, (_, index) => sighting(`item${String(call * 50 + index)}`)),
    );
  }
  expect(JSON.parse((await admin()).text)).toHaveLength(MAX_ITEM_ROWS);
}, 60_000);

test("an item keeps a few samples, one per player", async () => {
  const { send, admin } = backend();
  for (let player = 0; player < MAX_SAMPLES + 3; player++) {
    await send(`player${String(player)}`, [sighting("scylla", { lore: [[{ text: String(player) }]] })]);
  }
  await send("player0", [sighting("scylla", { lore: [[{ text: "again" }]] })]);
  expect(JSON.parse((await admin()).text)).toMatchObject([
    { players: MAX_SAMPLES + 3, samples: expect.toSatisfy((samples: unknown[]) => samples.length === MAX_SAMPLES) },
  ]);
});

test("the agent's view never holds a user id", async () => {
  const { send, admin } = backend();
  const ann = "user-ann-4f1c";
  const bob = "user-bob-9b2e";
  await send(ann, [sighting("scylla", { skullTexture: mojang })]);
  await send(bob, [sighting("scylla", { skullTexture: mojang })]);
  const { status, text } = await admin();
  expect(status).toBe(200);
  expect(text).toContain("scylla");
  expect(text).not.toContain(ann);
  expect(text).not.toContain(bob);
  expect(JSON.parse(text)).toMatchObject([{ players: 2, glintPlayers: 2, textures: [{ skinId: "", players: 2 }] }]);
});
