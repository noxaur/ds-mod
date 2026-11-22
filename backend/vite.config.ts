import { voidPlugin } from "void";
import { defineConfig } from "vite-plus";

const noVoid = {
  group: ["void", "void/*"],
  message:
    "src/ is the Effect API and runs without Void. Reach a Cloudflare resource through a service the route file provides.",
};

const noRenderInside = {
  group: ["**/items/render/*"],
  message:
    "items/render/ is the item renderer's inside. Import a reader of dragsim.co's data from src/upstream/, and the renderer from items/render.ts.",
};

export default defineConfig({
  // The tests call the Effect API directly, and Void's plugin would start a second server inside the test runner.
  plugins: process.env["VITEST"] === undefined ? [voidPlugin()] : [],
  test: {
    globalSetup: ["../scripts/test-time-limit.ts"],
    // Threads end with the process, so a test stuck in a loop dies with the run that the time limit ends.
    pool: "threads",
  },
  // Void writes the migrations, so formatting them would only make its next one differ. The item fixtures are the
  // server's data as it sent it, in one line each, and formatting would triple their size.
  fmt: { ignorePatterns: [".void/**", "db/migrations/**", "src/items/fixtures/**"], printWidth: 120 },
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
      "dsmod/no-computed-console": "error",
      "typescript/no-explicit-any": "error",
      // An Effect generator leaves early with `return yield* failure`, which this rule reads as a missing value.
      // TypeScript's noImplicitReturns still catches a function that forgets to return.
      "typescript/consistent-return": "off",
    },
    overrides: [
      {
        files: ["src/**"],
        rules: {
          "dsmod/no-json-parse": "error",
          "dsmod/time-through-effect": "error",
          "no-restricted-imports": ["error", { patterns: [noVoid, noRenderInside] }],
        },
      },
      {
        files: ["src/profiles/**"],
        rules: {
          "no-restricted-imports": [
            "error",
            {
              patterns: [
                noVoid,
                noRenderInside,
                {
                  group: ["**/items/*", "!**/items/model.ts", "!**/items/render.ts"],
                  message:
                    "Profiles draws items only through items/model.ts and items/render.ts. Read dragsim.co's data with src/upstream/.",
                },
              ],
            },
          ],
        },
      },
      { files: ["src/**/*.test.ts"], rules: { "dsmod/no-json-parse": "off" } },
      { files: ["**/*.test.ts", "test/**"], rules: { "dsmod/test-harness": "error" } },
      { files: ["test/harness.ts"], rules: { "dsmod/test-harness": "off" } },
      {
        files: ["src/**/*.test.ts", "src/ids.ts", "src/identity/minecraft.ts"],
        rules: { "dsmod/time-through-effect": "off" },
      },
    ],
  },
});
