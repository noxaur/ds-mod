# backend

The REST service, in TypeScript with Effect. Void builds it and deploys it to Cloudflare Workers. Read the root `AGENTS.md` first.

- `src/api.ts` defines every endpoint but sign-in with Effect's `HttpApi`. After changing it, run `pnpm openapi` and commit `../api/openapi.json`.
- `src/` holds the API and its handlers as Effect layers, and runs without Void, so a test calls it directly. `routes/[...].ts` only hands each request to it.
- Sign-in is Better Auth's, configured in `src/identity/options.ts` and served by Void at `/api/auth/*`.
- Model work that can fail as an `Effect`. `dsmod/no-try` rejects `try`.
- Void's docs for the installed version are in `node_modules/void/docs/`, and Vite+'s in `node_modules/vite-plus/docs/`. Read them before guessing an API, because both are newer than what you remember.
- Use pnpm, and ask the user before adding a dependency. `vite` is an alias of the Vite+ core, in `package.json` and in the overrides of `pnpm-workspace.yaml`, so that Void and Vite+ share one Vite. Keep both equal to `vite-plus`. When they differ, `vp check` fails with "Excessive stack depth" in `vite.config.ts`.
- `mise run check:backend` runs `vp check` (format, lint and types) and `vp test`.
- `pnpm dev` binds `127.0.0.1` on purpose. Vite alone listens on IPv6 `localhost` only, and Minecraft runs Java with IPv4 preferred, so the mod could not reach it.
