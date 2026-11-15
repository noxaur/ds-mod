import { defineEnv, string } from "void/env";

export default defineEnv({
  // What dsctl sends to act as an agent. With none set, nobody can.
  ADMIN_TOKEN: string().optional(),
});
