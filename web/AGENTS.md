# web

The website, in Next.js with Effect. Read the root `AGENTS.md` first.

- Model work that can fail as an `Effect`, and run it at the edge: a server component, route handler or event handler calls `Effect.runPromise`.
- Before using ui-sdk, follow the `ui-sdk` skill in `.agents/skills/` (`.claude/skills/` links to it). It is a link into `node_modules/@ui-sdk/core`, so run `pnpm install` first, and it always matches the installed version. Its docs index is `node_modules/@ui-sdk/react/docs/llms.txt`.
- Use pnpm. `mise run check:web` runs `tsc` and ESLint with zero warnings allowed. Fix the code, not the config.
