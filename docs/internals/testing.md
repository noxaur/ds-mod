# Testing and CI

Why the checks are shaped the way they are. Running them is in [operations/development.md](../operations/development.md#checks), and CI and releases in [operations/release.md](../operations/release.md).

## A feature is tested by replaying a real capture

Every feature has a fixture cut from a capture, replayed through the same game logic the player runs into a transcript. A test that built its own events was rejected, because it would test the author's idea of a closed server that changes without notice. The run that writes a transcript fails once, so that someone reads it before keeping it. A feature that reads nothing from the server has a game test instead.

## CI measures the mod in a real game

Fast and smooth is checked, not promised. A perf test boots the jars a player installs and reads the mod's own timers, so the numbers are the ones play produces. A unit benchmark was rejected, because it would miss the draw cost, which exists only inside Minecraft's renderer. Every time is scaled to a reference device, and each cap gates the median of twelve windows, because one window on a shared runner can be several times slower.

## A pull request runs the game tests its change reaches

The game takes long to boot and each test makes a fresh world, so the whole suite runs before a beta, and a pull request runs only the tests its change reaches, found by walking names in the source. An agent naming the tests was rejected, because the instruction was skipped or answered wrong. Recording which classes each test runs was rejected, because the central classes run in every test.

## A game test runs behind the developer

A developer runs the game tests beside their own game. So the test game opens without the keyboard, under a window class of its own, and writes its sound to a file. Setup in the developer's own config was rejected, because the core works with no setup.

## Each game test starts the mod afresh

Every test in a run shares one game, because a boot per test would multiply the run's time. So before each test the mod starts again as a launch with no saved files would. A test that cleans up after itself was rejected, because the next leak would be found the same way: a test that passed in the full order failing in a picked one.

## CI's time limit is a step, not a timeout

A job's timeout cancels it and says only that it was cancelled, and it can't tell a slow change from a cold cache. So the last step of every job reads when the job started and fails with the time and what to do, and lets a run through when the cache was cold. A build cache for compiled classes was rejected, because most changes edit what every compile reads.

## A warm job runs offline

A job with a warm cache runs Gradle offline. Online, the build fetches Minecraft's version files again whenever Mojang changed them, which slowed every run until the next version bump. Offline, a cache that lacks a file fails the build instead of quietly downloading it.
