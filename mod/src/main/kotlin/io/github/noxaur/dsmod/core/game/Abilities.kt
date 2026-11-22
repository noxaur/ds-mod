package io.github.noxaur.dsmod.core.game

import kotlin.math.roundToLong

/** An ability an item's lore names, with the wait between two uses. */
data class Ability(val name: String, val cooldownMillis: Long)

/**
 * A set bonus that charges, such as Riftwalker's `(4) Every 8s your next hit blinks you behind the target`: [name] is the
 * bonus as the lore heads it, [worn] the pieces the server counts as worn, and the charge needs [pieces] of them.
 */
data class SetCharge(val name: String, val worn: Int, val pieces: Int, val millis: Long)

/**
 * The lines the server uses for item abilities, read by every feature that follows them. An ability's lore is a heading
 * such as `Giant's Slam` followed by the button, `RIGHT CLICK`, with the server's own glyphs between the two, then its
 * description, `Mana Cost: 100` and `Cooldown: 4.4s`, and a blank line before the next one. A cast that costs mana shows
 * as `-100 Mana (Giant's Slam)` in the action bar, and a press too early is refused in chat.
 */
object Abilities {
    /** How the server starts its refusal of an ability still on cooldown. */
    const val REFUSAL = "This ability is on cooldown for "
    private val REFUSAL_LINE = Regex("""This ability is on cooldown for (\d+(?:\.\d+)?)s""")
    private val CAST = Regex("""-[\d,]+ Mana \((.+?)\)""")
    private val HEADING = Regex("""(?:Item Ability: )?(.+?) ?(?:(?:SNEAK )?(?:RIGHT|LEFT) CLICK|OFFHAND KEY)""")
    private val COOLDOWN = Regex("""Cooldown: (\d+(?:\.\d+)?)(?:s| seconds?)""")
    private val TIERED = Regex("""Tiered Bonus: (.+) \((\d+)/\d+\)""")
    private val CHARGE = Regex("""\((\d+)\) Every (\d+(?:\.\d+)?)s your next hit\b.*""")

    /** The Warden Helmet's Chameleon, which makes a helmet count toward any set. */
    private const val ANY_SET = "counts as the helmet of any armor set"

    /** How long the refusal in [plain] says is left, or null when it is not one or does not parse. */
    fun refusalMillis(plain: String): Long? = REFUSAL_LINE.seconds(plain)

    /** The ability an action bar names in its mana part, such as `Giant's Slam`, or null when it has none. */
    fun cast(bar: String): String? = if (" Mana (" in bar) CAST.find(bar)?.groupValues?.get(1) else null

    /** Whether an action bar has a mana part that [cast] could not read. */
    fun castUnread(bar: String) = " Mana (" in bar && cast(bar) == null

    /** The abilities in [item]'s lore that have a cooldown, each with the heading above its `Cooldown` line. */
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

    /** The first `Cooldown` line anywhere in [item]'s lore, for an ability that has no heading, or null when it has none. */
    fun cooldown(item: Item): Long? = item.cleanLore.firstNotNullOfOrNull { line -> COOLDOWN.seconds(line)?.takeIf { it > 0 } }

    /** The charging set bonus in a worn piece's lore, or null when it has none. */
    fun charge(item: Item): SetCharge? {
        item.sections(TIERED) { bonus, lines ->
            val charge = lines.firstNotNullOfOrNull { CHARGE.matchEntire(it) } ?: return@sections
            return SetCharge(bonus.groupValues[1], bonus.groupValues[2].toInt(), charge.groupValues[1].toInt(), millis(charge.groupValues[2]))
        }
        return null
    }

    /** Whether [item] is a helmet that stands in for any set's, as the Warden Helmet's Chameleon does. */
    fun anySetHelmet(item: Item): Boolean {
        if (item.name?.plain?.contains("Warden Helmet") == true) return true
        return item.cleanLore.joinToString(" ").lowercase().replace(WHITESPACE, " ").contains(ANY_SET)
    }

    private val WHITESPACE = Regex("""\s+""")

    /** The milliseconds a whole [plain] line names in its first group, or null when the line is not this regex's. */
    private fun Regex.seconds(plain: String) = matchEntire(plain)?.groupValues?.get(1)?.let(::millis)

    private fun millis(seconds: String) = (seconds.toDouble() * 1000).roundToLong()
}
