import { expect, test } from "vite-plus/test";

import { safeTexture } from "./texture.ts";

const texture = (url: string): string => btoa(JSON.stringify({ textures: { SKIN: { url } } }));
const skin = (hex: string): string => texture(`http://textures.minecraft.net/texture/${hex}`);

test("a texture on Mojang's texture server is accepted, over http or https", () => {
  const plain = skin("78bd1889bf8a635ec71fc05b1ed548763778e3d371926d39a62c29d350c48b49");
  expect(safeTexture(plain)).toBe(plain);
  const secure = texture("https://textures.minecraft.net/texture/ABCDEF0123");
  expect(safeTexture(secure)).toBe(secure);
});

test("a texture is dropped unless it decodes and names only Mojang's texture server", () => {
  const rejected: unknown[] = [
    undefined,
    null,
    42,
    "",
    "not base64 at all",
    btoa("not json"),
    btoa(JSON.stringify({ textures: {} })),
    btoa(JSON.stringify({ nothing: true })),
    btoa(JSON.stringify({ textures: { SKIN: {} } })),
    btoa(JSON.stringify({ textures: { SKIN: { url: 5 } } })),
    texture("http://evil.example/texture/abc123"),
    texture("http://textures.minecraft.net.evil.example/texture/abc123"),
    texture("http://textures.minecraft.net@evil.example/texture/abc123"),
    texture("http://evil.example/?http://textures.minecraft.net/texture/abc123"),
    texture("http://textures.minecraft.net/texture/abc123/../../x"),
    texture("http://textures.minecraft.net/texture/"),
    texture("http://textures.minecraft.net/texture/xyz"),
    texture("http://textures.minecraft.net/texture/abc123?x=1"),
    texture("ftp://textures.minecraft.net/texture/abc123"),
    texture("file:///etc/passwd"),
    texture("//textures.minecraft.net/texture/abc123"),
    texture("http://textures.minecraft.net/texture/abc123\nhttp://evil.example"),
    "a".repeat(9000),
  ];
  for (const value of rejected) {
    expect(safeTexture(value)).toBeUndefined();
  }
});

test("one bad URL among good ones drops the whole texture", () => {
  const mixed = btoa(
    JSON.stringify({
      textures: {
        SKIN: { url: "http://textures.minecraft.net/texture/abc123" },
        CAPE: { url: "http://evil.example/cape" },
      },
    }),
  );
  expect(safeTexture(mixed)).toBeUndefined();
});
