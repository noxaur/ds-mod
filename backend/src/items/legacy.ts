import type { Line, Span } from "./model.ts";

/** What `§` followed by each color code means, as the `#RRGGBB` a span carries. */
const COLORS: Readonly<Record<string, string>> = {
  "0": "#000000",
  "1": "#0000AA",
  "2": "#00AA00",
  "3": "#00AAAA",
  "4": "#AA0000",
  "5": "#AA00AA",
  "6": "#FFAA00",
  "7": "#AAAAAA",
  "8": "#555555",
  "9": "#5555FF",
  a: "#55FF55",
  b: "#55FFFF",
  c: "#FF5555",
  d: "#FF55FF",
  e: "#FFFF55",
  f: "#FFFFFF",
};

/** The `#RRGGBB` for a `§` color code such as `6`, or undefined for one that is not a color. */
export const colorOf = (code: string): string | undefined => COLORS[code.toLowerCase()];

type Style = Omit<Span, "text">;

const same = (a: Style, b: Style): boolean =>
  a.color === b.color &&
  a.bold === b.bold &&
  a.italic === b.italic &&
  a.underlined === b.underlined &&
  a.strikethrough === b.strikethrough &&
  a.obfuscated === b.obfuscated;

/** Appends text in a style, joining it to the span before when the style is the same. */
export const push = (spans: Span[], text: string, style: Style): void => {
  if (text === "") {
    return;
  }
  const last = spans.at(-1);
  if (last !== undefined && same(last, style)) {
    spans[spans.length - 1] = { ...last, text: last.text + text };
    return;
  }
  spans.push({ text, ...style });
};

/** A style code: `§`, or the `&` the server's catalog also writes in some descriptions, then the code. */
const MARKER = /[§&]([0-9a-fk-or])/giu;

/**
 * Reads the server's `§` text into a line. A color code clears the styles before it, and `§r` clears everything back
 * to `color`, which is what text with no code in front of it is drawn in. A flag that is false is left out.
 */
export const parseLegacy = (text: string, color?: string): Line => {
  const spans: Span[] = [];
  const base = (): Style => (color === undefined ? {} : { color });
  let style = base();
  let from = 0;
  for (const match of text.matchAll(MARKER)) {
    push(spans, text.slice(from, match.index), style);
    from = match.index + 2;
    const code = (match[1] ?? "").toLowerCase();
    const next = COLORS[code];
    if (next !== undefined) {
      style = { color: next };
    } else if (code === "l") {
      style = { ...style, bold: true };
    } else if (code === "o") {
      style = { ...style, italic: true };
    } else if (code === "n") {
      style = { ...style, underlined: true };
    } else if (code === "m") {
      style = { ...style, strikethrough: true };
    } else if (code === "k") {
      style = { ...style, obfuscated: true };
    } else {
      style = base();
    }
  }
  push(spans, text.slice(from), style);
  return spans;
};
