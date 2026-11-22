import { defineConfig } from "vite-plus";

export default defineConfig({
  fmt: { ignorePatterns: [".next/**", "next-env.d.ts", "pnpm-lock.yaml"], printWidth: 120 },
  test: {
    include: ["app/**/*.test.ts"],
    globalSetup: ["../scripts/test-time-limit.ts"],
    // Threads end with the process, so a test stuck in a loop dies with the run that the time limit ends.
    pool: "threads",
  },
});
