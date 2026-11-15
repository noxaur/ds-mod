import { Context, Effect } from "effect";

import type { ItemAppearance, ItemSighting } from "./model.ts";

/**
 * What the players who agree on an item saw, and so what the renderer may trust. Each needs enough different players,
 * because one client can send anything. `glint` is null when too few players saw the item.
 */
export interface ItemHints {
  readonly glint: "always" | "never" | "mixed" | null;
  /** Skull textures by `skinId`, `""` for the item's own, each on `textures.minecraft.net`. */
  readonly textures: ReadonlyMap<string, string>;
}

/** What one player saw of one item in one sighting, cut from an `ItemSighting` by `items.ts`. */
export interface Observation {
  readonly entry: string;
  /** The item's `skinId`, `""` for none. */
  readonly skinId: string;
  /** The skull texture, already known to be on `textures.minecraft.net`. */
  readonly texture: string | null;
  readonly glint: boolean;
  /** Kept as the agent's example, or null when it is too big to keep. */
  readonly sample: ItemSighting | null;
}

export interface ItemLimits {
  /** At most this many (entry, player) rows, and as many (entry, player, skin) rows. */
  readonly maxRows: number;
  /** At most this many samples for one entry, one per player. */
  readonly maxSamples: number;
}

/** How many different players saw an entry. A player who saw both a glint and none counts in `glintPlayers` only. */
export interface EntryTally {
  readonly entry: string;
  readonly players: number;
  /** Players who saw it glint at least once. */
  readonly glintPlayers: number;
  /** Players who saw it glint every time. */
  readonly alwaysGlintPlayers: number;
}

/** How many different players saw this texture on this skin of an entry. */
export interface TextureTally {
  readonly entry: string;
  readonly skinId: string;
  readonly texture: string;
  readonly players: number;
}

/** Players who must agree before the renderer is told. */
export const MIN_PLAYERS = 2;

/** The one place the threshold is applied, so the in-memory store and D1 cannot disagree on it. */
export const hintsFrom = (
  entries: readonly string[],
  tallies: readonly EntryTally[],
  textures: readonly TextureTally[],
): ReadonlyMap<string, ItemHints> => {
  const wanted = new Set(entries);
  const hints = new Map<string, ItemHints>();
  for (const tally of tallies) {
    if (!wanted.has(tally.entry)) {
      continue;
    }
    const agreed = new Map<string, TextureTally>();
    for (const seen of textures) {
      const best = agreed.get(seen.skinId);
      // Two textures for one skin cannot both be right: the one more players saw wins, and the first of equals.
      if (
        seen.entry === tally.entry &&
        seen.players >= MIN_PLAYERS &&
        (best === undefined ||
          seen.players > best.players ||
          (seen.players === best.players && seen.texture < best.texture))
      ) {
        agreed.set(seen.skinId, seen);
      }
    }
    let glint: ItemHints["glint"] = null;
    if (tally.players >= MIN_PLAYERS) {
      glint = tally.alwaysGlintPlayers === tally.players ? "always" : tally.glintPlayers === 0 ? "never" : "mixed";
    }
    hints.set(tally.entry, { glint, textures: new Map([...agreed].map(([skin, seen]) => [skin, seen.texture])) });
  }
  return hints;
};

/** The sightings items have. A rule about them lives in `items.ts`, and this only reads and writes. */
export class ItemStore extends Context.Service<
  ItemStore,
  {
    /**
     * Remembers what one player saw, once per player however often they send it. A row for a new entry is kept only
     * while fewer than `limits.maxRows` are, and an entry whose row was refused keeps no texture and no sample.
     */
    readonly record: (
      userId: string,
      modVersion: string,
      observations: readonly Observation[],
      limits: ItemLimits,
    ) => Effect.Effect<void>;
    /** Everything players saw, for an agent. It holds no user id. */
    readonly appearances: Effect.Effect<readonly ItemAppearance[]>;
    /** What enough players agree on, for the entries asked about. An entry nobody saw is left out. */
    readonly hints: (entries: readonly string[]) => Effect.Effect<ReadonlyMap<string, ItemHints>>;
  }
>()("dsmod/items/ItemStore") {}

/** Builds the agent's view from the tallies and samples any store keeps. */
export const appearancesFrom = (
  tallies: readonly EntryTally[],
  textures: readonly TextureTally[],
  samples: ReadonlyMap<string, readonly ItemSighting[]>,
): readonly ItemAppearance[] =>
  tallies.map(({ entry, players, glintPlayers }) => ({
    entry,
    players,
    glintPlayers,
    textures: textures
      .filter((seen) => seen.entry === entry)
      .map(({ skinId, texture, players: seen }) => ({ skinId, texture, players: seen })),
    samples: samples.get(entry) ?? [],
  }));

/** A store that forgets everything when the process ends, for the tests. */
export const memoryItemStore = (): ItemStore["Service"] => {
  interface Seen {
    readonly entry: string;
    glint: boolean;
    plain: boolean;
  }
  const seen = new Map<string, Seen>();
  const textures = new Map<string, TextureTally & { readonly userId: string }>();
  const samples = new Map<string, Map<string, ItemSighting>>();

  const tallies = (): EntryTally[] => {
    const byEntry = new Map<string, { players: number; glintPlayers: number; alwaysGlintPlayers: number }>();
    for (const row of seen.values()) {
      const tally = byEntry.get(row.entry) ?? { players: 0, glintPlayers: 0, alwaysGlintPlayers: 0 };
      tally.players += 1;
      tally.glintPlayers += row.glint ? 1 : 0;
      tally.alwaysGlintPlayers += row.glint && !row.plain ? 1 : 0;
      byEntry.set(row.entry, tally);
    }
    return [...byEntry].map(([entry, tally]) => ({ entry, ...tally }));
  };

  const textureTallies = (): TextureTally[] => {
    const byKey = new Map<string, TextureTally>();
    for (const row of textures.values()) {
      const key = `${row.entry}\n${row.skinId}\n${row.texture}`;
      byKey.set(key, { ...row, players: (byKey.get(key)?.players ?? 0) + 1 });
    }
    return [...byKey.values()].map(({ entry, skinId, texture, players }) => ({ entry, skinId, texture, players }));
  };

  return {
    record: (userId, _modVersion, observations, limits) =>
      Effect.sync(() => {
        for (const { entry, skinId, texture, glint, sample } of observations) {
          const key = `${entry}\n${userId}`;
          const known = seen.get(key);
          if (known === undefined && seen.size >= limits.maxRows) {
            continue;
          }
          const row = known ?? { entry, glint: false, plain: false };
          row.glint ||= glint;
          row.plain ||= !glint;
          seen.set(key, row);
          if (texture !== null) {
            const textureKey = `${key}\n${skinId}`;
            if (textures.has(textureKey) || textures.size < limits.maxRows) {
              textures.set(textureKey, { entry, skinId, texture, players: 1, userId });
            }
          }
          const kept = samples.get(entry) ?? new Map<string, ItemSighting>();
          if (sample !== null && !kept.has(userId) && kept.size < limits.maxSamples) {
            kept.set(userId, sample);
            samples.set(entry, kept);
          }
        }
      }),
    appearances: Effect.sync(() =>
      appearancesFrom(
        tallies(),
        textureTallies(),
        new Map([...samples].map(([entry, byPlayer]) => [entry, [...byPlayer.values()]])),
      ),
    ),
    hints: (entries) => Effect.sync(() => hintsFrom(entries, tallies(), textureTallies())),
  };
};
