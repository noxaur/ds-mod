import { defineAuth } from "void/auth";

import { mojangHasJoined } from "./src/identity/minecraft.ts";
import { signInOptions } from "./src/identity/options.ts";
import { websiteRules } from "./src/identity/trusted.ts";

export default defineAuth(({ defaults, env }) => {
  const key = env["AUTH_TRUSTED_KEY"];
  return {
    ...defaults,
    ...signInOptions(mojangHasJoined(typeof key === "string" ? key : undefined)),
    /** @keep Challenges need no sign-in and each is a row until it expires, so one address gets few. The count lives
     * in each Worker's memory: it slows one client down and bounds nothing across the deployment. */
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
