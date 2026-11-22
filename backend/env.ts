import { defineEnv, string, url } from "void/env";

export default defineEnv({
  ADMIN_TOKEN: string().optional(),
  AUTH_TRUSTED_KEY: string().optional(),
  MOJANG_RELAY_URL: url().optional(),
  MOJANG_RELAY_KEY: string().optional(),
});
