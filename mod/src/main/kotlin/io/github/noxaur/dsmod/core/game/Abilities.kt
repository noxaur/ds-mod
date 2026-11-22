package io.github.noxaur.dsmod.core.game

import io.github.noxaur.dsmod.core.text.count
import io.github.noxaur.dsmod.core.text.number
import kotlin.math.roundToLong

data class Ability(val name: String, val cooldownMillis: Long)

data class SetCharge(val name: String, val worn: Int, val pieces: Int, val millis: Long)

/** @keep An ability's lore is a heading such as `Giant's Slam` followed by the button, `RIGHT CLICK`, with the
 * server's own glyphs between the two, then its description, `Mana Cost: 100` and `Cooldown: 4.4s`, and a
 * blank line before the next one. A cast that costs mana shows as `-100 Mana (Giant's Slam)` in the action bar */
object Abilities {
    const val REFUSAL = "This ability is on cooldown for "
    private val REFUSAL_LINE = Regex("""This ability is on cooldown for (\d+(?:\.\d+)?)s""")
    private val CAST = Regex("""-[\d,]+ Mana \((.+?)\)""")
    private val HEADING = Regex("""(?:Item Ability: )?(.+?) ?(?:(?:SNEAK )?(?:RIGHT|LEFT) CLICK|OFFHAND KEY)""")
    private val COOLDOWN = Regex("""Cooldown: (\d+(?:\.\d+)?)(?:s| seconds?)""")
    private val TIERED = Regex("""Tiered Bonus: (.+) \((\d+)/\d+\)""")
    private val CHARGE = Regex("""\((\d+)\) Every (\d+(?:\.\d+)?)s your next hit\b.*""")

    // @keep The Warden Helmet's Chameleon makes a helmet count toward any set
    private const val ANY_SET = "counts as the helmet of any armor set"

    fun refusalMillis(plain: String): Long? = REFUSAL_LINE.seconds(plain)

    fun cast(bar: String): String? = if (" Mana (" in bar) CAST.find(bar)?.groupValues?.get(1) else null

    fun castUnread(bar: String) = " Mana (" in bar && cast(bar) == null

    fun of(item: Item): List<Ability> {
        var found: MutableList<Ability>? = null
        item.sections(HEADING) { heading, lines ->
            for (line in lines) {
                val cooldown = COOLDOWN.seconds(line) ?: continue
                if (cooldown > 0) (found ?: ArrayList<Ability>(1).also { found = it }) += Ability(heading.groupValues[1].trim(), cooldown)
            }
        }
        return found ?: emptyList()
    }

    fun cooldown(item: Item): Long? = item.cleanLore.firstNotNullOfOrNull { line -> COOLDOWN.seconds(line)?.takeIf { it > 0 } }

    fun charge(item: Item): SetCharge? {
        item.sections(TIERED) { bonus, lines ->
            val worn = count(bonus.groupValues[2]) ?: return@sections
            for (line in lines) {
                val charge = CHARGE.matchEntire(line) ?: continue
                val pieces = count(charge.groupValues[1]) ?: continue
                val every = millis(charge.groupValues[2]) ?: continue
                return SetCharge(bonus.groupValues[1], worn, pieces, every)
            }
        }
        return null
    }

    fun anySetHelmet(item: Item): Boolean {
        if (item.name?.plain?.contains("Warden Helmet") == true) return true
        return item.cleanLore.joinToString(" ").lowercase().replace(WHITESPACE, " ").contains(ANY_SET)
    }

    private val WHITESPACE = Regex("""\s+""")

    private fun Regex.seconds(plain: String) = matchEntire(plain)?.groupValues?.get(1)?.let(::millis)

    private fun millis(seconds: String) = number(seconds)?.let { (it * 1000).roundToLong() }
}
