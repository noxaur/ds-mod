# Deployment

The backend runs on the user's Cloudflare account and the website on the user's Vercel account. The mod's jar bakes in both addresses, so a player has nothing to set.

| Part | Address | Where | How it deploys |
| --- | --- | --- | --- |
| Backend | `https://api.ds-mod.perwko.com` | Worker `dsmod-backend` on Cloudflare, with its D1 database and R2 bucket | `.github/workflows/deploy.yml`, once `ci` passes on a push to `main` |
| Website | `https://ds-mod.perwko.com` | Vercel project `ds-mod`, root directory `web/` | On every push to `main`, through Vercel's GitHub app, when the push changes `web/`. No other branch or pull request deploys, so previews never spend the daily deployment limit |
| Mod | Both, baked into the jar | `-Pbackend_url` and `-Pwebsite_url` in `.github/workflows/beta.yml` | With each beta release |

The addresses are fixed: a jar keeps the ones it was built with, so moving either one strands every player on an older release.

`perwko.com`'s DNS is on Cloudflare. The backend's record belongs to the Worker's custom domain in `backend/void.config.ts`, which Cloudflare manages. The website's is a CNAME `ds-mod` to the target Vercel names under the project's domains, with the proxy off, so that Vercel can issue its certificate.

## Deploy the backend

The `deploy` workflow runs `void deploy --platform cloudflare` after each green `ci` run on `main`, with the repository secret `CLOUDFLARE_API_TOKEN`. That token is scoped to the user's account and to `perwko.com`, with Workers Scripts, Workers KV Storage, D1 and R2 edit, Workers Routes edit, and account settings and zone read. The flag is needed because the saved target lives in the git-ignored `backend/.void/`, so a fresh checkout has none.

To deploy by hand from a checkout that the user has signed in to Cloudflare:

```sh
cd backend
pnpm exec void deploy
```

Deploy applies pending migrations from `db/migrations/` before it uploads the Worker, after it saves a D1 restore point and prints the `void db restore` command for it. A deploy that fails after migrating leaves the new schema under the old Worker. Usually the fix is a new commit or a rerun of the workflow. Restore only when the old Worker cannot run against the new schema, because a restore discards every write since the restore point. Commit `void.lock.json` whenever it changes. It holds the account and the resource IDs. `workers_dev` and `preview_urls` in `void.config.ts` must match the live Worker, or a deploy stops before it uploads.

Without `CLOUDFLARE_API_TOKEN`, Void's Cloudflare commands use the browser sign-in and refuse to run without a terminal. In an agent's shell, run them under `script -qfec "<command>" /dev/null`. `void cloudflare login` prints a code that the user confirms in the browser. Its session sits in the system keychain and can deploy, set secrets and manage the Worker's domain, but not edit DNS records.

After a deploy, `https://api.ds-mod.perwko.com/healthz` answers 204, and a `POST` to `/api/auth/minecraft/challenge` answers 200 with a `serverId`, which proves the database is reachable.

## Alerts

The `alert` workflow posts to the user's Discord channel when `ci`, `deploy` or `beta` fails on `main`, or when a Vercel deploy fails. It posts through the webhook in the repository secret `DISCORD_WEBHOOK_URL`. To change the channel, make a new webhook in Discord and replace the secret:

```sh
gh secret set DISCORD_WEBHOOK_URL
```

## Secrets

| Name | What it is | Where it lives |
| --- | --- | --- |
| `BETTER_AUTH_SECRET` | Signs every session | Created by Void on the first deploy. Changing it signs every player out |
| `ADMIN_TOKEN` | What `dsctl` sends to act as an agent | The Worker, and the user's `~/.config/dsmod/production.env`, readable only by the user |
| `AUTH_TRUSTED_KEY` | Lets the website's sign-in calls skip the per-address rate limit, which would otherwise count every player who signs in on the website together. The website's `AUTH_TRUSTED_KEY` in Vercel must hold the same value | The Worker, and the Vercel project's environment variables |
| `MOJANG_RELAY_URL` | `https://ds-mod.perwko.com/mojang/has-joined`, where the backend asks Mojang who joined, because Mojang refuses Cloudflare's addresses. Without it the backend asks Mojang itself, and every sign-in from the mod answers 503 | The Worker |
| `MOJANG_RELAY_KEY` | What the backend sends the relay, which answers nobody else. The website's `MOJANG_RELAY_KEY` in Vercel must hold the same value | The Worker, and the Vercel project's environment variables |

To change `ADMIN_TOKEN`, edit the file and pipe the value in. `void secret sync` waits on a prompt that a non-interactive shell never answers.

```sh
sed -n 's/^ADMIN_TOKEN=//p' ~/.config/dsmod/production.env | tr -d '\n' | pnpm exec void secret put ADMIN_TOKEN
```

To change `MOJANG_RELAY_KEY`, set the website's and redeploy it, check that the live relay opens to the new key, and only then set the Worker's. A sign-in in between answers 503, because the old key no longer opens the relay. The check asks about a server id nobody joined, which Mojang answers 204 and a refused key 404. In `backend/`, with the user signed in to Vercel:

```sh
key=$(openssl rand -hex 32)
printf %s "$key" | pnpm dlx vercel env add MOJANG_RELAY_KEY --environment production --sensitive --force --project ds-mod &&
  pnpm dlx vercel redeploy ds-mod.perwko.com &&
  printf 'x-mojang-relay-key: %s' "$key" |
  curl -sS -o /dev/null -w '%{http_code}' -H @- "https://ds-mod.perwko.com/mojang/has-joined?username=Notch&serverId=$(printf '0%.0s' $(seq 40))" |
  grep -qx 204 &&
  printf %s "$key" | pnpm exec void secret put MOJANG_RELAY_KEY
unset key
```

`dsctl` reaches the deployment with `DSMOD_BACKEND=https://api.ds-mod.perwko.com` and `DSMOD_ADMIN_TOKEN` read from that file.

## The website

`web/vercel.json` overrides the project's build settings in Vercel.

Vercel reads `packageManager` only from a `package.json` at the repository's root, and this repository has none, so without the install command it installs with an old pnpm that ignores the lockfile and fails. `corepack enable` run inside `web/` picks the pinned pnpm from `web/package.json` instead.

`git.deploymentEnabled` in `web/vercel.json` lets only `main` deploy. A branch Vercel never deploys spends nothing, unlike a skipped build. To see a branch on Vercel before it merges, deploy it by hand with `pnpm dlx vercel deploy` from the repository root, because the project's root directory is `web/` and the CLI resolves it from where it runs.

`web/scripts/vercel-ignore.sh` skips a build of `main` when nothing in `web/` changed since the last built commit, which Vercel gives as `VERCEL_GIT_PREVIOUS_SHA`. Vercel clones ten commits deep, so the script fetches an older base by its SHA, which works because the repository is public. Whatever it cannot judge, it builds, including the first build, which has no previous commit. A skipped build shows as canceled in Vercel and still counts toward the deployment quota. To build a skipped commit, redeploy it in Vercel with the project's Ignore Build Step unchecked.

The project's settings in Vercel hold `BACKEND_URL=https://api.ds-mod.perwko.com`, `AUTH_TRUSTED_KEY`, `MOJANG_RELAY_KEY` and Node 24. A change to any of them is made in Vercel and needs a redeploy.
