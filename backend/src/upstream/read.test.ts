import { expect, test } from "vite-plus/test";

import { parseColor } from "./read.ts";

test("a color is read from #RRGGBB, from the catalog's r,g,b, or from a number", () => {
  expect(parseColor("#1589B6")).toBe(0x1589b6);
  expect(parseColor("21,137,182")).toBe(0x1589b6);
  expect(parseColor(" 255, 255 ,255 ")).toBe(0xffffff);
  expect(parseColor(0x123456)).toBe(0x123456);
  for (const bad of ["", "#12345", "#GGGGGG", "256,0,0", "1,2", "1,2,3,4", "a,b,c", -1, 0x1000000, 1.5, null, {}]) {
    expect(parseColor(bad)).toBeUndefined();
  }
});
