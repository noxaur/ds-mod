package io.github.noxaur.dsmod.core.feature.end

import io.github.noxaur.dsmod.core.feature.Feature
import io.github.noxaur.dsmod.core.game.Area
import io.github.noxaur.dsmod.core.hud.HudBlock
import kotlinx.serialization.Serializable
import kotlin.reflect.KMutableProperty0

/** One boss's history: the fights the player did damage in, how many they topped, and the loot they were given. */
@Serializable
data class BossRecord(val fights: Int = 0, val first: Int = 0, val loot: Map<String, Int> = emptyMap())

/** Every boss the player has fought, by the name the server gives it, such as `Wise Dragon`. */
@Serializable
data class BossLog(val bosses: Map<String, BossRecord> = emptyMap())

/**
 * Keeps the player's own results from the End's bosses. The server sends one message of several lines when a
 * dragon or the Endstone Protector falls, and its `Your Damage` line is the player's own, so the fight counts
 * only for a player who is in it. A dragon's loot follows about ten seconds later as its own lines, by which
 * time the Protector may have fallen too, so the loot goes to a dragon and not to the last boss. Another
 * dragon can fall during that wait, so each summary waits its turn and the header takes the oldest one.
 */
class BossDrops(toggle: KMutableProperty0<Boolean>) : Feature("boss-drops", toggle, Area.END) {
    private val log = saved(BossLog())
    private var last: String? = null
    private var lastDragon: String? = null
    private var position = 0
    private var damage = ""
    private var fights = 0
    private var loot = emptyList<Pair<Int, String>>()
    private var lootAt: Long? = null
    private var lootDragon: String? = null
    private val awaitingLoot = ArrayDeque<String>()

    // A player's chat line is one line with their name first, so only the server's summary has these on lines of their own.
    private val down = Regex("""^\s+([A-Z][A-Z ]*[A-Z]) DOWN!$""", RegexOption.MULTILINE)
    private val mine = Regex("""^\s+Your Damage: (\S+) \(Position #(\d+)\)$""", RegexOption.MULTILINE)

    init {
        line(Regex("""[^\n]*\n[^\n]* DOWN!\n"""), { text -> down.find(text)?.let { it.groupValues[1] to mine.find(text) } }) { (boss, yours), _ ->
            lootAt = null
            // The summary reaches everyone nearby, and one without this line is for a fight the player did no damage in.
            val (dealt, place) = yours?.destructured ?: return@line
            val name = boss.lowercase().split(' ').joinToString(" ") { it.replaceFirstChar(Char::uppercase) }
            last = name
            if (name.endsWith(" Dragon")) {
                lastDragon = name
                awaitingLoot.addLast(name)
            }
            damage = dealt
            position = place.toInt()
            fights++
            loot = emptyList()
            record(name) { it.copy(fights = it.fights + 1, first = it.first + if (position == 1) 1 else 0) }
        }
        line("Here is your loot from the last dragon:") { now ->
            lootAt = now
            // The header names no dragon. It belongs to the summary that is still waiting, not whichever dragon fell since.
            lootDragon = awaitingLoot.removeFirstOrNull() ?: lastDragon
            if (last == lastDragon) loot = emptyList()
        }
        line(Regex(""" - \d+x """), Regex(""" - (\d+)x (.+)""")) { (amount, item), now ->
            // The rows arrive in the same tick as their header, so a row long after it belongs to some other list.
            if (now - (lootAt ?: return@line) > LOOT_MILLIS) return@line
            val dragon = lootDragon ?: return@line
            if (last == dragon) loot = loot + (amount.toInt() to item)
            record(dragon) { it.copy(loot = it.loot + (item to (it.loot[item] ?: 0) + amount.toInt())) }
        }
    }

    private fun record(boss: String, change: (BossRecord) -> BossRecord) {
        val bosses = log.value.bosses
        log.value = BossLog(bosses + (boss to change(bosses[boss] ?: BossRecord())))
    }

    override fun hud(now: Long): HudBlock? {
        val boss = last ?: return null
        val record = log.value.bosses[boss] ?: return null
        return block {
            row(boss) { value("#$position"); muted(" with $damage") }
            for ((amount, item) in loot) row("Loot") { value("${amount}x $item") }
            row("Fights") { value(fights.toLong()); muted(" (${record.fights} all-time on this boss)") }
        }
    }

    private companion object {
        const val LOOT_MILLIS = 1_000L
    }
}
