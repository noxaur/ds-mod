import { Effect, Option, Schema } from "effect";

import { numberEntries } from "../items/render/read.ts";
import { decodeCatalog, entriesOf, makeRenderer } from "../items/render.ts";
import { ItemStore } from "../items/store.ts";
import { parseLegacy } from "../items/legacy.ts";
import { catalog, lookup } from "./cache.ts";
import { equipmentCells, layoutsOf, PETS_ID } from "./layout.ts";
import type { Layout } from "./layout.ts";
import { ContainerNotFound, ProfileId, SnapshotExpired } from "./model.ts";
import { rankPrefix, titleCase } from "./names.ts";
import { petOf } from "./pets.ts";
import { skillProgress, slayerProgress } from "./progress.ts";
import * as section from "./sections.ts";
import { ProfileStore } from "./store.ts";
import { Sections } from "./wire.ts";
import type { WireProfile } from "./wire.ts";

/** How long a client may keep asking for containers of one profile before it must fetch the profile again. */
export const SNAPSHOT_SECONDS = 10 * 60;
const TOP_KILLS = 10;

/** What a snapshot keeps of a profile: its containers, or only their pages when the player hid them. */
const Snapshot = Schema.Struct({ hidden: Schema.Boolean, sections: Sections });
const decodeSnapshot = Schema.decodeUnknownOption(Snapshot);

const snapshotKey = (id: string) => `snapshot:${id}`;

// A snapshot id comes from the client and is a KV key, so only the shape this backend makes reaches the store.
const isSnapshotId = Schema.is(ProfileId);

const sectionsOf = (profile: WireProfile): Sections => ({
  inventory: profile.inventory,
  ender_chest: profile.ender_chest,
  storage: profile.storage,
  wardrobe: profile.wardrobe,
  accessory_bag: profile.accessory_bag,
  pets: profile.pets,
});

/** Draws the items of one answer, which may hold nulls where a slot is empty. */
const draw = Effect.fn(function* (items: readonly unknown[]) {
  const present = items.filter((item) => item !== null && item !== undefined);
  const hints = yield* (yield* ItemStore).hints(entriesOf(present));
  const renderer = makeRenderer((yield* catalog).raw, hints);
  return items.map((item) => (item === null || item === undefined ? null : renderer.render(item)));
});

/** The player hides their items, and a pet is not one: the pets stay as the profile lists them. */
const summaryOf = (layout: Layout, hiddenItems: boolean) => {
  const hidden = hiddenItems && layout.id !== PETS_ID;
  return { id: layout.id, title: layout.title, columns: layout.columns, slots: hidden ? 0 : layout.slots, hidden };
};

const COINS = "#FFAA00";
const BITS = "#55FFFF";
const MOTES = "#FF55FF";
const DRAGON_ESSENCE = "#AA00AA";
const HAUNTED_CANDY = "#FFAA00";
const DRACONIC_EXPERIENCE = "#FF5555";
const ACCESSORY_POWER = "#55FF55";

const count = (value: number) => Math.floor(value).toLocaleString("en-US");

const stat = (id: string, name: string, color: string, text: string | null) =>
  text === null ? [] : [{ id, name, value: [{ text, color }] }];
const number = (value: number | null | undefined) => (value === undefined || value === null ? null : count(value));

const statsOf = (profile: WireProfile) => [
  ...stat("coins", "Coins", COINS, number(profile.coins)),
  ...stat("bits", "Bits", BITS, number(profile.bits)),
  ...stat("motes", "Motes", MOTES, number(profile.motes)),
  ...stat("dragon_essence", "Dragon Essence", DRAGON_ESSENCE, number(profile.dragon_essence)),
  ...stat(
    "accessory_power",
    "Accessory Power",
    ACCESSORY_POWER,
    profile.accessory_power === undefined || profile.accessory_power === null
      ? null
      : titleCase(profile.accessory_power),
  ),
  ...stat("haunted_candy_earned", "Haunted Candy Earned", HAUNTED_CANDY, number(profile.haunted_candy_earned)),
  ...stat("draconic_experience", "Draconic Experience", DRACONIC_EXPERIENCE, number(profile.draconic_experience)),
];

