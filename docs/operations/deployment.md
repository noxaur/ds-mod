# Deployment

The backend runs on the user's Cloudflare account and the website on the user's Vercel account. The mod's jar bakes in both addresses, so a player has nothing to set.

| Part | Address | Where | How it deploys |
| --- | --- | --- | --- |
| Backend | `https://api.ds-mod.perwko.com` | Worker `dsmod-backend` on Cloudflare, with its D1 database and R2 bucket | `.github/workflows/deploy.yml`, once `ci` passes on a push to `main` |
| Website | `https://ds-mod.perwko.com` | Vercel project `ds-mod`, root directory `web/` | On every push to `main`, through Vercel's GitHub app. Pull requests get a preview |
| Mod | Both, baked into the jar | `-Pbackend_url` and `-Pwebsite_url` in `.github/workflows/beta.yml` | With each beta release |

The addresses are fixed: a jar keeps the ones it was built with, so moving either one strands every player on an older release.

`perwko.com`'s DNS is on Cloudflare. The backend's record belongs to the Worker's custom domain in `backend/void.config.ts`, which Cloudflare manages. The website's is a CNAME `ds-mod` to the target Vercel names under the project's domains, with the proxy off, so that Vercel can issue its certificate.

## Deploy the backend

The `deploy` workflow runs `void deploy --platform cloudflare` after each green `ci` run on `main`, with the repository secret `CLOUDFLARE_API_TOKEN`. That token is scoped to the user's account and to `perwko.com`, with Workers Scripts, D1 and R2 edit, Workers Routes edit, and account settings and zone read. The flag is needed because the saved target lives in the git-ignored `backend/.void/`, so a fresh checkout has none.

To deploy by hand from a checkout that the user has signed in to Cloudflare:

```sh
cd backend
pnpm exec void deploy
```

Deploy applies pending migrations from `db/migrations/` before it uploads the Worker, after it saves a D1 restore point and prints the `void db restore` command for it. A deploy that fails after migrating leaves the new schema under the old Worker. Usually the fix is a new commit or a rerun of the workflow. Restore only when the old Worker cannot run against the new schema, because a restore discards every write since the restore point. Commit `void.lock.json` whenever it changes. It holds the account and the resource IDs. `workers_dev` and `preview_urls` in `void.config.ts` must match the live Worker, or a deploy stops before it uploads.

Without `CLOUDFLARE_API_TOKEN`, Void's Cloudflare commands use the browser sign-in and refuse to run without a terminal. In an agent's shell, run them under `script -qfec "<command>" /dev/null`. `void cloudflare login` prints a code that the user confirms in the browser. Its session sits in the system keychain and can deploy, set secrets and manage the Worker's domain, but not edit DNS records.

After a deploy, `https://api.ds-mod.perwko.com/healthz` answers 204, and a `POST` to `/api/auth/minecraft/challenge` answers 200 with a `serverId`, which proves the database is reachable.

## Secrets

| Name | What it is | Where it lives |
| --- | --- | --- |
| `BETTER_AUTH_SECRET` | Signs every session | Created by Void on the first deploy. Changing it signs every player out |
| `ADMIN_TOKEN` | What `dsctl` sends to act as an agent | The Worker, and the user's `~/.config/dsmod/production.env`, readable only by the user |

To change `ADMIN_TOKEN`, edit the file and pipe the value in. `void secret sync` waits on a prompt that a non-interactive shell never answers.

```sh
sed -n 's/^ADMIN_TOKEN=//p' ~/.config/dsmod/production.env | tr -d '\n' | pnpm exec void secret put ADMIN_TOKEN
```

`dsctl` reaches the deployment with `DSMOD_BACKEND=https://api.ds-mod.perwko.com` and `DSMOD_ADMIN_TOKEN` read from that file.

## The website

`web/vercel.json` sets the install command. Vercel reads `packageManager` only from a `package.json` at the repository's root, and this repository has none, so without the command it installs with an old pnpm that ignores the lockfile and fails. `corepack enable` run inside `web/` picks the pinned pnpm from `web/package.json` instead.

The project's settings in Vercel hold `BACKEND_URL=https://api.ds-mod.perwko.com` and Node 24. A change to either is made in Vercel and needs a redeploy.
