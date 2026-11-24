import type { Line } from "../items/model.ts";
import { titleCase } from "../upstream/title.ts";

export { titleCase };

const SLAYERS: Readonly<Record<string, string>> = {
  zombie: "Revenant Horror",
  enderman: "Voidgloom Seraph",
};
export const slayerName = (id: string): string => SLAYERS[id] ?? titleCase(id);

// @keep A bestiary id names the mob, with `_MOB` after the ones that are also a slayer's boss.
export const mobName = (id: string): string => titleCase(id.replace(/_MOB$/u, ""));

const GREEN = "#55FF55";
const AQUA = "#55FFFF";

/** @keep The ranks whose colors the game shows the same for everyone, as captured. A `DRAGON` player picks their own color, and the staff ranks were never captured. */
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

export const rankPrefix = (rank: string): Line =>
  rank === "DEFAULT" ? [] : (COLORED[rank] ?? [{ text: `[${rank.replace(/_PLUS$/u, "+").replace(/_/gu, " ")}]` }]);
