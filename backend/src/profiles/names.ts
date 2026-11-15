import type { Line } from "../items/model.ts";
import { titleCase } from "../items/title.ts";

export { titleCase };

/** The bosses the server's slayers are named for, by the mob's id. A slayer not here shows its id in title case. */
const SLAYERS: Readonly<Record<string, string>> = {
  zombie: "Revenant Horror",
  enderman: "Voidgloom Seraph",
};
export const slayerName = (id: string): string => SLAYERS[id] ?? titleCase(id);

/** A bestiary id names the mob, with `_MOB` after the ones that are also a slayer's boss. */
export const mobName = (id: string): string => titleCase(id.replace(/_MOB$/u, ""));

const GREEN = "#55FF55";
const AQUA = "#55FFFF";

/**
 * The ranks whose colors the game's chat and tab list show the same for everyone, as captured. A `DRAGON` player picks
 * their own color, and the staff ranks were never captured, so those are left plain for a client to draw in its own.
 */
const COLORED: Readonly<Record<string, Line>> = {
  VIP: [{ text: "[VIP]", color: GREEN }],
  VIP_PLUS: [
    { text: "[VIP", color: GREEN },
    { text: "+", color: "#FFAA00" },
    { text: "]", color: GREEN },
  ],
  MVP: [{ text: "[MVP]", color: AQUA }],
  MVP_PLUS: [
    { text: "[MVP", color: AQUA },
    { text: "+", color: "#FF5555" },
    { text: "]", color: AQUA },
  ],
};

/** How the game shows a rank before a name. `MVP_PLUS` reads `[MVP+]`, and the rank of a player with none has no prefix. */
export const rankPrefix = (rank: string): Line =>
  rank === "DEFAULT" ? [] : (COLORED[rank] ?? [{ text: `[${rank.replace(/_PLUS$/u, "+").replace(/_/gu, " ")}]` }]);
