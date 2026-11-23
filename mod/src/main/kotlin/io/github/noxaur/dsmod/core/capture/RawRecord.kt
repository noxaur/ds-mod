package io.github.noxaur.dsmod.core.capture

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

    BLOCK,

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
