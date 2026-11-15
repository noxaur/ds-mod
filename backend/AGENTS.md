# backend

The REST service, in TypeScript with Effect. Void builds it and deploys it to Cloudflare Workers. Read the root `AGENTS.md` first.

- `src/api.ts` defines every endpoint with Effect's `HttpApi`. After changing it, run `pnpm openapi` and commit `../api/openapi.json`.
- `src/` holds the API and its handlers as Effect layers. `routes/[...path].ts` only hands each request to them, so nothing in `src/` imports from `void` except the services that wrap a Cloudflare resource.
- Model work that can fail as an `Effect`. `dsmod/no-try` rejects `try`.
- Void's docs for the installed version are in `node_modules/void/docs/`, and Vite+'s in `node_modules/vite-plus/docs/`. Read them before guessing an API, because both are newer than what you remember.
- Use pnpm, and ask the user before adding a dependency. `vite` is an alias of the Vite+ core in `package.json`, so that Void and Vite+ share one Vite. Keep its version equal to `vite-plus`.
- `mise run check:backend` runs `vp check` (format, lint and types) and `vp test`.
- `pnpm dev` binds `127.0.0.1` on purpose. Vite alone listens on IPv6 `localhost` only, and Minecraft runs Java with IPv4 preferred, so the mod could not reach it.
