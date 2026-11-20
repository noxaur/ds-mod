# CI and releases

What runs on a pull request, on `main`, and before a beta reaches players. Why CI is shaped this way is in [internals/testing.md](../internals/testing.md).

## CI

`.github/workflows/ci.yml` runs on every pull request and on `main`. It runs each `check:` task in its own job, a `gametests` job that runs the game tests the change reaches, and a `perf` job that boots the built jars and runs `PerfTest`. That job posts the table as a comment on the pull request, and fails when a row is over its cap in `mod/src/gametest/resources/perf-budgets.json`. Raise a cap only when the user agrees.

Each job fails when it takes over 3 minutes. A run that had to download its dependencies, after a version bump in `gradle.properties` or `mise.toml`, or a new Gradle wrapper, is let through and says so. When a job nears its 3 minutes, make it faster or move the slow part to the beta pipeline.

## Beta releases

`.github/workflows/beta.yml` runs every second day at 03:00 UTC. When `main` has a commit since the last release, and CI passed on that commit, it runs the mod's checks and every game test, and then publishes the release jar as the prerelease `v<version>-beta.<run number>`, built against the deployed backend and website. When anything fails, nothing is published, and the run's summary shows the perf table.

When CI on that commit is still running, failed or never ran, the run stops before it builds anything. Start it again once CI has passed.

To release a beta now, run it by hand:

```sh
gh workflow run beta
```
