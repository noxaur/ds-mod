import { readFileSync } from "node:fs";
import { join } from "node:path";

import { Effect, Schema } from "effect";
import { afterEach, beforeEach, expect, test, vi } from "vite-plus/test";

import { makeApp } from "../app.ts";
import { memoryServices } from "../memory.ts";
import { CATALOG_FRESH_MS, CATALOG_RETRY_MS, FRESH_MS } from "./cache.ts";
import { Container, Profile } from "./model.ts";
import { memoryProfileStore, WriteRefused } from "./store.ts";
import { UpstreamDown } from "./upstream.ts";

const fixture = (name: string): any => JSON.parse(readFileSync(join(import.meta.dirname, "fixtures", name), "utf8"));
const player = fixture("player.json");
const catalog = fixture("catalog.json");
const OTHER_PROFILE: string = player.profiles[0].id;

type Answer = { status: number; body: unknown } | "down";

/** dragsim.co as the fixtures say it is, counting what it was asked, and failing on demand. */
function backend() {
  const asked: string[] = [];
  const state: { player: Answer; catalog: Answer } = {
    player: { status: 200, body: player },
    catalog: { status: 200, body: catalog },
  };
  const services = memoryServices({
    upstream: {
      get: (path) => {
        asked.push(path);
        const answer = path.startsWith("/data/catalog") ? state.catalog : state.player;
        return answer === "down" ? Effect.fail(new UpstreamDown()) : Effect.succeed(answer);
      },
    },
  });
  const app = makeApp(services, () => ({ userId: "ann", adminToken: undefined }));
  const get = async (path: string) => {
    const response = await app(new Request(`http://backend${path}`));
    const body: unknown = await response.json().catch(() => null);
    return { status: response.status, body };
  };
  /** A profile as the contract says it is, so an answer that drifts from it fails here. */
  const profile = async (path = "/v1/players/Testwyrm/profile") =>
    Schema.decodeUnknownSync(Profile)((await get(path)).body);
  const container = async (path: string) => Schema.decodeUnknownSync(Container)((await get(path)).body);
  /** The status and the `_tag` of a failure, which is how a client tells the 404s apart. */
  const failure = async (path: string) => {
    const { status, body } = await get(path);
    return { status, tag: Reflect.get(Object(body), "_tag") as unknown };
  };
  return {
    asked,
    state,
    get,
    profile,
    container,
    failure,
    playerCalls: () => asked.filter((path) => path.startsWith("/api/player")).length,
  };
}

beforeEach(() => {
  vi.useFakeTimers({ toFake: ["Date"] });
  vi.setSystemTime(new Date("2026-11-15T12:00:00Z"));
});
afterEach(() => vi.useRealTimers());

test("a profile is the player's, with levels worked out from the catalog", async () => {
  const { profile } = backend();
  const value = await profile();
  expect(value.player).toEqual({
    uuid: player.player.uuid,
    name: "Testwyrm",
    rank: {
      id: "MVP_PLUS",
      prefix: [
        { text: "[MVP", color: "#55FFFF" },
        { text: "+", color: "#FF5555" },
        { text: "]", color: "#55FFFF" },
      ],
    },
  });
  expect(value.stale).toBe(false);
  expect(value.fetchedAt).toBe("2026-11-15T12:00:00.000Z");
  expect(value.profileId).toBe(player.profile.id);
  expect(value.profiles).toEqual([
    { id: OTHER_PROFILE, name: [{ text: "Arcane", color: "#AA00AA" }], mode: "NORMAL", selected: false },
    { id: player.profile.id, name: [{ text: "Superior", color: "#AA00AA" }], mode: "IRONMAN", selected: true },
  ]);

  const skills = Object.fromEntries(value.skills.map((skill) => [skill.id, skill]));
  // 175 XP is exactly the cumulative XP of combat level 2, so level 2 has just begun.
  expect(skills["combat"]).toMatchObject({
    name: "Combat",
    level: 2,
    maxLevel: 100,
    xpIntoLevel: 0,
    xpForNextLevel: 200,
  });
  expect(skills["mining"]).toMatchObject({ level: 0, xpIntoLevel: 49, xpForNextLevel: 50 });
  expect(skills["catacombs"]).toMatchObject({ maxLevel: 50, level: 50, xpForNextLevel: null });
  // A skill the catalog has no table for uses the default one, and still shows.
  expect(skills["dragon_slaying"]).toMatchObject({ name: "Dragon Slaying", level: 1, maxLevel: 100 });
  expect(skills["taming"]).toMatchObject({ level: 100, xpForNextLevel: null });
});

