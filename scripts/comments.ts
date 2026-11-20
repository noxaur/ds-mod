import { execFileSync } from "node:child_process";
import { existsSync, readFileSync, writeFileSync } from "node:fs";
import { join } from "node:path";

const kinds: Record<string, string[]> = {
  kotlin: ["line_comment", "multiline_comment"],
  java: ["line_comment", "block_comment"],
  typescript: ["comment"],
  tsx: ["comment"],
  javascript: ["comment"],
  css: ["comment"],
  yaml: ["comment"],
  bash: ["comment"],
};

const byExtension: Record<string, string> = {
  kt: "kotlin",
  kts: "kotlin",
  java: "java",
  ts: "typescript",
  mts: "typescript",
  tsx: "tsx",
  js: "javascript",
  mjs: "javascript",
  css: "css",
  yml: "yaml",
  yaml: "yaml",
  sh: "bash",
};

const byLine: Record<string, RegExp> = {
  tla: /(\\\*|\(\*).*/,
  toml: /^\s*#.*/,
  properties: /^\s*[#!].*/,
};

const generated = new Set(["mod/gradlew", "mod/gradlew.bat", "web/next-env.d.ts"]);

const approved = [
  /^\/\/ rule:[a-z-]+ \S/,
  /^\/\/ eslint-disable-next-line [\w/@-]+(, [\w/@-]+)* -- \S/,
  /^(\/\/|\/\*\*?|#|\\\*|\(\*)\s*@keep \S/,
];

export type Comment = { file: string; line: number; text: string };

const languageOf = (file: string) =>
  file.startsWith(".githooks/") ? "bash" : byExtension[file.slice(file.lastIndexOf(".") + 1)];

const rules = () =>
  Object.entries(kinds)
    .flatMap(([language, names]) => names.map((kind) => `id: ${language}-${kind}\nlanguage: ${language}\nrule: { kind: ${kind} }`))
    .join("\n---\n");

function droppedBefore(root: string, file: string, offset: number): Comment {
  const before = readFileSync(join(root, file)).subarray(0, offset).toString().trimEnd();
  const start = before.endsWith("*/") ? before.lastIndexOf("/*") : -1;
  if (start < 0) throw new Error(`${file}: ast-grep found an empty comment that follows no block comment, at byte ${offset}.`);
  return { file, line: before.slice(0, start).split("\n").length, text: before.slice(start) };
}

export function scan(root: string, files: string[]): Comment[] {
  const parsed = files.filter((file) => languageOf(file) !== undefined);
  const found: Comment[] = [];
  if (parsed.length > 0) {
    const out = execFileSync("ast-grep", ["scan", "--inline-rules", rules(), "--json=stream", ...parsed], {
      cwd: root,
      encoding: "utf8",
      maxBuffer: 1 << 28,
    });
    for (const line of out.split("\n").filter(Boolean)) {
      const match = JSON.parse(line) as { file: string; text: string; range: { start: { line: number }; byteOffset: { start: number } } };
      const text = match.text.trim();
      if (text === "") {
        found.push(droppedBefore(root, match.file, match.range.byteOffset.start));
        continue;
      }
      if (match.range.start.line === 0 && text.startsWith("#!")) continue;
      found.push({ file: match.file, line: match.range.start.line + 1, text });
    }
  }
  for (const file of files) {
    const comment = byLine[file.slice(file.lastIndexOf(".") + 1)];
    if (comment === undefined) continue;
    readFileSync(join(root, file), "utf8")
      .split("\n")
      .forEach((text, index) => {
        const match = comment.exec(text);
        if (match !== null) found.push({ file, line: index + 1, text: match[0].trim() });
      });
  }
  return found.filter((comment) => !approved.some((marker) => marker.test(comment.text)));
}

export const entry = (comment: Comment) => `${comment.file}: ${comment.text.replace(/\s*\n\s*/g, " ")}`;

export function compare(found: Comment[], listed: string[]) {
  const left = new Map<string, number>();
  for (const line of listed) left.set(line, (left.get(line) ?? 0) + 1);
  const added: Comment[] = [];
  for (const comment of found) {
    const count = left.get(entry(comment)) ?? 0;
    if (count > 0) left.set(entry(comment), count - 1);
    else added.push(comment);
  }
  const gone = [...left].flatMap(([line, count]) => Array<string>(count).fill(line));
  return { added, gone };
}

function main() {
  const root = execFileSync("git", ["rev-parse", "--show-toplevel"], { encoding: "utf8" }).trim();
  const files = execFileSync("git", ["ls-files", "--cached", "--others", "--exclude-standard", "mod", "backend", "web", "specs", "scripts", ".github", ".githooks", "mise.toml"], { cwd: root, encoding: "utf8" })
    .split("\n")
    .filter((file) => file !== "" && !generated.has(file) && existsSync(join(root, file)));
  const found = scan(root, files);
  const path = join(root, "comments.txt");
  const listed = existsSync(path) ? readFileSync(path, "utf8").split("\n").filter(Boolean) : [];
  const { added, gone } = compare(found, listed);
  let failed = false;
  if (added.length > 0) {
    for (const comment of added) {
      console.log(`${comment.file}:${comment.line}: delete this comment. Say it with a name, a type or a test, or fix what it excuses. A comment the user agreed to keep starts with "@keep ", as in "// @keep <what no code can say>".`);
    }
    failed = true;
  }
  if (gone.length > 0) {
    const kept = found.filter((comment) => !added.includes(comment)).map(entry).sort();
    writeFileSync(path, kept.length > 0 ? `${kept.join("\n")}\n` : "");
    console.log(`comments.txt listed ${gone.length} comments that are gone. It is written again, so stage it.`);
    failed = true;
  }
  process.exit(failed ? 1 : 0);
}

if (import.meta.main) main();
