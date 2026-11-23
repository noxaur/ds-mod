import assert from "node:assert/strict";
import { mkdirSync, mkdtempSync, writeFileSync } from "node:fs";
import { tmpdir } from "node:os";
import { join } from "node:path";
import { test } from "node:test";
import { check } from "./docs.ts";

const write = (docs: string, pages: Record<string, string>) => {
  for (const [page, text] of Object.entries(pages)) {
    mkdirSync(join(docs, page, ".."), { recursive: true });
    writeFileSync(join(docs, page), text);
  }
  return docs;
};

test("passes docs that keep every rule", () => {
  const docs = write(mkdtempSync(join(tmpdir(), "docs-")), {
    "README.md": "- [Install](user/install.md)\n- [Menus](internals/menus.md)\n- [Glossary](internals/glossary.md)\n- [Release](operations/release.md)\n- [Deep](user/more/deep.md)\n",
    "user/install.md": "# Install\n\nPut your jar in mods.\n",
    "user/more/deep.md": "# Deep\n",
    "internals/menus.md": "# Menus\n\nThe mod reads the menu when the server changes it.\n## Testing\n",
    "internals/glossary.md": "# Glossary\n\n`Feature`: a thing the player turns on.\n",
    "operations/release.md": "# Release\n\nTag the user's commit.\n",
  });
  assert.deepEqual(check(docs), []);
});

test("names the file, the line and the fix for each rule it breaks", () => {
  const docs = write(mkdtempSync(join(tmpdir(), "docs-")), {
    "README.md": "- [Menus](internals/menus.md)\n- [Release](operations/release.md)\n",
    "user/install.md": "# Install\n",
    "internals/menus.md": `# Menus\r\n\r\n## How to read a menu\r\nCall \`Menus.open\`.\r\n## Long\r\n${"a".repeat(601)}\r\n`,
    "operations/release.md": "# Release\n\nTag your commit.\n",
  });
  assert.deepEqual(check(docs), [
    "docs/README.md:1: link user/install.md here with one line on what it covers",
    "docs/internals/menus.md:3:## How to read a menu",
    "docs/internals records why, not steps. Move the recipe to docs/operations/.",
    "docs/internals/menus.md:4:Call `Menus.open`.",
    "docs/internals records decisions in the domain's words, so it names no code. Say what was decided and why, and let the source show how.",
    "docs/internals/menus.md:5: cut this section to one paragraph of at most 600 characters: the decision, the constraint that decided it and the alternative it rejected. A limitation is an issue, not a section.",
    "docs/operations/release.md:3:Tag your commit.",
    `docs/operations is read by an agent. The machine, desktop and accounts are the user's, so write "the user's".`,
  ]);
});
