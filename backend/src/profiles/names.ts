import type { Line } from "../items/model.ts";

/** `NEST_ZEALOT` and `nest zealot` are both `Nest Zealot`. */
export const titleCase = (id: string): string =>
  id
    .split(/[_\s]+/u)
    .filter((word) => word !== "")
    .map((word) => word.charAt(0).toUpperCase() + word.slice(1).toLowerCase())
    .join(" ");

/** The bosses the server's slayers are named for, by the mob's id. A slayer not here shows its id in title case. */
const SLAYERS: Readonly<Record<string, string>> = {
  zombie: "Revenant Horror",
  enderman: "Voidgloom Seraph",
};
export const slayerName = (id: string): string => SLAYERS[id] ?? titleCase(id);

/** A bestiary id names the mob, with `_MOB` after the ones that are also a slayer's boss. */
export const mobName = (id: string): string => titleCase(id.replace(/_MOB$/u, ""));

/**
 * How the game shows a rank before a name. Neither the server's data nor anything else we have says what colors it
 * uses, so none is guessed: a client draws the prefix in its own. `MVP_PLUS` reads `[MVP+]`, and the rank of a player
 * with none has no prefix.
 */
export const rankPrefix = (rank: string): Line =>
  rank === "DEFAULT" ? [] : [{ text: `[${rank.replace(/_PLUS$/u, "+").replace(/_/gu, " ")}]` }];
