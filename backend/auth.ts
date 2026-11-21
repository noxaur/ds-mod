import { defineAuth } from "void/auth";

import { signInOptions } from "./src/identity/options.ts";
import { websiteRules } from "./src/identity/trusted.ts";

export default defineAuth(({ defaults, env }) => ({
  ...defaults,
  ...signInOptions(),
  // A sign-in is two requests, and the mod repeats it once a day. Anyone can ask for a challenge without signing in,
  // and each one is a row until it expires, so one address gets few. The count is kept in each Worker's memory, so
  // it slows one client down and is no bound across the deployment.
  rateLimit: {
    ...defaults.rateLimit,
    customRules: {
      ...defaults.rateLimit?.customRules,
      ...websiteRules(env["AUTH_TRUSTED_KEY"]),
      "/minecraft/challenge": { window: 60, max: 10 },
      "/minecraft/verify": { window: 60, max: 10 },
    },
  },
}));