test("slayers show the server's level, and an unknown one still shows", async () => {
  const { profile } = backend();
  expect((await profile()).slayers).toEqual([
    {
      id: "zombie",
      name: "Revenant Horror",
      level: 9,
      maxLevel: 9,
      totalXp: 17642500,
      xpIntoLevel: 0,
      xpForNextLevel: null,
    },
    {
      id: "enderman",
      name: "Voidgloom Seraph",
      level: 9,
      maxLevel: 9,
      totalXp: 60444500,
      xpIntoLevel: 0,
      xpForNextLevel: null,
    },
    { id: "spider", name: "Spider", level: 0, maxLevel: 9, totalXp: 15, xpIntoLevel: 0, xpForNextLevel: null },
  ]);
});

test("stats are formatted as the game shows them, and the top kills are the ten most", async () => {
  const { profile } = backend();
  const value = await profile();
  expect(value.stats.map((stat) => [stat.id, stat.value[0]?.text])).toEqual([
    ["coins", "35,124,030,742"],
    ["bits", "94,254"],
    ["motes", "0"],
    ["dragon_essence", "831,916"],
    ["accessory_power", "Sighted"],
  ]);
  const kills = value.topKills.map((kill) => kill.kills);
  expect(value.topKills).toHaveLength(10);
  expect(value.topKills[0]).toEqual({ id: "NEST_ZEALOT", name: "Nest Zealot", kills: 368742 });
  expect(kills).toEqual(kills.toSorted((a, b) => b - a));
  // A mob with no kills is not a top kill, and `_MOB` is the id's, not the name's.
  expect(value.topKills.map((kill) => kill.id)).not.toContain("VOIDGLOOM_SERAPH_MOB");
  expect(value.topKills.map((kill) => kill.name)).not.toContain("Revenant Horror Mob");
});

test("the containers are listed as the game lays them out", async () => {
  const { profile } = backend();
  const value = await profile();
  expect(value.containers.map((box) => [box.id, box.slots, box.columns])).toEqual([
    ["inventory", 36, 9],
    ["ender_chest", 27, 9],
    ["storage:0", 45, 9],
    ["storage:1", 45, 9],
    ["storage:2", 45, 9],
    ["wardrobe:0", 36, 9],
    ["accessories", 18, 9],
  ]);
  expect(value.containers.every((box) => !box.hidden)).toBe(true);
  // Armor is inventory keys 41 to 44, and the held item is the first hotbar slot. How each is drawn is the renderer's test.
  const { helmet, chestplate, leggings, boots, held } = value.equipment;
  expect([helmet, chestplate, leggings, boots, held].map((item) => item?.entry)).toEqual([
    "maxor_helmet",
    "maxor_chestplate",
    "maxor_leggings",
    "maxor_boots",
    "scylla",
  ]);
});

test("a container has one slot for each place, null where upstream has no item", async () => {
  const { profile, container } = backend();
  const { snapshot } = await profile();
  const inventory = await container(`/v1/snapshots/${snapshot}/containers/inventory`);
  expect(inventory).toMatchObject({ id: "inventory", title: [{ text: "Inventory" }], slots: 36, hidden: false });
  expect(inventory.items).toHaveLength(36);
  expect((await container(`/v1/snapshots/${snapshot}/containers/wardrobe:0`)).items).toHaveLength(36);
});

test("a hidden profile lists its containers with no items, and no equipment", async () => {
  const { profile, container, state } = backend();
  state.player = { status: 200, body: { ...player, profile: { ...player.profile, inventory_hidden: true } } };
  const value = await profile();
  expect(value.equipment).toEqual({ helmet: null, chestplate: null, leggings: null, boots: null, held: null });
  expect(value.containers.length).toBeGreaterThan(5);
  expect(value.containers.every((box) => box.hidden && box.slots === 0)).toBe(true);
  expect(await container(`/v1/snapshots/${value.snapshot}/containers/ender_chest`)).toMatchObject({
    hidden: true,
    items: [],
  });
});

test("a hidden player's items are kept nowhere", async () => {
  const kept: string[] = [];
  const store = memoryProfileStore();
  const services = memoryServices({
    profiles: {
      get: store.get,
      put: (key, value, ttl) => {
        kept.push(JSON.stringify(value));
        return store.put(key, value, ttl);
      },
    },
    upstream: {
      get: (path) =>
        Effect.succeed({
          status: 200,
          body: path.startsWith("/data/catalog")
            ? catalog
            : { ...player, profile: { ...player.profile, inventory_hidden: true } },
        }),
    },
  });
  const app = makeApp(services, () => ({ userId: "ann", adminToken: undefined }));
  expect((await app(new Request("http://backend/v1/players/Testwyrm/profile"))).status).toBe(200);
  expect(kept.some((value) => value.includes("Testwyrm"))).toBe(true);
  expect(kept.join()).not.toContain("scylla");
});

