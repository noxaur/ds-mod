import { voidPlugin } from "void";
import { defineConfig } from "vite-plus";

export default defineConfig({
  // The tests call the Effect API directly, and Void's plugin would start a second server inside the test runner.
  plugins: process.env["VITEST"] === undefined ? [voidPlugin()] : [],
  test: {
    globalSetup: ["./scripts/test-time-limit.ts"],
    // Threads end with the process, so a test stuck in a loop dies with the run that the time limit ends.
    pool: "threads",
  },
  fmt: { ignorePatterns: [".void/**"], printWidth: 120 },
  lint: {
    ignorePatterns: [".void/**"],
    jsPlugins: ["./lint/dsmod.js"],
    options: { typeAware: true, typeCheck: true, denyWarnings: true },
    categories: { correctness: "error", suspicious: "error" },
    rules: {
      eqeqeq: "error",
      curly: "error",
      "no-console": "error",
      "no-param-reassign": "error",
      "prefer-const": "error",
      "typescript/consistent-type-imports": "error",
      "typescript/strict-boolean-expressions": "error",
      "typescript/switch-exhaustiveness-check": "error",
      "typescript/no-floating-promises": "error",
      "dsmod/no-try": "error",
      // An Effect generator leaves early with `return yield* failure`, which this rule reads as a missing value.
      // TypeScript's noImplicitReturns still catches a function that forgets to return.
      "typescript/consistent-return": "off",
    },
    overrides: [
      {
        files: ["src/**"],
        rules: {
          "no-restricted-imports": [
            "error",
            {
              patterns: [
                {
                  group: ["void", "void/*"],
                  message:
                    "src/ is the Effect API and runs without Void. Reach a Cloudflare resource through a service the route file provides.",
                },
              ],
            },
          ],
        },
      },
    ],
  },
});
