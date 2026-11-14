package io.github.noxaur.dsmod.core.game

/**
 * Where a feature runs. [row] is what the tab list's `Area` row reads there, taken from a capture, and [folder]
 * is the package under `core/feature` whose features all belong to it.
 */
enum class Area(val row: String?, val folder: String?) {
    ANYWHERE(null, null),
    END("The End", "end"),
}