test("a copy the store refuses costs the lookup nothing, and the name is written once", async () => {
  // KV takes one write per key a second. This store refuses every write to a key it already holds.
  const store = memoryProfileStore();
  const written: string[] = [];
  const services = memoryServices({
    profiles: {
      get: store.get,
      put: (key, value, ttl) => {
        if (written.includes(key)) {
          return Effect.fail(new WriteRefused());
        }
        written.push(key);
        return store.put(key, value, ttl);
      },
    },
    upstream: {
      get: (path) => Effect.succeed({ status: 200, body: path.startsWith("/data/catalog") ? catalog : player }),
    },
  });
  const app = makeApp(services, () => ({ userId: "ann", adminToken: undefined }));
  // Another lookup of this profile wrote it a moment ago, so this one's copy is refused.
  written.push("profile:11111111-2222-4333-8444-555555555555:selected");
  expect((await app(new Request("http://backend/v1/players/Testwyrm/profile"))).status).toBe(200);
  // Picking the other profile at once fetches again, which must not write the name a second time.
  expect((await app(new Request(`http://backend/v1/players/Testwyrm/profile?profile=${OTHER_PROFILE}`))).status).toBe(
    200,
  );
  expect(written.filter((key) => key.startsWith("name:"))).toHaveLength(1);
});

test("asking again soon reaches dragsim.co once, whatever the name's case", async () => {
  const { get, playerCalls, asked } = backend();
  await get("/v1/players/Testwyrm/profile");
  await get("/v1/players/testwyrm/profile");
  await get("/v1/players/TESTWYRM/profile");
  expect(playerCalls()).toBe(1);
  expect(asked.filter((path) => path.startsWith("/data/catalog"))).toHaveLength(1);
  vi.setSystemTime(Date.now() + FRESH_MS + 1);
  await get("/v1/players/Testwyrm/profile");
  expect(playerCalls()).toBe(2);
});

test("each profile of a player has a copy of its own", async () => {
  const { get, playerCalls, asked } = backend();
  await get("/v1/players/Testwyrm/profile");
  await get(`/v1/players/Testwyrm/profile?profile=${OTHER_PROFILE}`);
  await get(`/v1/players/Testwyrm/profile?profile=${OTHER_PROFILE}`);
  expect(playerCalls()).toBe(2);
  expect(asked).toContain(`/api/player/Testwyrm?mode=main&profile=${OTHER_PROFILE}`);
});

test("the catalog is fetched again after an hour", async () => {
  const { get, asked } = backend();
  await get("/v1/players/Testwyrm/profile");
  vi.setSystemTime(Date.now() + CATALOG_FRESH_MS + 1);
  await get("/v1/players/Testwyrm/profile");
  expect(asked.filter((path) => path.startsWith("/data/catalog"))).toHaveLength(2);
});

test("a copy answers as stale when dragsim.co is down, however old", async () => {
  const { get, profile, state } = backend();
  await get("/v1/players/Testwyrm/profile");
  state.player = "down";
  vi.setSystemTime(Date.now() + 5 * 60 * 60 * 1000);
  const value = await profile();
  expect(value.stale).toBe(true);
  expect(value.fetchedAt).toBe("2026-11-15T12:00:00.000Z");
});

test("a 5xx, a failure and an answer that does not decode are all unavailable without a copy", async () => {
  for (const answer of ["down", { status: 502, body: {} }, { status: 200, body: { profile: 7 } }] as const) {
    const { failure, state } = backend();
    state.player = answer;
    expect(await failure("/v1/players/Testwyrm/profile")).toEqual({ status: 503, tag: "UpstreamUnavailable" });
  }
});

test("an answer that no longer decodes falls back on the last copy", async () => {
  const { get, profile, state } = backend();
  await get("/v1/players/Testwyrm/profile");
  state.player = { status: 200, body: { player: "changed" } };
  vi.setSystemTime(Date.now() + FRESH_MS + 1);
  expect((await profile()).stale).toBe(true);
});

