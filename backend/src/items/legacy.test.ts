import { expect, test } from "vite-plus/test";

import { parseLegacy } from "./legacy.ts";

test("a color code starts a span in that color", () => {
  expect(parseLegacy("§7Hello §aworld")).toEqual([
    { text: "Hello ", color: "#AAAAAA" },
    { text: "world", color: "#55FF55" },
  ]);
});

test("a style stays until a color or a reset, and a flag that is false is left out", () => {
  expect(parseLegacy("§8§oStill warm.§r plain §c§lloud")).toEqual([
    { text: "Still warm.", color: "#555555", italic: true },
    { text: " plain " },
    { text: "loud", color: "#FF5555", bold: true },
  ]);
  expect(Object.keys(parseLegacy("§7x")[0] ?? {}).toSorted()).toEqual(["color", "text"]);
});

test("every style code sets its flag", () => {
  expect(parseLegacy("§k§l§m§n§oa")).toEqual([
    { text: "a", obfuscated: true, bold: true, strikethrough: true, underlined: true, italic: true },
  ]);
});

test("text with no code is drawn in the color given, and a reset goes back to it", () => {
  expect(parseLegacy("plain §6gold§r again", "#FFFFFF")).toEqual([
    { text: "plain ", color: "#FFFFFF" },
    { text: "gold", color: "#FFAA00" },
    { text: " again", color: "#FFFFFF" },
  ]);
});

test("the & the catalog writes in some descriptions is a code too", () => {
  expect(parseLegacy("&7Each eye &6Golden &a12.5%&7.")).toEqual([
    { text: "Each eye ", color: "#AAAAAA" },
    { text: "Golden ", color: "#FFAA00" },
    { text: "12.5%", color: "#55FF55" },
    { text: ".", color: "#AAAAAA" },
  ]);
  expect(parseLegacy("Fire & Ice")).toEqual([{ text: "Fire & Ice" }]);
});

test("an empty line has no spans, and neighbors in one style are one span", () => {
  expect(parseLegacy("")).toEqual([]);
  expect(parseLegacy("§7a§7b§r§7c")).toEqual([{ text: "abc", color: "#AAAAAA" }]);
});

test("a code at the very end or an unknown one does not throw", () => {
  expect(parseLegacy("text§")).toEqual([{ text: "text§" }]);
  expect(parseLegacy("a§zb")).toEqual([{ text: "a§zb" }]);
});
