import { bearer } from "better-auth/plugins/bearer";
import { oneTimeToken } from "better-auth/plugins/one-time-token";
import { defineAuth } from "void/auth";

import { minecraft } from "./src/identity/minecraft.ts";

export default defineAuth(({ defaults }) => ({
  ...defaults,
  // A player is their Minecraft account. No other way in exists, so nobody can make an account Mojang did not vouch for.
  emailAndPassword: { enabled: false },
  // A session ends 24 hours after it began and is never extended, so a stolen token or cookie dies on its own.
  // The mod proves itself to Mojang again without the player noticing.
  session: { expiresIn: 60 * 60 * 24, disableSessionRefresh: true },
  // A sign-in is two requests, and the mod repeats it once a day. Anyone can ask for a challenge without signing in,
  // and each one is a row until it expires, so one address gets few.
  rateLimit: {
    ...defaults.rateLimit,
    customRules: {
      ...defaults.rateLimit?.customRules,
      "/minecraft/challenge": { window: 60, max: 10 },
      "/minecraft/verify": { window: 60, max: 10 },
    },
  },
  plugins: [
    minecraft(),
    // The mod has no cookie jar, so it sends its session as a bearer token.
    bearer(),
    // `/dsmod login` trades the mod's session for a link that signs a browser in once, within a minute.
    oneTimeToken({ expiresIn: 1, storeToken: "hashed" }),
  ],
}));
