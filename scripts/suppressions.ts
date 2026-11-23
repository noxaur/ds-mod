import { execFileSync } from "node:child_process";

export const label = "human review needed";

const markers: [RegExp, RegExp][] = [
  [/\.(kt|kts|java)$/, /@(file:)?Suppress(Warnings)?\(/],
  [/\.(ts|mts|tsx|js|mjs|cjs)$/, /(\/\/|\/\*)\s*eslint-disable/],
];

export function added(diff: string): string[] {
  const found: string[] = [];
  let file = "";
  let line = 0;
  for (const text of diff.split("\n")) {
    if (text.startsWith("+++ ")) {
      file = text.replace(/^\+\+\+ (b\/)?/, "");
      continue;
    }
    const hunk = /^@@ -\d+(?:,\d+)? \+(\d+)/.exec(text);
    if (hunk !== null) {
      line = Number(hunk[1]);
      continue;
    }
    const marker = markers.find(([path]) => path.test(file))?.[1];
    if (text.startsWith("+") && marker?.test(text) === true) found.push(`${file}:${line}`);
    if (text.startsWith("+") || text.startsWith(" ")) line++;
  }
  return found;
}

export function unexplained(found: string[], body: string): string[] {
  const lines = body.split("\n");
  return found.filter((at) => {
    const named = new RegExp(`${at.replace(/[.*+?^${}()|[\]\\]/g, "\\$&")}(?!\\d)`);
    return !lines.some((text) => named.test(text) && text.replace(named, "").split(/\W+/).filter(Boolean).length >= 3);
  });
}

function main() {
  const repo = process.env.GITHUB_REPOSITORY ?? "";
  const pr = process.env.PR ?? "";
  const diff = execFileSync("gh", ["pr", "diff", pr, "--repo", repo], { encoding: "utf8", maxBuffer: 1 << 28 });
  const found = added(diff);
  if (found.length === 0) return;
  execFileSync("gh", ["api", "--method", "POST", `repos/${repo}/issues/${pr}/labels`, "-f", `labels[]=${label}`]);
  const missing = unexplained(found, process.env.BODY ?? "");
  for (const at of missing) {
    console.log(`${at}: this pull request adds a suppression here, so a person reviews it. Add a line to the pull request body that names \`${at}\` and says why the check is wrong at this spot and why the code cannot satisfy it instead.`);
  }
  process.exit(missing.length > 0 ? 1 : 0);
}

if (import.meta.main) main();
