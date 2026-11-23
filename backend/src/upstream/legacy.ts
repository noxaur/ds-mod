import type { Line, Span } from "../items/model.ts";

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

export const colorOf = (code: string): string | undefined => COLORS[code.toLowerCase()];

type Style = Omit<Span, "text">;

const same = (a: Style, b: Style): boolean =>
  a.color === b.color &&
  a.bold === b.bold &&
  a.italic === b.italic &&
  a.underlined === b.underlined &&
  a.strikethrough === b.strikethrough &&
  a.obfuscated === b.obfuscated &&
  a.font === b.font;

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

const MARKER = /[§&]([0-9a-fk-or])|§x((?:§[0-9a-f]){6})/giu;

export const parseLegacy = (text: string, color?: string): Line => {
  const spans: Span[] = [];
  const base = (): Style => (color === undefined ? {} : { color });
  let style = base();
  let from = 0;
  for (const match of text.matchAll(MARKER)) {
    push(spans, text.slice(from, match.index), style);
    from = match.index + match[0].length;
    const hex = match[2];
    const code = (match[1] ?? "").toLowerCase();
    const next = hex === undefined ? COLORS[code] : `#${hex.replaceAll("§", "").toUpperCase()}`;
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
