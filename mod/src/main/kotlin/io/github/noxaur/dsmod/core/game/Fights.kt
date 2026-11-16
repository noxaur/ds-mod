package io.github.noxaur.dsmod.core.game

/**
 * Which boss fights are on, from the lines and the tab row that announce them: a dragon from its spawn to its
 * de-spawn or the egg's return, the Endstone Protector from its emergence to its defeat or while the tab list's
 * `Protector` row reads `Spawned`, and the player's slayer boss from its spawn to its death or the quest's end.
 * `Game` folds every event into one of these before the features see it, and the features that follow one fight
 * read its lines with the parsers here.
 */
class Fights {
    var dragon = false
        private set
    var slayer = false
        private set
    private var emerged = false
    private var spawned = false

    // Chat is a second or two ahead of the tab list, so a defeat outranks a row that still reads `Spawned`.
    private var defeated = false

    val protector: Boolean get() = emerged || spawned && !defeated

    /** Whether any boss fight is on. */
    val on: Boolean get() = dragon || slayer || protector

    /** Reads one chat line, and returns whether a fight began or ended. */
    fun line(text: String): Boolean {
        val before = on
        when {
            DRAGON_HEAD.matchesAt(text, 0) -> DRAGON.matchEntire(text)?.let { dragon = it.groupValues[2] == SPAWNED }
            text.startsWith(EGG_RESPAWNING) -> dragon = false
            text.startsWith(PROTECTOR_EMERGED) -> {
                emerged = true
                defeated = false
            }
            text.startsWith(PROTECTOR_DEFEATED) -> {
                emerged = false
                defeated = true
            }
            SLAYER_HEAD.matchesAt(text, 0) -> SLAYER_BOSS.matchEntire(text)?.let { slayer = it.groupValues[1] == SPAWNING }
            QUEST_ENDS.any(text::startsWith) -> slayer = false
        }
        return on != before
    }

    /** Reads the tab list's `Protector` row, or null when the tab list has none, and returns whether a fight began or ended. */
    fun protectorRow(value: String?): Boolean {
        val before = on
        val now = value?.let(ProtectorRow::parse)?.stage == SPAWNED_STAGE
        // The row moving on from the fight ends it, whether or not the defeat reached chat.
        if (spawned && !now) emerged = false
        if (!now) defeated = false
        spawned = now
        return on != before
    }

    /** A new world has none of the old one's fights. */
    fun clear() {
        dragon = false
        slayer = false
        emerged = false
        spawned = false
        defeated = false
    }

    companion object {
        /** `☠ The ` also starts the Protector's lines, so the head names a dragon. */
        val DRAGON_HEAD = Regex("""☠ The .+ Dragon has """)
        val DRAGON = Regex("""☠ The (\w+) Dragon has (spawned!|de-spawned\.)""")
        const val SPAWNED = "spawned!"
        const val EGG_RESPAWNING = "The Dragon Egg is respawning"
        const val PROTECTOR_EMERGED = "☠ The Endstone Protector has emerged"
        const val PROTECTOR_DEFEATED = "The Endstone Protector has been defeated!"
        const val SPAWNED_STAGE = "Spawned"
        val SLAYER_HEAD = Regex("""☠ .*SLAYER BOSS""")
        val SLAYER_BOSS = Regex("""☠ (?:NICE! )?SLAYER BOSS (SPAWNING|SLAIN)!""")
        const val SPAWNING = "SPAWNING"
        const val SLAYER_SPAWNING = "☠ SLAYER BOSS SPAWNING!"
        val QUEST_ENDS = listOf("  SLAYER QUEST COMPLETE!", "  SLAYER QUEST FAILED")
    }
}
