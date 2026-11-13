# web

The website, in Next.js with Effect. Read the root `AGENTS.md` first.

- Model work that can fail as an `Effect`, and run it at the edge: a server component, route handler or event handler calls `Effect.runPromise`.
- Before using ui-sdk, follow the ui-sdk skill if it's installed, and read `node_modules/@ui-sdk/react/docs/llms.txt`. To build a new component, also search `node_modules/@ui-sdk/react/docs/core.md` by heading, and read `rendering.md` next to it.
- Use pnpm. `mise run check:web` runs `tsc` and ESLint with zero warnings allowed. Fix the code, not the config.
