import { describe, expect, it } from "vite-plus/test";
import { face } from "./head";

const skin = (height: 32 | 64, fill: number[], paint: [number, number, number[]][] = []) => {
  const data = new Uint8ClampedArray(64 * height * 4);
  for (let i = 0; i < data.length; i += 4) {
    data.set(fill, i);
  }
  for (const [x, y, rgba] of paint) {
    data.set(rgba, (y * 64 + x) * 4);
  }
  return { width: 64, height, data };
};

const pixel = (out: Uint8ClampedArray, x: number, y: number) => [...out.slice((y * 8 + x) * 4, (y * 8 + x) * 4 + 4)];

const red = [200, 0, 0, 255];
const black = [0, 0, 0, 255];
const clear = [0, 0, 0, 0];

describe("face", () => {
  it("lays the hat over the face where the hat has pixels", () => {
    const out = face(
      skin(64, clear, [
        [8, 8, red],
        [9, 8, red],
        [40, 8, black],
      ]),
    );
    expect(pixel(out, 0, 0)).toEqual(black);
    expect(pixel(out, 1, 0)).toEqual(red);
  });

  it("blends a see-through hat pixel over the face", () => {
    const out = face(
      skin(64, clear, [
        [8, 8, [200, 100, 0, 255]],
        [40, 8, [0, 0, 0, 51]],
      ]),
    );
    expect(pixel(out, 0, 0)).toEqual([160, 80, 0, 255]);
  });

  it("makes the face opaque, as the game does", () => {
    expect(pixel(face(skin(64, clear, [[8, 8, [200, 0, 0, 10]]])), 0, 0)).toEqual(red);
  });

  it("drops an old skin's overlay when none of it is see-through", () => {
    const out = face(skin(32, black, [[8, 8, red]]));
    expect(pixel(out, 0, 0)).toEqual(red);
  });

  it("keeps an old skin's hat when any of its overlay is see-through", () => {
    const out = face(
      skin(32, black, [
        [8, 8, red],
        [63, 31, clear],
      ]),
    );
    expect(pixel(out, 0, 0)).toEqual(black);
  });
});
