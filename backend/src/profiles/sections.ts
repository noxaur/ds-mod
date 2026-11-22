import { type Catalog, named, statSheet } from "../items/render.ts";
import { count, lists, numberEntries, record, statSet, text } from "../upstream/read.ts";
import { titleCase } from "../upstream/title.ts";
import type { Profile } from "./model.ts";
import { mobName } from "./names.ts";
import { skillProgress } from "./progress.ts";
import type { Progression } from "./wire.ts";

/* @keep Each section is read leniently, so a section that changes shape costs only itself: a value that is missing or
 * of the wrong type is left out, and a section that is no object is empty. */

export const playerStats = (catalog: Catalog, raw: unknown): Profile["playerStats"] => statSheet(catalog, statSet(raw));

export const bestiary = (raw: unknown): Profile["bestiary"] =>
  Object.entries(record(raw))
    .flatMap(([id, entry]) => {
      if (typeof entry !== "object" || entry === null || Array.isArray(entry)) {
        return [];
      }
      const kills = record(entry)["kills"];
      // @keep Null is how the server leaves a count out. Anything else that is not finite is a shape change.
      if (kills !== undefined && kills !== null && !Number.isFinite(kills)) {
        return [];
      }
      return [{ id, name: mobName(id), kills: Math.floor(count(kills) ?? 0) }];
    })
    .toSorted((a, b) => b.kills - a.kills);

export const collections = (catalog: Catalog, raw: unknown): Profile["collections"] =>
  Object.entries(numberEntries(raw)).map(([id, amount]) => ({ ...named(catalog, id), amount }));

export const rngMeters = (raw: unknown): Profile["rngMeters"] =>
  Object.entries(record(raw)).map(([id, meter]) => ({
    id,
    name: titleCase(id),
    progress: count(record(meter)["kills"]) ?? 0,
  }));

const floorName = (id: string): string => {
  const [, kind, number] = /^([FM])(\d+)$/u.exec(id) ?? [];
  return kind === undefined ? titleCase(id) : `${kind === "F" ? "Floor" : "Master Mode"} ${number ?? ""}`;
};

const floorOrder = (id: string): number => {
  const [, kind, number] = /^([FM])(\d+)$/u.exec(id) ?? [];
  return kind === undefined ? Infinity : (kind === "F" ? 0 : 1000) + Number(number);
};

export const dungeons = (progression: Progression, raw: unknown): Profile["dungeons"] => {
  const source = record(raw);
  if (Object.keys(source).length === 0) {
    return null;
  }
  const completions = numberEntries(source["completions"]);
  const scores = numberEntries(source["best_score"]);
  const selected = text(source["class"]);
  return {
    selectedClass: selected === undefined || selected === "" ? null : { id: selected, name: titleCase(selected) },
    secrets: count(source["secrets"]) ?? 0,
    zombieKills: count(source["zombie_kills"]) ?? 0,
    skeletorKills: count(source["skeletor_kills"]) ?? 0,
    classes: Object.entries(numberEntries(source["class_xp"])).map(([id, xp]) =>
      skillProgress(progression, "catacombs", xp, id),
    ),
    floors: [...new Set([...Object.keys(completions), ...Object.keys(scores)])]
      .toSorted((a, b) => floorOrder(a) - floorOrder(b))
      .map((id) => ({
        id,
        name: floorName(id),
        completions: completions[id] ?? 0,
        bestScore: scores[id] ?? null,
      })),
    essence: Object.entries(numberEntries(source["essence"])).map(([id, amount]) => ({
      id,
      name: titleCase(id),
      amount,
    })),
  };
};

export const hotd = (raw: unknown): Profile["hotd"] => {
  const source = record(raw);
  if (Object.keys(source).length === 0) {
    return null;
  }
  const ability = text(source["ability"]);
  return {
    tier: count(source["tier"]) ?? 0,
    exp: count(source["exp"]) ?? 0,
    core: count(source["core"]) ?? 0,
    ender: count(source["ender"]) ?? 0,
    void: count(source["void"]) ?? 0,
    resets: count(source["resets"]) ?? 0,
    ability: ability === undefined || ability === "" ? null : { id: ability, name: titleCase(ability) },
    tokens: count(source["tokens"]) ?? 0,
    nodes: Object.entries(numberEntries(source["nodes"])).map(([id, level]) => ({
      id,
      name: titleCase(id),
      level,
    })),
  };
};

export const forge = (catalog: Catalog, raw: unknown): Profile["forge"] =>
  (lists(raw) ?? []).flatMap((slot, index) => {
    const source = record(slot);
    const recipe = text(source["recipe"]);
    if (recipe === undefined) {
      return [];
    }
    const startedAt = count(source["started"]) ?? 0;
    const durationMs = count(source["duration"]) ?? 0;
    return [
      {
        slot: Math.trunc(count(source["slot"]) ?? index),
        recipe,
        name: named(catalog, recipe).name,
        startedAt,
        durationMs,
        finishesAt: startedAt + durationMs,
        stage: text(source["stage"]) ?? "",
      },
    ];
  });
