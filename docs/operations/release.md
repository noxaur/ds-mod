# CI and releases

What runs on a pull request, on `main`, and before a beta reaches players. Why CI is shaped this way is in [internals/testing.md](../internals/testing.md).

## CI

`.github/workflows/ci.yml` runs on every pull request and on `main`. It runs each `check:` task in its own job, with the mod's lint (`./gradlew lintCheck`) in a `lint` job of its own, and `gametests` jobs that run the game tests the change reaches, split by a `pick` job into jobs that run side by side. `PerfTest` runs only in the beta pipeline.

A pull request that adds a `@Suppress` or an `eslint-disable` gets the `human review needed` label from `.github/workflows/label.yml`, whose `suppressions` job fails until the body names each one's `file:line` and says why it is there.

Each job warns when it takes over 3 minutes, unless it had to download its dependencies, after a version bump in `gradle.properties` or `mise.toml`, or a new Gradle wrapper. When a job warns, make it faster or move the slow part to the beta pipeline.

## Beta releases

`.github/workflows/beta.yml` runs every second day at 03:00 UTC. When `main` has a commit since the last release, and CI passed on that commit, it runs the mod's checks and every game test, and then publishes the release jar as the prerelease `v<version>-beta.<run number>`, built against the deployed backend and website. When anything fails, nothing is published. The game tests include `PerfTest`, which fails when a row is over its cap in `mod/src/gametest/resources/perf-budgets.json`, and the run's summary shows its table. Raise a cap only when the user agrees.

When CI on that commit is still running, failed or never ran, the run stops before it builds anything. Start it again once CI has passed.

To release a beta now, run it by hand:

```sh
gh workflow run beta
```
