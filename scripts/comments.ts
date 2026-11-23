import { execFileSync } from "node:child_process";
import { copyFileSync, existsSync, mkdtempSync, readFileSync, rmSync } from "node:fs";
import { tmpdir } from "node:os";
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

type Syntax = { line: string; block?: [string, string]; strings: string[]; escapes: string };

const tokenized: Record<string, Syntax> = {
  tla: { line: "\\*", block: ["(*", "*)"], strings: ['"'], escapes: '"' },
  toml: { line: "#", strings: ['"""', "'''", '"', "'"], escapes: '"' },
};

const lineStarts: Record<string, RegExp> = {
  properties: /^\s*[#!].*/,
};

const generated = new Set(["mod/gradlew", "mod/gradlew.bat", "web/next-env.d.ts"]);

const approved = [
  /^\/\/ rule:[a-z-]+ \S/,
  /^\/\/ eslint-disable-next-line [\w/@-]+(, [\w/@-]+)* -- \S/,
  /^(\/\/|\/\*\*?|#|\\\*|\(\*)\s*@keep \S/,
];

export type Comment = { file: string; line: number; text: string };

const extensionOf = (file: string) => file.slice(file.lastIndexOf(".") + 1);

const isHook = (file: string) => file.startsWith(".githooks/");

const languageOf = (file: string) => (isHook(file) ? "bash" : byExtension[extensionOf(file)]);

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

function tokenize(file: string, text: string, syntax: Syntax): Comment[] {
  const found: Comment[] = [];
  const lineAt = (at: number) => text.slice(0, at).split("\n").length;
  let at = 0;
  while (at < text.length) {
    const quote = syntax.strings.find((delimiter) => text.startsWith(delimiter, at));
    if (quote !== undefined) {
      at += quote.length;
      while (at < text.length && !text.startsWith(quote, at)) at += text[at] === "\\" && syntax.escapes.includes(quote[0]) ? 2 : 1;
      at += quote.length;
    } else if (text.startsWith(syntax.line, at)) {
      const end = text.indexOf("\n", at) < 0 ? text.length : text.indexOf("\n", at);
      found.push({ file, line: lineAt(at), text: text.slice(at, end).trim() });
      at = end;
    } else if (syntax.block !== undefined && text.startsWith(syntax.block[0], at)) {
      const [open, close] = syntax.block;
      let end = at;
      for (let depth = 0; end < text.length; ) {
        if (text.startsWith(open, end)) {
          depth += 1;
          end += open.length;
        } else if (text.startsWith(close, end)) {
          depth -= 1;
          end += close.length;
          if (depth === 0) break;
        } else end += 1;
      }
      found.push({ file, line: lineAt(at), text: text.slice(at, end).trim() });
      at = end;
    } else at += 1;
  }
  return found;
}

function parse(root: string, files: string[]): Comment[] {
  if (files.length === 0) return [];
  const copies = mkdtempSync(join(tmpdir(), "comments-hooks-"));
  const hooks = new Map(
    files.filter(isHook).map((file, index) => {
      const copy = join(copies, `${index}.sh`);
      copyFileSync(join(root, file), copy);
      return [copy, file];
    }),
  );
  const out = execFileSync("ast-grep", ["scan", "--inline-rules", rules(), "--json=stream", ...files.filter((file) => !isHook(file)), ...hooks.keys()], {
    cwd: root,
    encoding: "utf8",
    maxBuffer: 1 << 28,
  });
  rmSync(copies, { recursive: true });
  return out
    .split("\n")
    .filter(Boolean)
    .flatMap((line) => {
      const match = JSON.parse(line) as { file: string; text: string; range: { start: { line: number }; byteOffset: { start: number } } };
      const file = hooks.get(match.file) ?? match.file;
      const text = match.text.trim();
      if (text === "") return [droppedBefore(root, file, match.range.byteOffset.start)];
      if (match.range.start.line === 0 && text.startsWith("#!")) return [];
      return [{ file, line: match.range.start.line + 1, text }];
    });
}

export function scan(root: string, files: string[]): Comment[] {
  const found = parse(root, files.filter((file) => languageOf(file) !== undefined));
  for (const file of files) {
    const syntax = tokenized[extensionOf(file)];
    if (syntax !== undefined) found.push(...tokenize(file, readFileSync(join(root, file), "utf8"), syntax));
    const start = lineStarts[extensionOf(file)];
    if (start === undefined) continue;
    readFileSync(join(root, file), "utf8")
      .split("\n")
      .forEach((text, index) => {
        const match = start.exec(text);
        if (match !== null) found.push({ file, line: index + 1, text: match[0].trim() });
      });
  }
  return found.filter((comment) => !approved.some((marker) => marker.test(comment.text)));
}

export const entry = (comment: Comment) => `${comment.file}: ${comment.text.replace(/\s*\n\s*/g, " ")}`;

function main() {
  const root = execFileSync("git", ["rev-parse", "--show-toplevel"], { encoding: "utf8" }).trim();
  const files = execFileSync("git", ["ls-files", "--cached", "--others", "--exclude-standard", "mod", "backend", "web", "specs", "scripts", ".github", ".githooks", "mise.toml"], {
    cwd: root,
    encoding: "utf8",
  })
    .split("\n")
    .filter((file) => file !== "" && !generated.has(file) && existsSync(join(root, file)));
  const found = scan(root, files);
  for (const comment of found) {
    console.log(`${comment.file}:${comment.line}: delete this comment. Say it with a name, a type or a test, or fix what it excuses. Only a fact no code can show, such as how the server behaves or a number someone measured, earns "// @keep <that fact>", and a person then reviews each one the pull request adds.`);
  }
  process.exit(found.length > 0 ? 1 : 0);
}

if (import.meta.main) main();
