import type { Progress as ProgressModel } from "./model.ts";
import { slayerName, titleCase } from "./names.ts";
import type { Progression } from "./wire.ts";

type Progress = typeof ProgressModel.Type;

/** @keep The server's slayers top out at 9: two players with 17 and 60 million XP both sit there. No table of the XP between
 * levels is in the catalog or anywhere else we have, so a slayer shows the level the server sent and no progress to
 * the next, and the cap is what the data suggests rather than what it says.
 */
export const SLAYER_MAX_LEVEL = 9;

const tableOf = (progression: Progression, skill: string): readonly number[] =>
  skill === "combat" ? progression.combat : skill === "catacombs" ? progression.catacombs : progression.default;

export const skillProgress = (
  progression: Progression,
  id: string,
  totalXp: number,
  shownAs: string = id,
): Progress => {
  const table = tableOf(progression, id);
  let level = 0;
  while (level < table.length && totalXp >= (table[level] ?? Infinity)) {
    level += 1;
  }
  const start = level === 0 ? 0 : (table[level - 1] ?? 0);
  const next = table[level];
  return {
    id: shownAs,
    name: titleCase(shownAs),
    level,
    maxLevel: table.length,
    totalXp,
    xpIntoLevel: totalXp - start,
    xpForNextLevel: next === undefined ? null : next - start,
  };
};

// @keep `slayer` holds `<id>_xp` and `<id>_level` for each slayer.
export const slayerProgress = (slayer: Readonly<Record<string, number>>): Progress[] =>
  Object.entries(slayer).flatMap(([key, totalXp]) => {
    if (!key.endsWith("_xp")) {
      return [];
    }
    const id = key.slice(0, -"_xp".length);
    return [
      {
        id,
        name: slayerName(id),
        level: Math.floor(slayer[`${id}_level`] ?? 0),
        maxLevel: SLAYER_MAX_LEVEL,
        totalXp,
        xpIntoLevel: 0,
        xpForNextLevel: null,
      },
    ];
  });
