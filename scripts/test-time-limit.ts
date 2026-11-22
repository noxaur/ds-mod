const limitMs = 30_000;

export default function setup(project: { readonly config: { readonly watch: boolean } }): (() => void) | undefined {
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