test("a missing catalog with no copy is unavailable", async () => {
  const { failure, state } = backend();
  state.catalog = "down";
  expect((await failure("/v1/players/Testwyrm/profile")).status).toBe(503);
});

test("a name dragsim.co does not know is remembered for a minute", async () => {
  const { failure, state, playerCalls } = backend();
  state.player = { status: 404, body: { error: "No DragSim player found by that name.", status: 404 } };
  expect(await failure("/v1/players/Nobody/profile")).toEqual({ status: 404, tag: "PlayerNotFound" });
  expect((await failure("/v1/players/nobody/profile")).status).toBe(404);
  expect(playerCalls()).toBe(1);
});

test("a name that was known and is now missing stops reaching dragsim.co", async () => {
  const { get, failure, state, playerCalls } = backend();
  await get("/v1/players/Testwyrm/profile");
  expect(playerCalls()).toBe(1);
  state.player = { status: 404, body: { error: "No DragSim player found by that name.", status: 404 } };
  vi.setSystemTime(Date.now() + FRESH_MS + 1);
  expect((await failure("/v1/players/Testwyrm/profile")).status).toBe(404);
  expect((await failure("/v1/players/Testwyrm/profile")).status).toBe(404);
  expect((await failure("/v1/players/testwyrm/profile")).status).toBe(404);
  expect(playerCalls()).toBe(2);
});

test("a stale catalog is kept for a minute when dragsim.co is down, then tried again", async () => {
  const { get, asked, state } = backend();
  const catalogCalls = () => asked.filter((path) => path.startsWith("/data/catalog")).length;
  await get("/v1/players/Testwyrm/profile");
  state.catalog = "down";
  vi.setSystemTime(Date.now() + CATALOG_FRESH_MS + 1);
  expect((await get("/v1/players/Testwyrm/profile")).status).toBe(200);
  expect(catalogCalls()).toBe(2);
  for (let call = 0; call < 5; call++) {
    expect((await get("/v1/players/Testwyrm/profile")).status).toBe(200);
  }
  expect(catalogCalls()).toBe(2);
  vi.setSystemTime(Date.now() + CATALOG_RETRY_MS + 1);
  expect((await get("/v1/players/Testwyrm/profile")).status).toBe(200);
  expect(catalogCalls()).toBe(3);
  state.catalog = { status: 200, body: catalog };
  vi.setSystemTime(Date.now() + CATALOG_RETRY_MS + 1);
  await get("/v1/players/Testwyrm/profile");
  expect(catalogCalls()).toBe(4);
  await get("/v1/players/Testwyrm/profile");
  expect(catalogCalls()).toBe(4);
});

test("a profile the player does not have is not a missing player", async () => {
  const { failure, state } = backend();
  state.player = { status: 404, body: { error: "That profile does not belong to this player.", status: 404 } };
  expect(await failure(`/v1/players/Testwyrm/profile?profile=${OTHER_PROFILE}`)).toEqual({
    status: 404,
    tag: "ProfileNotFound",
  });
});

test("a name or a profile id that is not one is refused before anything is asked", async () => {
  const { get, asked } = backend();
  expect((await get("/v1/players/not%20a%20name!/profile")).status).toBe(400);
  expect((await get("/v1/players/Testwyrm/profile?profile=../x")).status).toBe(400);
  expect(asked).toEqual([]);
});

test("a snapshot that is gone, a container that is not there and a bad id are told apart", async () => {
  const { profile, failure } = backend();
  const { snapshot } = await profile();
  expect(await failure(`/v1/snapshots/${snapshot}/containers/nope`)).toEqual({ status: 404, tag: "ContainerNotFound" });
  expect(await failure(`/v1/snapshots/${crypto.randomUUID()}/containers/inventory`)).toEqual({
    status: 410,
    tag: "SnapshotExpired",
  });
  // An id that is not one this backend makes never reaches the store.
  expect((await failure("/v1/snapshots/profile:x:selected/containers/inventory")).tag).toBe("SnapshotExpired");
  expect((await failure("/v1/snapshots/catalog/containers/inventory")).tag).toBe("SnapshotExpired");
});

test("a profile answer is quick once cached", async () => {
  const { get } = backend();
  await get("/v1/players/Testwyrm/profile");
  const calls = 200;
  const start = performance.now();
  for (let call = 0; call < calls; call++) {
    await get("/v1/players/Testwyrm/profile");
  }
  // About 1.5 ms with a real player's profile. The bound only catches a cache that stopped working, such as a 2 MB
  // catalog parse per call.
  expect((performance.now() - start) / calls).toBeLessThan(25);
});
