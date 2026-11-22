package io.github.noxaur.dsmod.core.game

enum class SlayerLine {
    SPAWNING,
    SLAIN,
    ;

    companion object {
        const val SPAWNED = "☠ SLAYER BOSS SPAWNING!"
        val head = Regex("""☠ .*SLAYER BOSS""")
        private val line = Regex("""☠ (?:NICE! )?SLAYER BOSS (${entries.joinToString("|")})!""")

        fun parse(text: String): SlayerLine? = line.matchEntire(text)?.groupValues?.get(1)?.let(::valueOf)
    }
}
