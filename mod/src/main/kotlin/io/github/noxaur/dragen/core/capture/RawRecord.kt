package io.github.noxaur.dragen.core.capture

enum class RawRecord {
    CHAT,

    TITLE,

    BOSSBARS,

    TABHEADER,

    ITEMCOMPONENTS,

    ENTITIES,

    STATUS,

    SOUND,

    PARTICLES,

    // @keep A block update: place, the new `state`, and the state the block `was` before it.
    BLOCK,

    BREAKING,

    // @keep Minecraft's level event 2001 is a block's break effect, so that one also records the `block` state that broke.
    LEVELEVENT,

    DIGGING,

    SWING,

    USE,

    UNLOAD,

    SKULLS,

    BLOCKSTATES,

    MAP,

    HUD,

    METRICS,

    STEP,
    ;

    val type = name.lowercase()

    companion object {
        private val byType = entries.associateBy { it.type }

        fun of(type: String) = byType[type] ?: error("'$type' is no RawRecord. Add it there.")
    }
}
