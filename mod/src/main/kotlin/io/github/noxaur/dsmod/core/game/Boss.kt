package io.github.noxaur.dsmod.core.game

/** The kinds of boss the mod tells apart. */
enum class BossKind { DRAGON, PROTECTOR, SLAYER }

/**
 * A boss the client holds, by its [id] on this client, and the [label] the player knows it by. A dragon's name over
 * it holds only its level and health, such as `[Lv100]  9M/9M`, since the boss bar names it, so every dragon is
 * `Dragon`. The Protector is an iron golem named `[Lv100] Endstone Protector 9.9M/10M`, and a slayer boss any mob
 * named like `☠ Revenant Horror I 1.5M/3.8M❤`, with what the fight adds after the hearts.
 */
data class Boss(val id: Int, val kind: BossKind, val label: String) {
    companion object {
        private val slayer = Regex("""^☠ (.+?) \S+/\S+❤""")

        /** The boss [near] is, or null when its name has another shape. */
        fun parse(near: NearBoss): Boss? {
            val name = near.name.plain
            return when {
                near.entity == "minecraft:ender_dragon" -> Boss(near.id, BossKind.DRAGON, "Dragon")
                near.entity == "minecraft:iron_golem" && PROTECTOR in name -> Boss(near.id, BossKind.PROTECTOR, PROTECTOR)
                else -> slayer.find(name)?.let { Boss(near.id, BossKind.SLAYER, it.groupValues[1]) }
            }
        }

        private const val PROTECTOR = "Endstone Protector"
    }
}
