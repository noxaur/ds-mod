import { Result, Schema } from "effect";
import { Base64 } from "effect/encoding";

import { lenient } from "./read.ts";

const TEXTURE_URL = /^https?:\/\/textures\.minecraft\.net\/texture\/[0-9a-fA-F]+$/u;
const MAX_TEXTURE = 8192;

const Textures = Schema.Struct({
  textures: Schema.Record(Schema.String, Schema.Struct({ url: Schema.String })),
});
const texturesOf = lenient(Schema.fromJsonString(Textures));

const texturesIn = (value: string) => {
  if (value === "" || value.length > MAX_TEXTURE) {
    return undefined;
  }
  const json = Base64.decodeString(value);
  if (Result.isFailure(json)) {
    return undefined;
  }
  const textures = texturesOf(json.success)?.textures ?? {};
  const urls = Object.values(textures).map((texture) => texture.url);
  return urls.length > 0 && urls.every((url) => TEXTURE_URL.test(url)) ? textures : undefined;
};

export const safeTexture = (value: unknown): string | undefined =>
  typeof value === "string" && texturesIn(value) !== undefined ? value : undefined;

export const skinUrl = (value: string): string | undefined =>
  texturesIn(value)?.["SKIN"]?.url.replace(/^http:/u, "https:");
