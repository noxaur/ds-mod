import { execFileSync } from "node:child_process";

const reaches: [string, string[]][] = [
  ["mise.toml", ["check:mod", "check:backend", "check:web"]],
  ["mod/", ["check:mod"]],
  ["specs/", ["check:mod"]],
  ["api/", ["check:mod", "check:backend"]],
  ["README.md", ["check:mod"]],
  ["docs/user/features.md", ["check:mod"]],
  ["LICENSE", ["check:mod"]],
  ["backend/", ["check:backend"]],
  ["web/shared/", ["check:backend", "check:web"]],
  ["web/", ["check:web"]],
  ["scripts/test-time-limit.ts", ["check:backend", "check:web"]],
];

export function affected(paths: string[]): string[] {
  const tasks = new Set(paths.flatMap((path) => reaches.filter(([prefix]) => path === prefix || (prefix.endsWith("/") && path.startsWith(prefix))).flatMap(([, reached]) => reached)));
  return [...new Set(reaches.flatMap(([, reached]) => reached))].filter((task) => tasks.has(task));
}

if (import.meta.main) {
  const staged = execFileSync("git", ["diff", "--cached", "--name-only", "--no-renames", "-z"], { encoding: "utf8" }).split("\0").filter(Boolean);
  console.log(affected(staged).join(" ::: "));
}
