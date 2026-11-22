import { defineAuth } from "void/auth";

import { mojangHasJoined } from "./src/identity/minecraft.ts";
import { signInOptions } from "./src/identity/options.ts";
import { websiteRules } from "./src/identity/trusted.ts";

export default defineAuth(({ defaults, env }) => {
  const key = env["AUTH_TRUSTED_KEY"];
  return {
    ...defaults,
    ...signInOptions(mojangHasJoined(typeof key === "string" ? key : undefined)),
    // A sign-in is two requests, and the mod repeats it once a day. Anyone can ask for a challenge without signing in,
    // and each one is a row until it expires, so one address gets few. The count is kept in each Worker's memory, so
    // it slows one client down and is no bound across the deployment.
    rateLimit: {
      ...defaults.rateLimit,
      customRules: {
        ...defaults.rateLimit?.customRules,
        ...websiteRules(key),
        "/minecraft/challenge": { window: 60, max: 10 },
        "/minecraft/verify": { window: 60, max: 10 },
      },
    },
  };
});
