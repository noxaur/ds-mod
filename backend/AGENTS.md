# backend

The REST service, in TypeScript with Effect. Void builds it and deploys it to Cloudflare Workers. Read the root `AGENTS.md` first, and [internals/backend.md](../docs/internals/backend.md) before you change `src/` or `routes/`.

- Void's docs for the installed version are in `node_modules/void/docs/`, and Vite+'s in `node_modules/vite-plus/docs/`. Read them before guessing an API, because both are newer than what you remember.
- A new `Store` method gets a case in `test/contract.ts`, the one suite that both stores run.
- Use pnpm, and ask the user before adding a dependency.
- `vite` is an alias of the Vite+ core, in `package.json` and in the overrides of `pnpm-workspace.yaml`, so that Void and Vite+ share one Vite. Keep both equal to `vite-plus`. When they differ, `vp check` fails with "Excessive stack depth" in `vite.config.ts`.
