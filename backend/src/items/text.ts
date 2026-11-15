import type { Line, Span } from "./model.ts";

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

const FORMATS: Readonly<Record<string, "obfuscated" | "bold" | "strikethrough" | "underlined" | "italic">> = {
  k: "obfuscated",
  l: "bold",
  m: "strikethrough",
  n: "underlined",
  o: "italic",
};

// `§x§R§R§G§G§B§B`, the form that names a color the sixteen codes cannot.
const HEX = /^§x(?:§[0-9a-f]){6}/iu;

/**
 * Reads text written with `§` codes into the spans it draws. As in the game, a color code ends every format before it,
 * `§r` ends all, and a code it does not know is dropped. Text with no code is one plain span, and no text is no span.
 */
export const parseLegacy = (text: string): Line => {
  const spans: Span[] = [];
  let style: Omit<Span, "text"> = {};
  let run = "";
  const end = () => {
    if (run !== "") {
      spans.push({ text: run, ...style });
      run = "";
    }
  };
  for (let index = 0; index < text.length; index++) {
    const char = text.charAt(index);
    if (char !== "§") {
      run += char;
      continue;
    }
    end();
    const hex = HEX.exec(text.slice(index, index + 14));
    if (hex !== null) {
      style = { color: `#${hex[0].replace(/§/gu, "").slice(1).toUpperCase()}` };
      index += hex[0].length - 1;
      continue;
    }
    const code = text.charAt(index + 1).toLowerCase();
    const color = COLORS[code];
    const format = FORMATS[code];
    if (color !== undefined) {
      style = { color };
    } else if (format !== undefined) {
      style = { ...style, [format]: true };
    } else if (code === "r") {
      style = {};
    }
    // A lone `§` at the end has no code to skip.
    index += 1;
  }
  end();
  return spans;
};
