import assert from "node:assert/strict";
import { mkdirSync, mkdtempSync, writeFileSync } from "node:fs";
import { tmpdir } from "node:os";
import { join } from "node:path";
import { test } from "node:test";
import { entry, scan } from "./comments.ts";

const sources: Record<string, string> = {
  "a.kt": 'val u = "https://x" // kt tail\n/* kt block */\n/** kt doc */\nfun f() = """raw // ${"a /* b */"}"""\nval c = \'/\'\n',
  "c.kt": "val y = 0\n/** first § */\n/** second */\nfun f() = 1\n",
  "a.kts":"plugins {} // kts tail\n",
  "a.java": 'class A { String s = "//x"; /* java block */ } // java tail\n',
  "a.ts": 'const r = /https?:\\/\\//; // ts tail\nconst t = `a // ${"/* x */"}`;\n/** ts doc */\nexport {};\n',
  "a.tsx": "export const A = () => <div>{/* tsx */}</div>;\n",
  "a.mjs": 'export default "//"; /* mjs */\n',
  "a.css": '.a { content: "/* no */"; } /* css */\n',
  "a.yml": 'a: "#x" # yml tail\n# yml full\n',
  "a.sh": '#!/bin/sh\necho "#x" # sh tail\n',
  "a.tla": 'x == "\\* no" \\* tla line\n(* tla block *)\n(* tla (* nested *)\n  lines *)\n',
  "a.toml": 'a = "#x"\n# toml\nb = """\n# no\n""" # toml tail\nc = \'\'\'\n# no\n\'\'\'\n',
  ".githooks/pre-commit": "#!/bin/sh\n# hook\necho hi\n",
  "a.properties": "# properties\na=#x\n",
  "b.kt": "val x = 1 // rule:text-by-role the user agreed\n",
  "b.ts": "// eslint-disable-next-line no-console -- the one place that logs\nconsole.log(1);\n",
  "k.kt": "val x = 1 // @keep kept\n/** @keep kept */\nval y = 2 // @keep\n",
  "k.yml": "# @keep kept\n",
  "k.tla": "\\* @keep kept\n",
};

const expected = [
  "a.kt: // kt tail",
  "a.kt: /* kt block */",
  "a.kt: /** kt doc */",
  "c.kt: /** first § */",
  "c.kt: /** second */",
  "k.kt: // @keep",
  "a.kts: // kts tail",
  "a.java: /* java block */",
  "a.java: // java tail",
  "a.ts: // ts tail",
  "a.ts: /** ts doc */",
  "a.tsx: /* tsx */",
  "a.mjs: /* mjs */",
  "a.css: /* css */",
  "a.yml: # yml tail",
  "a.yml: # yml full",
  "a.sh: # sh tail",
  "a.tla: \\* tla line",
  "a.tla: (* tla block *)",
  "a.tla: (* tla (* nested *) lines *)",
  "a.toml: # toml",
  "a.toml: # toml tail",
  ".githooks/pre-commit: # hook",
  "a.properties: # properties",
];

test("finds every kind of comment, and no string, shebang or approved exception", () => {
  const root = mkdtempSync(join(tmpdir(), "comments-"));
  mkdirSync(join(root, ".githooks"));
  for (const [file, text] of Object.entries(sources)) writeFileSync(join(root, file), text);
  const found = scan(root, Object.keys(sources)).map(entry);
  assert.deepEqual(found.sort(), [...expected].sort());
});
