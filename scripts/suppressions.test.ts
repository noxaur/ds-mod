import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import { test } from "node:test";
import { added, unexplained } from "./suppressions.ts";

const diff = readFileSync(new URL("suppressions.fixture.diff", import.meta.url), "utf8");

test("finds each suppression and @keep comment a diff adds to code, at its line in the new file", () => {
  assert.deepEqual(added(diff), [
    "mod/src/main/kotlin/A.kt:3",
    "mod/src/main/kotlin/A.kt:22",
    "web/app/b.tsx:2",
    "mod/src/main/kotlin/D.kt:2",
    "mod/src/main/kotlin/D.kt:3",
    "web/app/e.jsx:4",
    "web/f g.ts:3",
    "web/f g.ts:6",
    "web/\\303\\246.ts:1",
    "mod/G.kt:1",
    "mod/G.kt:2",
    "mod/H.kt:1",
    ".github/workflows/i.yml:1",
    "specs/J.tla:1",
    "specs/J.tla:2",
  ]);
});

test("a suppression is explained by a body line that names where it is and gives a reason", () => {
  const body = [
    "## Suppressions",
    "- `mod/src/main/kotlin/A.kt:3`: the server's map is untyped, and a checked cast copies it every tick.",
    "- `mod/src/main/kotlin/A.kt:22`",
    "- `web/app/b.tsx:20`: a reason given for another line.",
    "- `mod/src/main/kotlin/D.kt:2` `mod/src/main/kotlin/D.kt:3` web/app/e.jsx:4",
    "- `old/web/app/e.jsx:4`: a reason given for a file of another path.",
    "- old+mod/G.kt:1: a reason given for a file of another path.",
    "- `mod/G.kt:1backup`: a reason given for a file of another path.",
    "- `mod/G.kt:2`: the annotation group is the form this declaration needs.",
  ].join("\n");
  assert.deepEqual(unexplained(added(diff), body), [
    "mod/src/main/kotlin/A.kt:22",
    "web/app/b.tsx:2",
    "mod/src/main/kotlin/D.kt:2",
    "mod/src/main/kotlin/D.kt:3",
    "web/app/e.jsx:4",
    "web/f g.ts:3",
    "web/f g.ts:6",
    "web/\\303\\246.ts:1",
    "mod/G.kt:1",
    "mod/H.kt:1",
    ".github/workflows/i.yml:1",
    "specs/J.tla:1",
    "specs/J.tla:2",
  ]);
});
