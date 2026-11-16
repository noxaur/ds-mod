# Backend and website

Where the backend and the website run, and the boundaries between them. Deploying them is in [operations/deployment.md](../operations/deployment.md).

## The backend is an Effect API inside a Void app

Void deploys the backend to Cloudflare Workers and owns the database, the file store and the sign-in tables. The API itself is defined and handled with Effect, and one catch-all route, `routes/[...].ts`, hands each request to it. Sign-in is the exception: Void serves `/api/auth/*` with Better Auth before any route of ours, because Better Auth keeps the sessions every other route checks. A route that is not about a session belongs in the Effect API and its contract.

The split keeps the way out open. The API takes its store and its caller as arguments, so a move to a single machine needs a new entry point, a new store and a sign-in, and no change to a rule. For the same reason `backend/src/` imports nothing from Void, which `no-restricted-imports` in `backend/vite.config.ts` enforces.

## One file touches D1 and R2

Void provisions a binding only for a file in `routes/` or `src/` that imports it, and `src/` must stay free of Void. So `routes/_store.ts` is the one file that reads and writes the database and the file store, and its `_` keeps it from being a route. Each domain in `src/` keeps its rules behind a `Store` interface and is tested with an in-memory store.

The trap is that `_store.ts` itself runs in no test. A rule that lands there, such as a cap that must be one SQL statement to stay atomic, is checked by nothing, which is why `backend/AGENTS.md` asks to keep it to plain reads and writes.

## A caller is decided in one place

`routes/[...].ts` says who is calling, a signed-in player or an agent with the admin token, and `makeApp` puts it in the request's context as `Caller`, where no client can write. A group that needs a caller takes `SignedIn` or `Agent` as middleware in `src/api.ts`, so the contract shows it and no handler can forget the check. The agent's routes fail closed when `ADMIN_TOKEN` is unset or empty.

A failure no client can fix, such as a database that is down, is a defect: `makeApp` writes its cause to the Worker's log and answers an empty 500. Only what the client got wrong is an `HttpApiError` the endpoint declares, so the contract never lists a failure the client could not act on.

## The website runs on Vercel, beside the backend

Void deploys no Next.js app, so the website runs on Vercel and calls the backend from its own servers. Nothing in a browser talks to the backend, which keeps the session cookie on the website's domain and leaves the backend no other origin to trust.

The cost is that the backend sees the website's few server addresses, not each player's, so Better Auth's per-address rate limit counts every player who logs in on the website together. When website logins fail with 429 under load, give the website a trusted key, as Void's auth guide describes. Reading the player's address from a header would trust a value any client can send.

## The dev server binds IPv4

`pnpm dev` binds `127.0.0.1` on purpose. Vite alone listens on IPv6 `localhost` only, and Minecraft runs Java with IPv4 preferred, so the mod could not reach it.
