# Testing and CI

Why the checks are shaped the way they are. Running them is in [operations/development.md](../operations/development.md#checks), and CI and releases in [operations/release.md](../operations/release.md).

## A feature is tested by replaying a real capture

Every feature has a fixture cut from a capture, and `FeatureReplayTest` replays it through the same `Game` the player runs and writes a transcript. A test that built its own events would test the author's idea of the server, and the server is closed and changes without notice. The run that writes a transcript fails once, so that someone reads it before keeping it.

The same test replays each fixture outside the feature's area, to prove the area lock, and averages each feature's handler and readout time, which must stay under 20 µs. A feature that reads nothing from the server has no fixture, so the test requires a game test named after its class instead.

## CI measures the mod in a real game

"Fast and smooth" is checked, not promised. `PerfTest` boots the jars a player installs and reads the mod's own `Metrics` timers, so the numbers are the ones a capture records in play. A unit benchmark would miss the draw cost, which exists only inside Minecraft's renderer.

Runners differ in speed, and one runner differs from run to run. `PerfTest` times a fixed loop on the machine it runs on and scales every time to the device named in `perf-budgets.json`, so a cap means the same on a laptop and on a runner. Each cap gates the median of twelve windows, because one window on a shared runner can be several times slower than the rest.

The game takes about 40 seconds to boot on a runner, where it renders in software. So CI runs `PerfTest` alone, and the other game tests run before a beta release. A broken screen can therefore reach `main`, and it is caught within two days, before any player gets it.

A beta ships only when CI passed on its commit. The backend, web and docs checks run only in CI, and a beta that ran them again would spend its time installing Node packages for a release that holds none of them.

## A game test runs behind the developer

Agents run the game tests while a person works on the same machine, often with their own game open. So the test game opens without the keyboard, under the window class `dsmod-gametest`, and writes its sound to a file. All of it lives in the `gametest` source set, so `runClient` and the release jar are untouched.

The window class is what lets a window manager tell the test from the game the developer plays, which has the same title. On Hyprland the test installs its own named window rule through `hyprctl` and needs nothing in the developer's config. A rule in a dotfile would be one more setup step, and the core works with no setup.

Both settings must be made from inside the game, just before it creates its window. A GLFW window hint lasts only until the next window, and Minecraft resets the hints first, so a launch flag can't carry it. OpenAL reads `ALSOFT_CONF` from the process environment when the first device opens, which a Java system property can't set, and Loom's production run task takes no environment. `wave` is the only driver listed, so when it fails the game is silent instead of falling back to the speakers.

The window has no keyboard focus, yet the game believes it has. Fabric marks it focused as the run starts and drops the window's own focus events, and it cancels the cursor grab, so a focused test game never takes the developer's mouse. The game hands a screen its mouse moves only while focused, so a drag in a test needs that focus, and the harness gives it back before each test in case one took it away.

## A commit runs the game tests its change reaches

Agents run game tests while they work, often several agents at once on the user's machine, and each one waits for its run to end. A run boots the game, which takes about 25 seconds, and each test then makes a fresh world, because Fabric ends every test on the title screen. The whole suite takes minutes, so the beta pipeline runs it, and a commit runs only the tests its change reaches. An agent once had to choose and name them, and an instruction to do so was skipped or answered with the wrong tests.

The pick walks names in the source: a changed file reaches the tests that name it, or that name a file that names it. Recording which classes each test runs was rejected, because the mod runs in the tick and render loops, so its central classes run in every test, and a mixin's code is merged into Minecraft's classes, out of a recorder's sight. A file most tests reach is a hub that a walk does not cross, and a change to it runs one smoke test. The pick diffs against the last green run, so a failed run is retried by the next commit, and with none it diffs against `main`.

`TestClock` fails a run when one test passes its cap, because a test that waits a fixed number of ticks costs every agent that runs it. The cap has room for a machine busy with other runs. `PerfTest` is exempt, because it measures over a fixed number of windows.

## Each game test starts the mod afresh

Every test in a run shares one game, because a boot per test would multiply the run's time. Fabric restores the game's options before each test, but the mod's state carried over: a timer one test started still counted down on the HUD of the next, and a test that compares frames to the pixel failed only after it. A run that picks some of the tests changes which test comes before which, so a test that passed in the full order failed in a picked one. So before each test the settings go back to their defaults and the mod starts again as a launch with no saved files would, through `DsModClient.restart`. The settings are reset in place, because the features and the edge's listeners hold their sections. A fresh game per test was rejected for its boot time, and a test that cleans up after itself for leaving the next leak to be found the same way.

## CI's time limit is a step, not a timeout

`timeout-minutes` cancels a job and says only that it was cancelled. It also can't tell a slow change from a cold cache: the first run after a version bump downloads Minecraft and takes about four minutes whatever the change does. The `time-limit` action runs last, reads when the job started, and fails with the time and what to do about it. It lets a run through when the cache was cold.

The cache key is the files that decide what is downloaded: `mise.toml`, the wrapper, and `gradle.properties`, which holds every version the build scripts read. Keying on `build.gradle.kts` as well let every edit to the build through the limit unchecked, though no such edit downloads anything.

The configuration cache is restored too, because Loom takes 15 to 40 seconds to configure the project on a runner, which put the perf job over its limit at random. Gradle checks every file the configuration read and configures again on any change, so the newest entry is safe to restore whatever it was saved from. A build cache for the compiled classes was rejected: most changes edit `src/main`, which every compile reads, so it would speed up only the runs that are already fast.

A job with a warm cache runs Gradle `--offline`. Online, Loom fetches Minecraft's version files again whenever Mojang changed them after the cache was saved, on every run until the next version bump, and the download leaves a file behind that stops the configuration cache matching the next run. Offline, a cache that lacks a file fails the build instead of quietly downloading it.
