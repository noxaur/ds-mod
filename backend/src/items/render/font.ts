/**
 * How far the game's font moves the cursor for a character, in pixels. The server lays a tooltip out with
 * private-use glyphs that move the cursor by a number of pixels, so the renderer has to measure text the way the
 * game's font does. The widths are the vanilla font's, adjusted where the server's own tooltips measure differently.
 */
const NARROW: Readonly<Record<string, number>> = {
  "!": 2,
  "'": 1,
  ",": 2,
  ".": 2,
  ":": 2,
  ";": 2,
  i: 2,
  "|": 2,
  l: 3,
  "`": 3,
  " ": 4,
  I: 4,
  t: 4,
  "(": 4,
  ")": 4,
  "[": 4,
  "]": 4,
  "{": 4,
  "}": 4,
  "*": 4,
  '"': 4,
  f: 5,
  k: 5,
  "<": 5,
  ">": 5,
  "@": 7,
  "~": 7,
  J: 4,
};

const DEFAULT = 6;
/** The private-use glyphs the server draws a stat's icon with, which add almost nothing to a line's width. */
const ICON = 1;
/** A symbol such as `❁`. */
const SYMBOL = 5;

const advanceOf = (char: string): number => {
  const known = NARROW[char];
  if (known !== undefined) {
    return known;
  }
  const code = char.codePointAt(0) ?? 0;
  if (code < 0x80) {
    return DEFAULT;
  }
  return code >= 0xe000 && code <= 0xf8ff ? ICON : SYMBOL;
};

/** The width of `text` in pixels. */
export const advance = (text: string): number => {
  let total = 0;
  for (const char of text) {
    total += advanceOf(char);
  }
  return total;
};
