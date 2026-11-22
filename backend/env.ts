import { defineEnv, string } from "void/env";

export default defineEnv({
  ADMIN_TOKEN: string().optional(),
  AUTH_TRUSTED_KEY: string().optional(),
});
