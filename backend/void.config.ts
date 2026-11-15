import { defineConfig } from "void/config";

export default defineConfig({
  // Void finds bindings by scanning source folders, and `store.ts` at the root is in none of them.
  inference: { bindings: { storage: true } },
  worker: {
    compatibility_date: "2026-02-24",
  },
});
