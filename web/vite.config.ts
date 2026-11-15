import { defineConfig } from "vite-plus";

export default defineConfig({
  fmt: { ignorePatterns: [".next/**", "next-env.d.ts", "pnpm-lock.yaml"], printWidth: 120 },
  test: { include: ["app/**/*.test.ts"] },
});
