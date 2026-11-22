import { defineAuth } from "void/auth";

import { mojangHasJoined } from "./src/identity/minecraft.ts";
import { signInOptions } from "./src/identity/options.ts";
import { websiteRules } from "./src/identity/trusted.ts";

const setting = (value: unknown) => (typeof value === "string" && value !== "" ? value : undefined);

export default defineAuth(({ defaults, env }) => {
  const relayUrl = setting(env["MOJANG_RELAY_URL"]);
  return {
    ...defaults,
    ...signInOptions(
      mojangHasJoined(
        relayUrl === undefined ? undefined : { url: relayUrl, key: setting(env["MOJANG_RELAY_KEY"]) ?? "" },
      ),
    ),
    /** @keep Challenges need no sign-in and each is a row until it expires, so one address gets few. The count lives
     * in each Worker's memory: it slows one client down and bounds nothing across the deployment. */
    rateLimit: {
      ...defaults.rateLimit,
      customRules: {
        ...defaults.rateLimit?.customRules,
        ...websiteRules(setting(env["AUTH_TRUSTED_KEY"])),
        "/minecraft/challenge": { window: 60, max: 10 },
        "/minecraft/verify": { window: 60, max: 10 },
      },
    },
  };
});
