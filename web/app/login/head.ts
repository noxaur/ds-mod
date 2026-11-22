export interface Pixels {
  readonly width: number;
  readonly height: number;
  readonly data: Uint8ClampedArray;
}

export function face(skin: Pixels): Uint8ClampedArray<ArrayBuffer> {
  const at = (x: number, y: number) => (y * skin.width + x) * 4;
  const byte = (i: number) => skin.data[i] ?? 0;
  let hat = skin.height !== 32;
  for (let y = 0; !hat && y < 32; y++) {
    for (let x = 32; !hat && x < 64; x++) {
      hat = byte(at(x, y) + 3) < 128;
    }
  }
  const out = new Uint8ClampedArray(8 * 8 * 4);
  for (let y = 0; y < 8; y++) {
    for (let x = 0; x < 8; x++) {
      const pixel = (y * 8 + x) * 4;
      const under = at(8 + x, 8 + y);
      const over = at(40 + x, 8 + y);
      const alpha = hat ? byte(over + 3) / 255 : 0;
      for (let channel = 0; channel < 3; channel++) {
        out[pixel + channel] = byte(over + channel) * alpha + byte(under + channel) * (1 - alpha);
      }
      out[pixel + 3] = 255;
    }
  }
  return out;
}
