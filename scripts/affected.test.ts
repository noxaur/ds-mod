import assert from "node:assert/strict";
import { test } from "node:test";
import { affected } from "./affected.ts";

test("runs only the project a change sits in", () => {
  assert.deepEqual(affected(["mod/src/main/kotlin/A.kt"]), ["check:mod"]);
  assert.deepEqual(affected(["backend/src/api.ts", "web/app/page.tsx"]), ["check:backend", "check:web"]);
});

test("runs the projects that read a file outside their folder", () => {
  assert.deepEqual(affected(["specs/Contribute.tla"]), ["check:mod"]);
  assert.deepEqual(affected(["api/openapi.json"]), ["check:mod", "check:backend"]);
  assert.deepEqual(affected(["web/shared/website.ts"]), ["check:backend", "check:web"]);
  assert.deepEqual(affected(["docs/user/features.md"]), ["check:mod"]);
});

test("runs every project when the tool versions change", () => {
  assert.deepEqual(affected(["mise.toml"]), ["check:mod", "check:backend", "check:web"]);
});

test("runs no project for a change only the light checks read", () => {
  assert.deepEqual(affected(["docs/internals/ui.md", "AGENTS.md", "scripts/docs.ts", ".githooks/pre-commit", "modules/x"]), []);
});
