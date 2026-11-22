import { defineEnv, string, url } from "void/env";

export default defineEnv({
  // What dsctl sends to act as an agent. With none set, nobody can.
  ADMIN_TOKEN: string().optional(),
  AUTH_TRUSTED_KEY: string().optional(),
  MOJANG_RELAY: url().optional(),
});
