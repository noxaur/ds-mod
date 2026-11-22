package io.github.noxaur.dsmod.core.game

enum class BossKind { DRAGON, PROTECTOR, SLAYER }

/** @keep A dragon's name over it holds only its level and health, such as `[Lv100] 9M/9M`, since the boss bar names
 * it, so every dragon is `Dragon`. The Protector is an iron golem named `[Lv100] Endstone Protector 9.9M/10M`,
 * and a slayer boss any mob named like `☠ Revenant Horror I 1.5M/3.8M❤`, with what the fight adds after the
 * hearts. A hologram is an armor stand, and never a boss, whatever its name says */
data class Boss(val id: Int, val kind: BossKind, val label: String) {
    companion object {
        private val slayer = Regex("""^☠ (.+?) \S+/\S+❤""")

        val FILTERS = listOf(
            EntityFilter(setOf("minecraft:ender_dragon")),
            EntityFilter(setOf("minecraft:iron_golem"), "[Lv"),
            EntityFilter(prefix = "☠"),
        )

        fun parse(seen: EntitySeen): Boss? {
            val name = seen.name.plain
            return when {
                seen.entity == "minecraft:ender_dragon" -> Boss(seen.id, BossKind.DRAGON, "Dragon")
                seen.entity == "minecraft:iron_golem" && PROTECTOR in name -> Boss(seen.id, BossKind.PROTECTOR, PROTECTOR)
                seen.entity == "minecraft:armor_stand" || seen.entity == "minecraft:player" -> null
                else -> slayer.find(name)?.let { Boss(seen.id, BossKind.SLAYER, it.groupValues[1]) }
            }
        }

        private const val PROTECTOR = "Endstone Protector"
    }
}
