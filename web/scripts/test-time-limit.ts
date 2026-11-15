import type { TestProject } from "vite-plus/test/node";

const limitMs = 30_000;

// Vitest bounds each test but not a whole run, so this global setup ends the run once it passes the limit.
// It runs in the main process, which stays free while the tests run in workers, so a test stuck in a loop cannot stop it.
export default function setup(project: TestProject): (() => void) | undefined {
  if (project.config.watch) {
    return undefined;
  }
  const timer = setTimeout(() => {
    process.stderr.write(`The unit tests ran past ${String(limitMs / 1000)} seconds. Make the slow test faster.\n`);
    process.exit(1);
  }, limitMs);
  timer.unref();
  return () => {
    clearTimeout(timer);
  };
}
