package io.github.noxaur.dsmod.core.game

/**
 * Where a feature runs. The tab list's info row [key] reads [value] there, taken from a capture, and [folder] is
 * the package under `core/feature` whose features all belong to it. The Dungeon Hub has no entry, because its
 * tab list has no info row to tell it by. The sidebar's places, such as `Void Sepulture` and `Dragon's Den`, are
 * parts of The End and are not areas.
 */
enum class Area(val key: String?, val value: String?, val folder: String?) {
    ANYWHERE(null, null, null),
    VILLAGE("Area", "Village", "village"),
    CRYPTS("Area", "The Crypts", "crypts"),
    END("Area", "The End", "end"),

    /** A dungeon run. Its tab list has no `Area` row at all. */
    CATACOMBS("Dungeon", "Catacombs", "dungeon"),
    ;

    /** The tab list row that puts the player here, for a test to send. */
    val row: String? get() = key?.let { "$it: $value" }
}
