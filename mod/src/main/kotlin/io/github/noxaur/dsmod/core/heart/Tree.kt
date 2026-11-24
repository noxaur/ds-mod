package io.github.noxaur.dsmod.core.heart

import io.github.noxaur.dsmod.core.game.Item
import io.github.noxaur.dsmod.core.player.Heart
import io.github.noxaur.dsmod.core.text.count

/** @keep The Heart of the Dragon as its menu showed it: the tier, the Core's level, the tokens spent, the powder, and each
 * perk's level by name. A perk the menu never showed, since it shows five tiers at a time, has no level */
data class Tree(
    val tier: Int,
    val core: Int,
    val spent: Int,
    val ender: Long,
    val void: Long,
    val levels: Map<String, Int>,
    val enderColor: Int? = null,
    val voidColor: Int? = null,
) {
    fun have(powder: Powder) = if (powder == Powder.ENDER) ender else void

    /** @keep A reset or another Heart profile changes tiers the player has not scrolled to since, and only the tokens
     * spent tell, since each perk took one */
    val stale: Boolean get() = levels.count { it.value > 0 } != spent

    companion object {
        const val HEART = "Heart of the Dragon"
        const val CORE = "Core of the Dragon"
        private const val COST = "Cost"
        private const val TOKEN = "1 Token of the Dragon"
        private val LEVEL = Regex("Level (\\d+)/\\d+")
        private val CORE_LEVEL = Regex("Level (\\d+)")
        private val TIER = Regex("Tier: (\\d+)")
        private val TOKENS = Regex("Token of the Dragon: (\\d+) \\((\\d+) spent\\)")
        private val ENDER = Regex("Ender Powder: ([\\d,]+)")
        private val VOID = Regex("Void Powder: ([\\d,]+)")

        private fun Item.find(pattern: Regex) = cleanLore.firstNotNullOfOrNull { pattern.matchEntire(it) }?.groupValues

        private fun Item.number(pattern: Regex) = find(pattern)?.get(1)?.let(::count)

        // @keep The server colors each powder's amount in that powder's color
        private fun Item.color(pattern: Regex) = lore.firstOrNull { pattern.matches(it.plain.trim()) }?.spans?.lastOrNull()?.color

        /** @keep A locked perk costs a token, and still reads `Level 1/50`, as level 1 is what the token buys. An ability's
         * `Level 3/3` is the Core's doing, so an unlocked ability is level 1. A line that reads none of these goes to
         * `unparsed` */
        fun level(perk: Perk, item: Item, unparsed: (String) -> Unit = {}): Int {
            if (item.section(COST).firstOrNull() == TOKEN) return 0
            if (perk.max == 1) return 1
            val level = item.number(LEVEL)
            if (level == null) unparsed(item.cleanLore.joinToString("\n"))
            return level ?: 0
        }

        fun read(heart: Heart, unparsed: (String) -> Unit = {}): Tree? {
            val egg = heart.items[HEART]?.item ?: return null
            val tier = egg.number(TIER)
            val spent = egg.find(TOKENS)?.get(2)?.let(::count)
            val ender = egg.number(ENDER)
            val void = egg.number(VOID)
            if (tier == null || spent == null || ender == null || void == null) {
                unparsed(egg.cleanLore.joinToString("\n"))
                return null
            }
            val core = heart.items[CORE]?.item?.number(CORE_LEVEL) ?: 0
            val levels = HashMap<String, Int>()
            for (perk in Perks.all) heart.items[perk.name]?.let { levels[perk.name] = level(perk, it.item, unparsed) }
            return Tree(tier, core, spent, ender.toLong(), void.toLong(), levels, egg.color(ENDER), egg.color(VOID))
        }
    }
}
