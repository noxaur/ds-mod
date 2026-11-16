# CI and releases

What runs on a pull request, on `main`, and before a beta reaches players. Why CI is shaped this way is in [internals/testing.md](../internals/testing.md).

## CI

`.github/workflows/ci.yml` runs on every pull request and on `main`. It runs each `check:` task in its own job, and a `perf` job that boots the built jars and runs `PerfTest`. That job posts the table as a comment on the pull request, and fails when a row is over its cap in `mod/src/gametest/resources/perf-budgets.json`. Raise a cap only when the user agrees.

Each job fails when it takes over 3 minutes. A run that had to download its dependencies, after a version bump or a change to `build.gradle.kts` or `mise.toml`, is let through and says so. When a job nears its 3 minutes, make it faster or move the slow part to the beta pipeline.

## Beta releases

`.github/workflows/beta.yml` runs every second day at 03:00 UTC. When `main` has a commit since the last release, it runs the mod's checks and every game test, and then publishes the release jar as the prerelease `v<version>-beta.<run number>`, built against the deployed backend and website. When anything fails, nothing is published, and the run's summary shows the perf table.

It does not run the backend, web or docs checks, and it does not wait for CI on the same commit. A commit on `main` whose CI failed can therefore still ship, as long as the mod's own checks pass.

To release a beta now, run it by hand:

```sh
gh workflow run beta
```
