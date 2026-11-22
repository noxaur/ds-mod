import { Effect, Schema } from "effect";
import { beforeEach, expect, test, vi } from "vite-plus/test";

import { profileStore } from "../../routes/_store.ts";
import { advance, testApp, testClock } from "../../test/harness.ts";
import type { Services } from "../app.ts";
import catalog from "./fixtures/catalog.json" with { type: "json" };
import player from "./fixtures/player.json" with { type: "json" };
import { CATALOG_FRESH_MS, CATALOG_RETRY_MS, FRESH_MS, MISSING_SECONDS } from "./cache.ts";
import { Container, Profile } from "./model.ts";
import { layoutsOf } from "./layout.ts";
import { unmappedPaths } from "./wire.ts";
import { WriteRefused } from "./store.ts";
import { UpstreamDown } from "./upstream.ts";

const OTHER_PROFILE = player.profiles[0]?.id ?? "";

type Answer = { status: number; body: unknown } | "down";

const ann = (overrides: Partial<Services>) => {
  const { call } = testApp({ clock, ...overrides });
  return { get: (path: string) => call("ann", "GET", path) };
};

function backend() {
  const asked: string[] = [];
  const state: { player: Answer; catalog: Answer } = {
    player: { status: 200, body: player },
    catalog: { status: 200, body: catalog },
  };
  const { get } = ann({
    upstream: {
      get: (path) => {
        asked.push(path);
        const answer = path.startsWith("/data/catalog") ? state.catalog : state.player;
        return answer === "down" ? Effect.fail(new UpstreamDown()) : Effect.succeed(answer);
      },
    },
  });
  const profile = async (path = "/v1/players/Testwyrm/profile") =>
    Schema.decodeUnknownSync(Profile)((await get(path)).value);
  const container = async (path: string) => Schema.decodeUnknownSync(Container)((await get(path)).value);
  const failure = async (path: string) => {
    const { status, value } = await get(path);
    const tag: unknown = Reflect.get(Object(value), "_tag");
    return { status, tag };
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

const START = "2026-11-15T12:00:00Z";
const clock = await testClock(START);
beforeEach(() => Effect.runPromise(clock.setTime(Date.parse(START))));

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
  // @keep 175 XP is exactly the cumulative XP of combat level 2, so level 2 has just begun.
  expect(skills["combat"]).toMatchObject({
    name: "Combat",
    level: 2,
    maxLevel: 100,
    xpIntoLevel: 0,
    xpForNextLevel: 200,
  });
  expect(skills["mining"]).toMatchObject({ level: 0, xpIntoLevel: 49, xpForNextLevel: 50 });
  expect(skills["catacombs"]).toMatchObject({ maxLevel: 50, level: 50, xpForNextLevel: null });
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
    ["haunted_candy_earned", "8,396"],
    ["draconic_experience", "370"],
  ]);
  const kills = value.topKills.map((kill) => kill.kills);
  expect(value.topKills).toHaveLength(10);
  expect(value.topKills[0]).toEqual({ id: "NEST_ZEALOT", name: "Nest Zealot", kills: 368742 });
  expect(kills).toEqual(kills.toSorted((a, b) => b - a));
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
    ["pets", 9, 9],
  ]);
  expect(value.containers.every((box) => !box.hidden)).toBe(true);
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
  expect(value.equippedWardrobeSlot).toBe(2);
  expect(value.containers.length).toBeGreaterThan(5);
  expect(value.containers.filter((box) => box.id !== "pets").every((box) => box.hidden && box.slots === 0)).toBe(true);
  expect(await container(`/v1/snapshots/${value.snapshot}/containers/ender_chest`)).toMatchObject({
    hidden: true,
    items: [],
  });
});

