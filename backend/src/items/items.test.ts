import { Effect, Schema } from "effect";
import { expect, test } from "vite-plus/test";

import { sighting, testApp } from "../../test/harness.ts";
import { ItemAppearances } from "./model.ts";
import { MAX_PLAYER_ITEM_ROWS, MAX_SAMPLES } from "./items.ts";
import { type ItemStore, MIN_PLAYERS, memoryItemStore } from "./store.ts";

const texture = (url: string) => btoa(JSON.stringify({ textures: { SKIN: { url } } }));
const mojang = texture("http://textures.minecraft.net/texture/abc123");

const backend = (items: ItemStore["Service"] = memoryItemStore()) => {
  const { call } = testApp({ items });
  const send = (who: string, sightings: unknown[]) =>
    call(who, "POST", "/v1/contrib/items", { modVersion: "1.0.0", items: sightings });
  const admin = (query = "") => call("agent", "GET", `/v1/admin/items${query}`);
  const hints = (...entries: string[]) => Effect.runPromise(items.hints(entries));
  const entries = async (query = "") => Schema.decodeUnknownSync(ItemAppearances)((await admin(query)).value);
  return { send, admin, entries, hints };
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
  const { send, hints, entries } = backend();
  for (let batch = 0; batch < 20; batch++) {
    await send(
      "ann",
      Array.from({ length: 50 }, () => sighting("scylla", { skullTexture: mojang })),
    );
  }
  const hint = (await hints("scylla")).get("scylla");
  expect(hint?.glint).toBeNull();
  expect(hint?.textures.size).toBe(0);
  expect((await entries()).items).toMatchObject([{ entry: "scylla", players: 1, glintPlayers: 1, samples: [] }]);
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
  const { send, hints, entries } = backend();
  const evil = texture("http://evil.example/texture/abc");
  const bad = [
    evil,
    texture("http://textures.minecraft.net.evil.example/texture/abc"),
    texture("http://evil.example/@textures.minecraft.net/texture/abc"),
    texture("http://textures.minecraft.net:8080/texture/abc123"),
    texture("http://user:pass@textures.minecraft.net/texture/abc123"),
    texture("http://textures.minecraft.net/other/abc123"),
    texture("http://textures.minecraft.net/texture/abc123/more"),
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
  expect((await entries()).items).toEqual([]);
  await send("ann", [sighting("head", { skullTexture: evil }), sighting("good")]);
  expect((await entries()).items).toMatchObject([{ entry: "good" }]);
});

const sendMany = async (send: ReturnType<typeof backend>["send"], who: string, count: number) => {
  // @keep 50 a call is the API's limit.
  for (let from = 0; from < count; from += 50) {
    await send(
      who,
      Array.from({ length: Math.min(50, count - from) }, (_, index) => sighting(`${who}-item${String(from + index)}`)),
    );
  }
};

test("one player cannot take more than their share of the table", async () => {
  const { send, entries } = backend();
  await sendMany(send, "ann", MAX_PLAYER_ITEM_ROWS + 100);
  await send("bob", [sighting("bobs")]);
  const all: string[] = [];
  let after = "";
  do {
    const page = await entries(`?limit=200&after=${after}`);
    all.push(...page.items.map((item) => item.entry));
    after = page.next ?? "";
  } while (after !== "");
  expect(all).toHaveLength(MAX_PLAYER_ITEM_ROWS + 1);
  expect(all).toContain("bobs");
});

test("the admin view is one entry with its samples, or a page of entries without", async () => {
  const { send, entries, admin } = backend();
  await send("ann", [sighting("a"), sighting("b"), sighting("c")]);
  await send("bob", [sighting("a")]);
  expect(await entries("?entry=a")).toMatchObject({
    items: [{ entry: "a", players: 2, samples: [{}, {}] }],
    next: null,
  });
  expect((await entries("?entry=missing")).items).toEqual([]);
  const first = await entries("?limit=2");
  expect(first).toMatchObject({
    items: [
      { entry: "a", samples: [] },
      { entry: "b", samples: [] },
    ],
    next: "b",
  });
  expect(await entries("?limit=2&after=b")).toMatchObject({ items: [{ entry: "c" }], next: null });
  expect((await admin("?limit=201")).status).toBe(400);
});

test("an item keeps a few samples, one per player", async () => {
  const { send, entries } = backend();
  for (let player = 0; player < MAX_SAMPLES + 3; player++) {
    await send(`player${String(player)}`, [sighting("scylla", { lore: [[{ text: String(player) }]] })]);
  }
  await send("player0", [sighting("scylla", { lore: [[{ text: "again" }]] })]);
  expect((await entries("?entry=scylla")).items).toMatchObject([
    { players: MAX_SAMPLES + 3, samples: expect.toSatisfy((samples: unknown[]) => samples.length === MAX_SAMPLES) },
  ]);
});

test("the agent's view never holds a user id", async () => {
  const { send, admin } = backend();
  const ann = "user-ann-4f1c";
  const bob = "user-bob-9b2e";
  await send(ann, [sighting("scylla", { skullTexture: mojang })]);
  await send(bob, [sighting("scylla", { skullTexture: mojang })]);
  const { status, value } = await admin("?entry=scylla");
  const text = JSON.stringify(value);
  expect(status).toBe(200);
  expect(text).toContain("scylla");
  expect(text).not.toContain(ann);
  expect(text).not.toContain(bob);
  expect(value).toMatchObject({
    items: [{ players: 2, glintPlayers: 2, textures: [{ skinId: "", players: 2 }] }],
  });
});
