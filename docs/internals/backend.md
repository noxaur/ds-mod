# Backend and website

Where the backend and the website run, and the boundaries between them. Deploying them is in [operations/deployment.md](../operations/deployment.md).

## The backend is an Effect API inside a Void app

Void deploys the backend to Cloudflare Workers and owns the database, the file store and the sign-in tables, and the API itself is defined and handled with Effect. The API takes its store and its caller as arguments and imports nothing from Void, so a move off Cloudflare needs a new entry point, a store and a sign-in, and no change to a rule. Sign-in is the exception that Void serves, because Better Auth keeps the sessions every other route checks.

## One file touches the database and the file store

Void binds the database only to a file that imports it, and the API must stay free of Void. So one file outside the API reads and writes storage, behind a store interface each domain declares, and a rule that need not be atomic is a function in the API, so a new store keeps it. Tests run that file itself, over the real migrations. An in-memory twin with a contract suite to keep it honest was rejected, because it wrote the SQL's caps, its one run per player and its ordering a second time in JavaScript.

## The database runs in tests as SQLite with the real migrations

Tests run the real migrations on Node's built-in SQLite behind a small stand-in for Cloudflare's database binding, which keeps its all-or-nothing batch and its bound-value limit. Miniflare was rejected: Void depends on it only indirectly, so using it would add a direct dependency, and a worker process per run would cost seconds of the 30-second test budget.

## A caller is decided in one place

The entry route says who is calling, a signed-in player or an agent, and puts it where no client can write. A group of endpoints that needs a caller declares it, so the contract shows it and no handler can forget the check. The agent's routes fail closed when the admin token is unset.

## Only a failure the client can fix is in the contract

A failure no client can fix, such as a database that is down, is logged and answered with an empty 500. Only what the client got wrong is an error the endpoint declares, so the contract never lists a failure the client could not act on.

## The website runs on Vercel, beside the backend

Void deploys no Next.js app, so the website runs on Vercel and calls the backend from its own servers. Nothing in a browser talks to the backend, which keeps the session cookie on the website's domain and leaves the backend no other origin to trust. Reading the player's address from a header was rejected, because any client can send one.

## The dev server binds IPv4, on a port of its own

The dev server binds IPv4 on purpose, because Vite alone listens on IPv6 and Minecraft runs Java with IPv4 preferred, so the mod could not reach it. It uses a port of its own, because Wrangler's default is often held by another project's dev server, and the dev jar would then sign in to that one.