test("a hidden player's items are kept nowhere", async () => {
  const kept: string[] = [];
  const { get } = ann({
    profiles: {
      get: profileStore.get,
      put: (key, value, ttl) => {
        kept.push(JSON.stringify(value));
        return profileStore.put(key, value, ttl);
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
  expect((await get("/v1/players/Testwyrm/profile")).status).toBe(200);
  expect(kept.some((value) => value.includes("Testwyrm"))).toBe(true);
  expect(kept.join()).not.toContain("scylla");
});

test("a copy the store refuses costs the lookup nothing, and the name is written once", async () => {
  // @keep KV takes one write per key a second. This store refuses every write to a key it already holds.
  const written: string[] = [];
  const { get } = ann({
    profiles: {
      get: profileStore.get,
      put: (key, value, ttl) => {
        if (written.includes(key)) {
          return Effect.fail(new WriteRefused());
        }
        written.push(key);
        return profileStore.put(key, value, ttl);
      },
    },
    upstream: {
      get: (path) => Effect.succeed({ status: 200, body: path.startsWith("/data/catalog") ? catalog : player }),
    },
  });
  written.push("profile:11111111-2222-4333-8444-555555555555:selected");
  expect((await get("/v1/players/Testwyrm/profile")).status).toBe(200);
  expect((await get(`/v1/players/Testwyrm/profile?profile=${OTHER_PROFILE}`)).status).toBe(200);
  expect(written.filter((key) => key.startsWith("name:"))).toHaveLength(1);
});

test("asking again soon reaches dragsim.co once, whatever the name's case", async () => {
  const { get, playerCalls, asked } = backend();
  await get("/v1/players/Testwyrm/profile");
  await get("/v1/players/testwyrm/profile");
  await get("/v1/players/TESTWYRM/profile");
  expect(playerCalls()).toBe(1);
  expect(asked.filter((path) => path.startsWith("/data/catalog"))).toHaveLength(1);
  await advance(clock, FRESH_MS + 1);
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
  await advance(clock, CATALOG_FRESH_MS + 1);
  await get("/v1/players/Testwyrm/profile");
  expect(asked.filter((path) => path.startsWith("/data/catalog"))).toHaveLength(2);
});

test("a copy answers as stale when dragsim.co is down, however old", async () => {
  const { get, profile, state } = backend();
  await get("/v1/players/Testwyrm/profile");
  state.player = "down";
  await advance(clock, 5 * 60 * 60 * 1000);
  const value = await profile();
  expect(value.stale).toBe(true);
  expect(value.fetchedAt).toBe("2026-11-15T12:00:00.000Z");
});

test("a name outlives the first day, so a later copy still answers when dragsim.co is down", async () => {
  const state: { player: Answer } = { player: { status: 200, body: player } };
  const { get } = ann({
    upstream: {
      get: (path) => {
        const answer = path.startsWith("/data/catalog") ? { status: 200, body: catalog } : state.player;
        return answer === "down" ? Effect.fail(new UpstreamDown()) : Effect.succeed(answer);
      },
    },
  });
  const lookUp = () => get("/v1/players/Testwyrm/profile");
  const hour = 60 * 60 * 1000;
  const stale = async (fetchedAt: string) => {
    const response = await lookUp();
    expect(response.status).toBe(200);
    expect(Schema.decodeUnknownSync(Profile)(response.value)).toMatchObject({ stale: true, fetchedAt });
  };
  expect((await lookUp()).status).toBe(200);
  await advance(clock, 23 * hour);
  expect((await lookUp()).status).toBe(200);
  state.player = "down";
  await advance(clock, 2 * hour);
  await stale("2026-11-16T11:00:00.000Z");
  state.player = { status: 200, body: player };
  await advance(clock, 2 * hour);
  expect((await lookUp()).status).toBe(200);
  await advance(clock, 30 * 60 * 1000);
  expect((await lookUp()).status).toBe(200);
  state.player = "down";
  await advance(clock, 24 * hour - 15 * 60 * 1000);
  await stale("2026-11-16T15:30:00.000Z");
});

test("a 5xx, a failure, a 404 without the server's words and an answer that does not decode are all unavailable without a copy", async () => {
  for (const answer of [
    "down",
    { status: 502, body: {} },
    { status: 404, body: "<html>Not Found</html>" },
    { status: 200, body: { profile: 7 } },
  ] as const) {
    const { failure, state } = backend();
    state.player = answer;
    expect(await failure("/v1/players/Testwyrm/profile")).toEqual({ status: 503, tag: "UpstreamUnavailable" });
  }
});

test("an answer that no longer decodes falls back on the last copy", async () => {
  const { get, profile, state } = backend();
  await get("/v1/players/Testwyrm/profile");
  state.player = { status: 200, body: { player: "changed" } };
  await advance(clock, FRESH_MS + 1);
  expect((await profile()).stale).toBe(true);
});

test("a missing catalog with no copy is unavailable", async () => {
  const { failure, state } = backend();
  state.catalog = "down";
  expect((await failure("/v1/players/Testwyrm/profile")).status).toBe(503);
});

test("a 404 without the server's words for a missing player falls back on the last copy and is not remembered", async () => {
  const { get, profile, state, playerCalls } = backend();
  await get("/v1/players/Testwyrm/profile");
  state.player = { status: 404, body: { error: "Not Found" } };
  await advance(clock, FRESH_MS + 1);
  expect((await profile()).stale).toBe(true);
  expect((await profile()).stale).toBe(true);
  expect(playerCalls()).toBe(3);
});

test("a name dragsim.co does not know is remembered for a minute", async () => {
  const { failure, state, playerCalls } = backend();
  state.player = { status: 404, body: { error: "No DragSim player found by that name.", status: 404 } };
  expect(await failure("/v1/players/Nobody/profile")).toEqual({ status: 404, tag: "PlayerNotFound" });
  expect((await failure("/v1/players/nobody/profile")).status).toBe(404);
  expect(playerCalls()).toBe(1);
  await advance(clock, MISSING_SECONDS * 1000);
  expect((await failure("/v1/players/Nobody/profile")).status).toBe(404);
  expect(playerCalls()).toBe(2);
});

test("a name that was known and is now missing stops reaching dragsim.co", async () => {
  const { get, failure, state, playerCalls } = backend();
  await get("/v1/players/Testwyrm/profile");
  expect(playerCalls()).toBe(1);
  state.player = { status: 404, body: { error: "No DragSim player found by that name.", status: 404 } };
  await advance(clock, FRESH_MS + 1);
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
  await advance(clock, CATALOG_FRESH_MS + 1);
  expect((await get("/v1/players/Testwyrm/profile")).status).toBe(200);
  expect(catalogCalls()).toBe(2);
  for (let call = 0; call < 5; call++) {
    expect((await get("/v1/players/Testwyrm/profile")).status).toBe(200);
  }
  expect(catalogCalls()).toBe(2);
  await advance(clock, CATALOG_RETRY_MS + 1);
  expect((await get("/v1/players/Testwyrm/profile")).status).toBe(200);
  expect(catalogCalls()).toBe(3);
  state.catalog = { status: 200, body: catalog };
  await advance(clock, CATALOG_RETRY_MS + 1);
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
  // @keep About 5 ms with a real player's profile, 18 pets included. The bound only catches a cache that stopped working.
  expect((performance.now() - start) / calls).toBeLessThan(25);
});

test("every field of a real player's answer is mapped, at every depth", () => {
  expect(unmappedPaths(player)).toEqual([]);
});

test("a profile names itself, with or without the list of profiles", async () => {
  const { profile, state } = backend();
  expect(await profile()).toMatchObject({
    profileName: [{ text: "Superior", color: "#AA00AA" }],
    profileMode: "IRONMAN",
  });
  state.player = { status: 200, body: { ...player, profiles: undefined } };
  const alone = await profile(`/v1/players/Testwyrm/profile?profile=${player.profile.id}`);
  expect(alone).toMatchObject({ profileName: [{ text: "Superior", color: "#AA00AA" }], profileMode: "IRONMAN" });
});

test("a map or a list where a value was expected is named, since it would be dropped", () => {
  const scylla = player.profile.inventory["0"];
  const odd = {
    profile: {
      coins: { nested: 1 },
      inventory: { 0: { ...scylla, extra: { wither_scrolls: { mask: 7, new_scroll: { charges: 2 } } } } },
    },
  };
  expect(unmappedPaths(odd).toSorted()).toEqual(["profile.coins", "profile.inventory.*.extra.wither_scrolls.*"]);
});

test("one odd entry in a number map loses only itself, and a map where a list belongs is named", async () => {
  const { profile, state } = backend();
  const odd = {
    ...player.profile,
    collections: { ...player.profile.collections, new_collection: {} },
    dungeons: { ...player.profile.dungeons, essence: { ...player.profile.dungeons.essence, odd: { amount: 1 } } },
    forge: { jobs: [] },
  };
  state.player = { status: 200, body: { ...player, profile: odd } };
  const value = await profile();
  expect(value.collections.length).toBe(Object.keys(player.profile.collections).length);
  expect(value.dungeons?.essence.length).toBe(Object.keys(player.profile.dungeons.essence).length);
  expect(unmappedPaths({ profile: odd }).toSorted()).toEqual([
    "profile.collections.*",
    "profile.dungeons.essence.*",
    "profile.forge",
  ]);
});

test("a field added deep inside a section or an item is named by its path", () => {
  const added = {
    ...player,
    brand_new: 1,
    profile: {
      ...player.profile,
      dungeons: { ...player.profile.dungeons, new_section: 42 },
      pets: [{ ...player.profile.pets[0], new_field: 42 }],
      inventory: { 0: { ...player.profile.inventory["0"], extra: { new_upgrade: {} } } },
      storage: { 0: { 3: { entry: "x", item_modifiers: { new_book: 1 } } } },
    },
  };
  expect(unmappedPaths(added).toSorted()).toEqual([
    "brand_new",
    "profile.dungeons.new_section",
    "profile.inventory.*.extra.new_upgrade",
    "profile.pets[].new_field",
    "profile.storage.*.*.item_modifiers.new_book",
  ]);
});

test("a key the backend does not map is logged once per lookup, naming it", async () => {
  const { get, state } = backend();
  state.player = {
    status: 200,
    body: { ...player, brand_new: 1, profile: { ...player.profile, new_section: {} } },
  };
  const logged = [vi.spyOn(console, "warn"), vi.spyOn(console, "log"), vi.spyOn(console, "info")].map((spy) =>
    spy.mockImplementation(() => undefined),
  );
  await get("/v1/players/Testwyrm/profile");
  await get("/v1/players/Testwyrm/profile");
  const lines = logged
    .flatMap((spy) => spy.mock.calls.map((call) => call.join(" ")))
    .filter((line) => line.includes("map"));
  vi.restoreAllMocks();
  expect(lines).toHaveLength(1);
  expect(lines[0]).toContain("brand_new");
  expect(lines[0]).toContain("profile.new_section");
});

test("the profile's own numbers, save time and worn wardrobe slot come through", async () => {
  const { profile } = backend();
  const value = await profile();
  expect(value.savedAt).toBe("2026-11-15T17:15:18.524Z");
  expect(value.equippedWardrobeSlot).toBe(2);
  expect(value.stats.find((stat) => stat.id === "draconic_experience")?.name).toBe("Draconic Experience");
  expect(value.playerStats.base).toEqual([
    { id: "intelligence", name: "Intelligence", color: "#55FFFF", value: 300 },
    { id: "health", name: "Health", color: "#FF5555", value: 600 },
    { id: "mystery", name: "Mystery", color: "#AAAAAA", value: 1 },
  ]);
  expect(value.playerStats.additive.map((stat) => stat.value)).toEqual([15]);
  expect(value.playerStats.multiplicative).toEqual([]);
  expect(value.playerStats.increaseMax.map((stat) => stat.id)).toEqual(["health"]);
});

test("the bestiary has every mob, most kills first, and topKills stays", async () => {
  const { profile } = backend();
  const value = await profile();
  expect(value.bestiary).toHaveLength(Object.keys(player.profile.bestiary).length);
  expect(value.bestiary[0]).toEqual({ id: "NEST_ZEALOT", name: "Nest Zealot", kills: 368742 });
  expect(value.bestiary.at(-1)?.kills).toBe(0);
  expect(value.bestiary.map((mob) => mob.id)).toContain("NEST_ENDERMITE");
  expect(value.topKills).toHaveLength(10);
});

test("collections, rng meters, dungeons, the heart of the dragon and the forge are passed on, named", async () => {
  const { profile } = backend();
  const value = await profile();
  expect(value.collections).toEqual([
    { id: "obsidian", name: "Obsidian", amount: 2747774 },
    { id: "mystery_ore", name: "Mystery Ore", amount: 3 },
  ]);
  expect(value.rngMeters).toEqual([
    { id: "revenant", name: "Revenant", progress: 2495 },
    { id: "mine_node", name: "Mine Node", progress: 962 },
  ]);
  expect(value.dungeons).toMatchObject({
    selectedClass: { id: "TANK", name: "Tank" },
    secrets: 12,
    zombieKills: 31,
    skeletorKills: 15,
    essence: [{ id: "wither_essence", name: "Wither Essence", amount: 8608 }],
    floors: [
      { id: "F7", name: "Floor 7", completions: 467, bestScore: 353 },
      { id: "M1", name: "Master Mode 1", completions: 2, bestScore: null },
    ],
  });
  expect(value.dungeons?.classes.find((one) => one.id === "TANK")).toMatchObject({
    name: "Tank",
    totalXp: 75730.5,
    maxLevel: 50,
    level: 18,
  });
  expect(value.hotd).toMatchObject({
    tier: 10,
    core: 10,
    ender: 11932047,
    void: 43761698,
    resets: 20,
    ability: { id: "void_infusion", name: "Void Infusion" },
    nodes: [
      { id: "MINING_FORTUNE", name: "Mining Fortune", level: 50 },
      { id: "DRAGONS_FAVOR", name: "Dragons Favor", level: 1 },
    ],
  });
  expect(value.forge).toEqual([
    {
      slot: 1,
      recipe: "obsidian",
      name: "Obsidian",
      startedAt: 1792679895946,
      durationMs: 1800000,
      finishesAt: 1792679895946 + 1800000,
      stage: "RUNNING",
    },
    {
      slot: 0,
      recipe: "refined_end_stone",
      name: "Refined End Stone",
      startedAt: 1792679893796,
      durationMs: 1800000,
      finishesAt: 1792679893796 + 1800000,
      stage: "READY",
    },
  ]);
});

test("a profile with none of the new sections still answers", async () => {
  const { profile, state } = backend();
  const bare = Object.fromEntries(
    Object.entries(player.profile).filter(
      ([key]) =>
        !["stats", "collections", "rng_meters", "dungeons", "hotd", "pets", "forge", "updated_at"].includes(key),
    ),
  );
  state.player = { status: 200, body: { ...player, profile: { ...bare, wardrobe: undefined } } };
  const value = await profile();
  expect(value).toMatchObject({
    savedAt: null,
    equippedWardrobeSlot: null,
    dungeons: null,
    hotd: null,
    pets: [],
    forge: [],
  });
  expect(value.containers.map((box) => box.id)).not.toContain("pets");
});

test("a pet is named from the catalog, with its held item, skin and a grid drawing of it", async () => {
  const { profile, container } = backend();
  const value = await profile();
  const [golden, cat] = value.pets;
  expect(golden).toMatchObject({
    type: "GOLDEN_DRAGON",
    name: "Golden Dragon",
    rarity: "LEGENDARY",
    totalXp: 1798563928,
    xpCurve: 2,
    candiesUsed: 3,
    active: true,
    heldItem: { id: "pet_item_lucky_clover", name: "Lucky Clover" },
    skinOverride: { id: "pet_skin_golden_dragon_ancient", name: "Ancient Golden Dragon Skin" },
    level: 200,
    maxLevel: 200,
    xpIntoLevel: 1_778_563_928,
    xpForNextLevel: null,
    category: "Combat",
    perks: [{ id: "strength", name: "Strength", color: "#FF5555", value: 0.5 }],
  });
  expect(golden?.item).toMatchObject({
    material: "minecraft:player_head",
    rarity: "LEGENDARY",
    name: [
      { text: "[Lvl 200] ", color: "#AAAAAA" },
      { text: "Golden Dragon", color: "#FFAA00" },
    ],
    skullTexture: catalog.pets.golden_dragon.skin.value,
  });
  expect(golden?.item.lore.map((line) => line.map((span) => span.text).join(""))).toContain("Held Item: Lucky Clover");
  expect(cat).toMatchObject({
    name: "Black Cat",
    heldItem: null,
    skinOverride: null,
    level: 100,
    maxLevel: 100,
    xpIntoLevel: 0,
    xpForNextLevel: null,
  });
  const { snapshot } = value;
  const pets = await container(`/v1/snapshots/${snapshot}/containers/pets`);
  expect(pets).toMatchObject({ id: "pets", slots: 9, hidden: false });
  expect(pets.items.slice(0, 2)).toEqual([golden?.item, cat?.item]);
  expect(pets.items.slice(2).every((slot) => slot === null)).toBe(true);
});

test("a hidden profile still shows its pets, and no item", async () => {
  const { profile, container, state } = backend();
  state.player = { status: 200, body: { ...player, profile: { ...player.profile, inventory_hidden: true } } };
  const value = await profile();
  expect(value.pets).toHaveLength(2);
  expect(value.containers.find((box) => box.id === "pets")).toMatchObject({ hidden: false, slots: 9 });
  expect((await container(`/v1/snapshots/${value.snapshot}/containers/pets`)).items[0]?.name[1]?.text).toBe(
    "Golden Dragon",
  );
});

test("a pet's level is worked out from its XP as dragsim.co does", async () => {
  const { profile, state } = backend();
  const [golden, cat] = player.profile.pets;
  const pets = [
    { ...golden, totalXp: 650 + 3 * 204_075 + 1_000 },
    { ...golden, totalXp: 0 },
    { ...cat, rarity: "RARE", totalXp: 450 + 625 + 10 },
  ];
  state.player = { status: 200, body: { ...player, profile: { ...player.profile, pets } } };
  state.catalog = {
    status: 200,
    body: { ...catalog, pets: { ...catalog.pets, black_cat: { id: "BLACK_CAT", display: "Black Cat", levels: true } } },
  };
  expect((await profile()).pets).toMatchObject([
    { level: 105, maxLevel: 200, xpIntoLevel: 1_000, xpForNextLevel: 204_075 },
    { level: 101, maxLevel: 200, xpIntoLevel: 0, xpForNextLevel: 650 },
    { level: 3, maxLevel: 100, xpIntoLevel: 10, xpForNextLevel: 802 },
  ]);
});

test("an item with no place is never dropped, and its container is listed only then", async () => {
  const { profile, container, state } = backend();
  expect((await profile()).containers.map((box) => box.id).filter((id) => id.endsWith(":other"))).toEqual([]);
  const sword = player.profile.inventory["0"];
  const stray = (name: string) => ({ ...sword, entry: name });
  state.player = {
    status: 200,
    body: {
      ...player,
      profile: {
        ...player.profile,
        inventory: {
          ...player.profile.inventory,
          "40": stray("a40"),
          "36": stray("a36"),
          "45": stray("a45"),
          cursor: stray("cursor"),
        },
        ender_chest: { ...player.profile.ender_chest, "-1": stray("ender"), odd: stray("ender2") },
        storage: {
          ...player.profile.storage,
          extra: { "0": stray("page") },
          "0": { ...player.profile.storage["0"], x: stray("pagex") },
        },
        accessory_bag: { ...player.profile.accessory_bag, "01": stray("bag") },
        wardrobe: {
          ...player.profile.wardrobe,
          slots: {
            ...player.profile.wardrobe.slots,
            "0": { ...player.profile.wardrobe.slots["0"], offhand: stray("off") },
          },
        },
      },
    },
  };
  await advance(clock, FRESH_MS + 1);
  const value = await profile();
  const others = value.containers.filter((box) => box.id.endsWith(":other"));
  expect(others.map((box) => [box.id, box.slots])).toEqual([
    ["inventory:other", 9],
    ["ender_chest:other", 9],
    ["storage:other", 9],
    ["wardrobe:other", 9],
    ["accessories:other", 9],
  ]);
  const entries = async (id: string) =>
    (await container(`/v1/snapshots/${value.snapshot}/containers/${id}`)).items.flatMap((item) =>
      item === null ? [] : [item.entry],
    );
  expect(await entries("inventory:other")).toEqual(["a36", "a40", "a45", "cursor"]);
  expect(await entries("ender_chest:other")).toEqual(["ender", "ender2"]);
  expect((await entries("storage:other")).toSorted((a, b) => String(a).localeCompare(String(b)))).toEqual([
    "page",
    "pagex",
  ]);
  expect(await entries("wardrobe:other")).toEqual(["off"]);
  expect(await entries("accessories:other")).toEqual(["bag"]);
  expect((await entries("inventory")).slice(-3)).toEqual(["scylla", "crypt_cache_item", "glacial_scythe"]);
  expect(value.equipment.helmet?.entry).toBe("maxor_helmet");
});

test("a slot key past a container's grid stays beside it and does not grow the grid", () => {
  const stray = { entry: "stray" };
  const talisman = { entry: "talisman" };
  const byId = Object.fromEntries(
    layoutsOf({
      ender_chest: { "100000": stray },
      storage: { "0": { "100000": stray } },
      accessory_bag: { "40": talisman, "100000": stray },
    }).map((layout) => [layout.id, layout]),
  );
  expect(byId["ender_chest"]?.slots).toBe(27);
  expect(byId["storage:0"]?.slots).toBe(45);
  expect(byId["accessories"]?.slots).toBe(45);
  expect(byId["accessories"]?.cells[40]).toEqual(talisman);
  expect(byId["ender_chest:other"]?.cells).toContainEqual(stray);
  expect(byId["storage:other"]?.cells).toContainEqual(stray);
  expect(byId["accessories:other"]?.cells).toContainEqual(stray);
});

test("one skill, slayer, or bestiary value that is not a finite number is skipped", async () => {
  const { profile, state } = backend();
  state.player = {
    status: 200,
    body: {
      ...player,
      profile: {
        ...player.profile,
        skills: { ...player.profile.skills, mining: null, farming: "nope", enchanting: Number.POSITIVE_INFINITY },
        slayer: { ...player.profile.slayer, spider_xp: null, enderman_level: "nine" },
        bestiary: { ...player.profile.bestiary, NEST_ZEALOT: { kills: "nope" }, BROKEN: "nope" },
      },
    },
  };
  const value = await profile();
  const skills = Object.fromEntries(value.skills.map((skill) => [skill.id, skill]));
  expect(skills["combat"]).toMatchObject({ level: 2, totalXp: 175 });
  expect(skills["mining"]).toBeUndefined();
  expect(skills["farming"]).toBeUndefined();
  expect(skills["enchanting"]).toBeUndefined();
  expect(skills["taming"]).toMatchObject({ level: 100 });
  expect(value.slayers.find((slayer) => slayer.id === "zombie")).toMatchObject({ level: 9, totalXp: 17642500 });
  expect(value.slayers.find((slayer) => slayer.id === "spider")).toBeUndefined();
  expect(value.slayers.find((slayer) => slayer.id === "enderman")).toMatchObject({ level: 0, totalXp: 60444500 });
  expect(value.bestiary.find((mob) => mob.id === "NEST_ZEALOT")).toBeUndefined();
  expect(value.bestiary.find((mob) => mob.id === "BROKEN")).toBeUndefined();
  expect(value.bestiary.find((mob) => mob.id === "CRYPT_GHOUL")).toMatchObject({ kills: 74385 });
});
