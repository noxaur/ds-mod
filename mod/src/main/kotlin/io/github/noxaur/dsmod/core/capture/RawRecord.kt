package io.github.noxaur.dsmod.core.capture

/** @keep Data the recorder writes for a reader of the file, which no feature sees until it is promoted to an event. A type
 * recorded as entries writes all of them once with "full": true, then only the changed ones, null for a removed one,
 * as ChangeLog does. */
enum class RawRecord {
    CHAT,

    TITLE,

    BOSSBARS,

    TABHEADER,

    /** @keep Entries by slot: the item components a `slot` event leaves out, such as `item_model` and the full `profile`, in
     * Minecraft's codec JSON. */
    ITEMCOMPONENTS,

    /** @keep Entries by entity id: `entity` type, place, `name`, `equipment` and `item`, for players, named entities, glowing
     * ones, displays, item frames and dropped named items. */
    ENTITIES,

    // @keep The player's block, `health`, `maxHealth`, `absorption`, `food`, `xpLevel` and `xpProgress`.
    STATUS,

    SOUND,

    PARTICLES,

    BLOCK,

    /** @keep Fires before the game acts: place, `yaw`, `pitch`, the held item's `item`, `name` and `custom_data` `itemId`,
     * and the `target` entity or the `block` under the crosshair. */
    SWING,

    USE,

    /** @keep A living entity that is not a player and that the client dropped, with its `health` and `reason`. A mob that
     * only left has this and no `death` event. */
    UNLOAD,

    /** @keep Entries keyed `x,y,z` for the skull blocks within four chunks, including those placed before the capture began. */
    SKULLS,

    BLOCKSTATES,

    /** @keep `id`, `scale`, `locked`, `decorations`, and `colors` as base64 of the zlib-deflated 128x128 bytes, only when the
     * pixels changed. */
    MAP,

    HUD,

    /** @keep The `capture-items` and `capture-entities` timers are what the recorder's own passes cost the client thread, and
     * PerfTest caps them. */
    METRICS,

    STEP,
    ;

    val type = name.lowercase()

    companion object {
        private val byType = entries.associateBy { it.type }

        fun of(type: String) = byType[type] ?: error("'$type' is no RawRecord. Add it there.")
    }
}