/**
 * A player's profile for the overview. The answer comes from the backend's copy of dragsim.co's while it is fresh,
 * and a snapshot of the containers is kept for the client's next requests.
 */
export const profile = Effect.fn(function* (name: string, profileId: string | undefined) {
  const { fetchedAt, stale, found } = yield* lookup(name, profileId);
  const { progression, raw } = yield* catalog;
  const { player, profile: current } = found;
  const items = decodeCatalog(raw);
  const bestiary = section.bestiary(current.bestiary);
  const equipped = current.wardrobe?.equipped;
  const hidden = current.inventory_hidden === true;
  const sections = sectionsOf(current);

  const id = crypto.randomUUID();
  // A snapshot's key is new each time, so a refusal is the store failing, which no client can fix.
  yield* Effect.orDie((yield* ProfileStore).put(snapshotKey(id), { hidden, sections }, SNAPSHOT_SECONDS));

  const worn = equipmentCells(current.inventory);
  const [helmet = null, chestplate = null, leggings = null, boots = null, held = null] = yield* draw([
    worn.helmet,
    worn.chestplate,
    worn.leggings,
    worn.boots,
    worn.held,
  ]);

  return {
    snapshot: id,
    fetchedAt: new Date(fetchedAt).toISOString(),
    stale,
    player: {
      uuid: player.uuid,
      name: player.player,
      rank: { id: player.rank ?? "DEFAULT", prefix: rankPrefix(player.rank ?? "DEFAULT") },
    },
    profiles: (found.profiles ?? []).map((other) => ({
      id: other.id,
      name: parseLegacy(other.name),
      mode: other.mode,
      selected: other.selected === true,
    })),
    profileId: current.id,
    profileName: parseLegacy(current.profile_name),
    profileMode: current.profile_mode,
    skills: Object.entries(numberEntries(current.skills)).map(([skill, xp]) => skillProgress(progression, skill, xp)),
    slayers: slayerProgress(numberEntries(current.slayer)),
    stats: statsOf(current),
    topKills: bestiary.filter(({ kills }) => kills > 0).slice(0, TOP_KILLS),
    savedAt:
      current.updated_at === undefined || current.updated_at === null
        ? null
        : new Date(current.updated_at).toISOString(),
    equippedWardrobeSlot: equipped === undefined || equipped === null || !Number.isInteger(equipped) ? null : equipped,
    playerStats: section.playerStats(items, current.stats),
    bestiary,
    collections: section.collections(items, current.collections),
    rngMeters: section.rngMeters(current.rng_meters),
    dungeons: section.dungeons(progression, current.dungeons),
    hotd: section.hotd(current.hotd),
    pets: (current.pets ?? []).map((pet) => petOf(items, pet)),
    forge: section.forge(items, current.forge),
    equipment: { helmet, chestplate, leggings, boots, held },
    containers: layoutsOf(sections).map((layout) => summaryOf(layout, hidden)),
  };
});

/** One container of the profile a snapshot was taken of. */
export const container = Effect.fn(function* (snapshot: string, containerId: string) {
  const stored = isSnapshotId(snapshot) ? yield* (yield* ProfileStore).get(snapshotKey(snapshot)) : null;
  const kept = decodeSnapshot(stored);
  // A snapshot of a shape this backend no longer writes is as good as gone: the client fetches the profile again.
  if (Option.isNone(kept)) {
    return yield* new SnapshotExpired();
  }
  const { hidden, sections } = kept.value;
  const layout = layoutsOf(sections).find((candidate) => candidate.id === containerId);
  if (layout === undefined) {
    return yield* new ContainerNotFound();
  }
  if (layout.id === PETS_ID) {
    const items = decodeCatalog((yield* catalog).raw);
    return {
      ...summaryOf(layout, hidden),
      items: layout.cells.map((pet) => (pet === null ? null : petOf(items, pet).item)),
    };
  }
  return { ...summaryOf(layout, hidden), items: hidden ? [] : yield* draw(layout.cells) };
});
