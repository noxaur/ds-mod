import { voidPlugin } from "void";
import { defineConfig } from "vite-plus";

export default defineConfig({
  // The tests call the Effect API directly, and Void's plugin would start a second server inside the test runner.
  plugins: process.env["VITEST"] === undefined ? [voidPlugin()] : [],
  // Void writes the migrations, so formatting them would only make its next one differ.
  fmt: { ignorePatterns: [".void/**", "db/migrations/**"], printWidth: 120 },
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
  },
});
