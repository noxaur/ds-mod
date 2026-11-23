import { execFileSync } from "node:child_process";

export const label = "human review needed";

const markers: [RegExp, RegExp][] = [
  [/\.(kt|kts|java)$/, /\bSuppress(Warnings)?\b/],
  [/\.(ts|mts|cts|tsx|js|mjs|cjs|jsx)$/, /eslint-disable|\beslint\s+[@\w/-]+\s*:|\/\*\s*eslint\s*$/],
];

export function added(diff: string): string[] {
  const found: string[] = [];
  let file = "";
  let line = 0;
  let header = false;
  for (const text of diff.split("\n")) {
    if (text.startsWith("diff --git ")) {
      header = true;
      continue;
    }
    if (header && text.startsWith("+++ ")) {
      file = text.slice(4).replace(/\t.*$/, "").replace(/^"(.*)"$/, "$1").replace(/^b\//, "");
      continue;
    }
    const hunk = /^@@ -\d+(?:,\d+)? \+(\d+)/.exec(text);
    if (hunk !== null) {
      header = false;
      line = Number(hunk[1]);
      continue;
    }
    if (header) continue;
    const marker = markers.find(([path]) => path.test(file))?.[1];
    if (text.startsWith("+") && marker?.test(text) === true) found.push(`${file}:${line}`);
    if (text.startsWith("+") || text.startsWith(" ")) line++;
  }
  return found;
}

export function unexplained(found: string[], body: string): string[] {
  const lines = body.split("\n");
  return found.filter((at) => {
    const named = new RegExp(`(?<=^|[\\s\`'"(\\[])${at.replace(/[.*+?^${}()|[\]\\]/g, "\\$&")}(?=$|[\\s\`'"),:;]|\\.(\\s|$))`);
    return !lines.some((text) => named.test(text) && text.replace(/\S*:\d+\S*/g, "").split(/\W+/).filter(Boolean).length >= 3);
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
