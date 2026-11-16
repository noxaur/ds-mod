package io.github.noxaur.dsmod.core.capture

/**
 * Every type of capture line that is not a `GameEvent`: data the recorder writes for a reader of the file, which no
 * feature sees until it is promoted to an event. A raw record's text is plain `text` plus Minecraft's full
 * `component`. A type recorded as entries writes all of them once with `"full": true`, then only the entries that
 * changed, with null for a removed one, as [ChangeLog] does.
 */
enum class RawRecord {
    /** A message that arrived as player chat, with its `sender`. The `system` event carries the same line. */
    CHAT,

    /** The `title` and `subtitle` on screen, each time the server sends them, even with the same text. */
    TITLE,

    /** The boss bars on screen, as `bars`, each with its text, `progress` and `color`. */
    BOSSBARS,

    /** The tab list's `header` and `footer`. Its rows are the `tablist` event. */
    TABHEADER,

    /**
     * Entries by slot, for the item components a `slot` event leaves out, such as `item_model` and the full
     * `profile`, in Minecraft's own codec JSON. The header names the menu's `id` and `menu` type.
     */
    ITEMCOMPONENTS,

    /**
     * Entries by entity id, for players, named entities and anything wearing or holding an item, each with its
     * `entity` type, place, `name`, `equipment` and `item`. Glowing entities, silverfish and creepers, displays with
     * their `text` or `item`, item frames with their `item`, `rotation` and `facing`, and dropped named items come too.
     */
    ENTITIES,

    /** The player's block, `health`, `maxHealth`, `absorption`, `food`, `xpLevel` and `xpProgress`. */
    STATUS,

    /** A sound the server played: `sound`, `source`, place, `volume` and `pitch`. */
    SOUND,

    /** Particles the server spawned: `particle`, place, `count` and `speed`. */
    PARTICLES,

    /** A block the server changed, with its place and new `state`. */
    BLOCK,

    /**
     * The player's attack button, before the game acts: their place, `yaw`, `pitch`, the held item's `item`, `name`
     * and `custom_data` `itemId`, and the `target` entity or the `block` under the crosshair.
     */
    SWING,

    /** The player's use button, with the same fields as [SWING]. */
    USE,

    /** A living entity that is not a player and that the client dropped, with its `health` and `reason`. A mob that only left has this and no `death` event. */
    UNLOAD,

    /** Entries keyed `x,y,z` for the skull blocks within four chunks, with their `state` (rotation) and `profile`, including those placed before the capture began. */
    SKULLS,

    /** Entries keyed `x,y,z` for the blocks in `BlockScan`'s watch-list within four chunks, including those placed before the capture began. */
    BLOCKSTATES,

    /**
     * A map the player carries or an item frame holds, when it changes: `id`, `scale`, `locked`, the markers as
     * `decorations`, and `colors`, base64 of the zlib-deflated 128x128 color bytes, only when the pixels changed.
     */
    MAP,

    /** The rows of each HUD block ds-mod showed, by feature id. */
    HUD,

    /**
     * How long each feature took and what it counted. The `capture-items` and `capture-entities` timers are what the
     * recorder's own passes over items and entities cost the client thread, and `PerfTest` caps them.
     */
    METRICS,

    /** The brief's step that starts on this line, with its `index` and `do`, or `done` after the last. Cut a fixture's window from these. */
    STEP,
    ;

    /** The `type` on the line. */
    val type = name.lowercase()

    companion object {
        private val byType = entries.associateBy { it.type }

        /**
         * The raw record whose line has [type], for the callers that name it by its type: `Recording` and
         * `CaptureInputMixin`, which are audited input sites, so a change to them waits for the user.
         */
        fun of(type: String) = byType[type] ?: error("'$type' is no RawRecord. Add it there.")
    }
}
