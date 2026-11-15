# web

The website, in Next.js with Effect. Read the root `AGENTS.md` first.

- Model work that can fail as an `Effect`, and run it at the edge: a server component, route handler or event handler calls `Effect.runPromise`.
- Before using ui-sdk, follow the ui-sdk skill if it's installed, and read `node_modules/@ui-sdk/react/docs/llms.txt`. To build a new component, also search `node_modules/@ui-sdk/react/docs/core.md` by heading, and read `rendering.md` next to it.
- Use pnpm. `mise run check:web` runs the format check, `tsc` and ESLint with zero warnings allowed. Fix the code, not the config.
- Vite+ formats the code (`vp fmt`). Next.js builds the site and ESLint lints it, so do not turn on `vp check` or `vp lint`, which would add a second linter with other rules.

<!-- BEGIN:nextjs-agent-rules -->

## This is NOT the Next.js you know

This version has breaking changes — APIs, conventions, and file structure may all differ from your training data. Read the relevant guide in `node_modules/next/dist/docs/` (resolved from this file's directory; in monorepos the `next` package may not be visible from the repo root) before writing any code. Heed deprecation notices.

This block is written and re-added by `next dev` — verify at `node_modules/next/dist/server/lib/generate-agent-files.js`. Removing it from a diff only re-creates the uncommitted change; committing it with your work keeps the tree clean.

<!-- END:nextjs-agent-rules -->
