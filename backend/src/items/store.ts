import type { Effect } from "effect";
import { Context } from "effect";

import type { ItemAppearance, ItemSighting } from "./model.ts";

/** @keep Each hint needs enough different players, because one client can send anything. */
export interface ItemHints {
  readonly glint: "always" | "never" | "mixed" | null;
  readonly textures: ReadonlyMap<string, string>;
}

export interface Observation {
  readonly entry: string;
  readonly skinId: string;
  readonly texture: string | null;
  readonly glint: boolean;
  readonly sample: ItemSighting | null;
}

export interface ItemLimits {
  readonly maxRows: number;
  readonly maxPlayerRows: number;
  readonly maxSamples: number;
}

export interface EntryTally {
  readonly entry: string;
  readonly players: number;
  readonly glintPlayers: number;
  readonly alwaysGlintPlayers: number;
}

export interface TextureTally {
  readonly entry: string;
  readonly skinId: string;
  readonly texture: string;
  readonly players: number;
}

export const MIN_PLAYERS = 2;

export interface Folded {
  readonly entry: string;
  readonly glint: boolean;
  readonly plain: boolean;
  readonly sample: ItemSighting | null;
  readonly textures: ReadonlyMap<string, string>;
}

export const fold = (observations: readonly Observation[]): readonly Folded[] => {
  const byEntry = new Map<
    string,
    { glint: boolean; plain: boolean; sample: ItemSighting | null; textures: Map<string, string> }
  >();
  for (const { entry, skinId, texture, glint, sample } of observations) {
    const folded = byEntry.get(entry) ?? {
      glint: false,
      plain: false,
      sample: null,
      textures: new Map<string, string>(),
    };
    folded.glint ||= glint;
    folded.plain ||= !glint;
    folded.sample ??= sample;
    if (texture !== null) {
      folded.textures.set(skinId, texture);
    }
    byEntry.set(entry, folded);
  }
  return [...byEntry].map(([entry, folded]) => ({ entry, ...folded }));
};

export const pageOf = (entries: readonly string[], limit: number) => ({
  entries: entries.slice(0, limit),
  next: entries.length > limit ? (entries[limit - 1] ?? null) : null,
});

export interface AppearanceQuery {
  readonly entry?: string | undefined;
  readonly after?: string | undefined;
  readonly limit: number;
}

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

export class ItemStore extends Context.Service<
  ItemStore,
  {
    readonly record: (
      userId: string,
      modVersion: string,
      observations: readonly Observation[],
      limits: ItemLimits,
    ) => Effect.Effect<void>;
    readonly appearances: (query: AppearanceQuery) => Effect.Effect<{
      readonly items: readonly ItemAppearance[];
      readonly next: string | null;
    }>;
    readonly hints: (entries: readonly string[]) => Effect.Effect<ReadonlyMap<string, ItemHints>>;
  }
>()("dsmod/items/ItemStore") {}

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
