import { record } from "../../upstream/read.ts";

const stats = (raw: unknown): Record<string, unknown> => {
  const { base, ...rest } = record(raw);
  return { ...rest, stats: base ?? {} };
};

/** @keep Everything the API sends is kept, with three keys moved where the game has them: `entry` is
 * `item_data.item_id`, a stat group's `base` is its `stats`, and `abilities` is `{ abilities }`. `count` and `material`
 * belong to the stack, not its data. A boolean stays one, and the game's NBT holds it as a byte. */
export const gameData = (raw: unknown): Record<string, unknown> => {
  const { entry, count: _count, material: _material, item_data, item_stats, abilities, ...rest } = record(raw);
  const data = record(item_data);
  return {
    ...rest,
    item_data: {
      ...data,
      ...(typeof entry === "string" && entry !== "" ? { item_id: entry } : {}),
      ...("reforge_stats" in data ? { reforge_stats: stats(data["reforge_stats"]) } : {}),
    },
    ...(item_stats === undefined ? {} : { item_stats: stats(item_stats) }),
    ...(abilities === undefined ? {} : { abilities: { abilities } }),
  };
};
