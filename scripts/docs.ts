import { readdirSync, readFileSync } from "node:fs";
import { join } from "node:path";

const recipe = /^#+ +(add|how to|write|run|check|verify|test)s?( |$)/i;

export function check(docs: string): string[] {
  const read = (page: string) => readFileSync(join(docs, page), "utf8").split(/\r?\n/);
  const pages = (folder: string, deep = false) =>
    readdirSync(join(docs, folder), { recursive: deep, withFileTypes: true })
      .filter((entry) => entry.isFile() && entry.name.endsWith(".md"))
      .map((entry) => join(entry.parentPath, entry.name).slice(docs.length + 1).replaceAll("\\", "/"))
      .sort();
  const matches = (page: string, test: (line: string) => boolean) =>
    read(page).flatMap((line, index) => (test(line) ? [`docs/${page}:${index + 1}:${line}`] : []));
  const failures: string[] = [];
  const readme = readFileSync(join(docs, "README.md"), "utf8");
  for (const page of ["user", "internals", "operations"].flatMap((folder) => pages(folder, true))) {
    if (!readme.includes(`(${page})`)) failures.push(`docs/README.md:1: link ${page} here with one line on what it covers`);
  }
  const internals = pages("internals");
  const recipes = internals.flatMap((page) => matches(page, (line) => recipe.test(line)));
  if (recipes.length > 0) failures.push(...recipes, "docs/internals records why, not steps. Move the recipe to docs/operations/.");
  const decisions = internals.filter((page) => page !== "internals/glossary.md");
  const code = decisions.flatMap((page) => matches(page, (line) => line.includes("`")));
  if (code.length > 0) {
    failures.push(...code, "docs/internals records decisions in the domain's words, so it names no code. Say what was decided and why, and let the source show how.");
  }
  for (const page of decisions) {
    let head = 1;
    let length = 0;
    const out = () => {
      if (length > 600) {
        failures.push(
          `docs/${page}:${head}: cut this section to one paragraph of at most 600 characters: the decision, the constraint that decided it and the alternative it rejected. A limitation is an issue, not a section.`,
        );
      }
    };
    read(page).forEach((line, index) => {
      if (!line.startsWith("#")) {
        length += line.length;
        return;
      }
      out();
      head = index + 1;
      length = 0;
    });
    out();
  }
  const yours = pages("operations").flatMap((page) => matches(page, (line) => /\byour\b/i.test(line)));
  if (yours.length > 0) failures.push(...yours, `docs/operations is read by an agent. The machine, desktop and accounts are the user's, so write "the user's".`);
  return failures;
}

if (import.meta.main) {
  const failures = check(join(import.meta.dirname, "..", "docs"));
  for (const failure of failures) console.log(failure);
  process.exit(failures.length > 0 ? 1 : 0);
}
