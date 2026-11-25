package io.github.noxaur.dragen.core.feature.end.trackers.bossfighttracker

import io.github.noxaur.dragen.core.config.DragenConfig
import io.github.noxaur.dragen.core.config.Store
import io.github.noxaur.dragen.core.feature.Feature
import io.github.noxaur.dragen.core.feature.HasFeature
import io.github.noxaur.dragen.core.game.Area
import io.github.noxaur.dragen.core.hud.HudBlock
import io.github.noxaur.dragen.core.text.count
import kotlinx.serialization.Serializable

@Serializable
data class BossRecord(val fights: Int = 0, val first: Int = 0, val loot: Map<String, Int> = emptyMap())

@Serializable
data class BossLog(val bosses: Map<String, BossRecord> = emptyMap())

/** @keep The server sends one message of several lines when a dragon or the Endstone Protector falls, with the player's own `Your Damage` line. A dragon's loot follows about ten seconds later, when another boss may have fallen, so each summary waits its turn. */
class BossFightTracker(config: DragenConfig) : HasFeature {
    override val feature = Feature(config, "boss-fight-tracker", Area.END, Store.END, "Show your rank, damage and loot from the last boss fight.", formerly = listOf("bossDrops"), hud = ::hud)

    private val log = feature.profiled(BossLog())
    private var last: String? = null

    private var lastDragon: String? by feature.world { null }
    private var lastDragonAt by feature.world { Long.MIN_VALUE }
    private var position = 0
    private var damage = ""
    private var fights = 0
    private var loot = emptyList<Pair<Int, String>>()
    private var lootAt: Long? by feature.world { null }
    private var lootDragon: String? by feature.world { null }
    private val awaitingLoot by feature.world { ArrayDeque<Pair<String, Long>>() }

    // @keep A player's chat line has their name first, so only the server's summary has these on lines of their own
    private val down = Regex("""^\s+([A-Z][A-Z ]*[A-Z]) DOWN!$""", RegexOption.MULTILINE)
    private val mine = Regex("""^\s+Your Damage: (\S+) \(Position #(\d+)\)$""", RegexOption.MULTILINE)

    init {
        feature.line(Regex("""[^\n]*\n[^\n]* DOWN!\n"""), { text -> down.find(text)?.let { it.groupValues[1] to mine.find(text) } }) { (boss, yours), now ->
            lootAt = null
            val (dealt, place) = yours?.destructured ?: return@line
            val at = count(place) ?: return@line
            val name = boss.lowercase().split(' ').joinToString(" ") { it.replaceFirstChar(Char::uppercase) }
            last = name
            if (name.endsWith(" Dragon")) {
                lastDragon = name
                lastDragonAt = now
                awaitingLoot.addLast(name to now)
                dropStale(now)
            }
            damage = dealt
            position = at
            fights++
            loot = emptyList()
            record(name) { it.copy(fights = it.fights + 1, first = it.first + if (position == 1) 1 else 0) }
        }
        feature.line("Here is your loot from the last dragon:") { now ->
            lootAt = now
            dropStale(now)
            lootDragon = awaitingLoot.removeFirstOrNull()?.first ?: lastDragon?.takeIf { now - lastDragonAt <= LOOT_WINDOW_MILLIS }
            if (last == lastDragon) loot = emptyList()
        }
        feature.line(Regex(""" - \d+x """), Regex(""" - (\d+)x (.+)""")) { (written, item), now ->
            if (now - (lootAt ?: return@line) > LOOT_MILLIS) return@line
            val dragon = lootDragon ?: return@line
            val amount = count(written) ?: return@line
            if (last == dragon) loot = loot + (amount to item)
            record(dragon) { it.copy(loot = it.loot + (item to (it.loot[item] ?: 0) + amount)) }
        }
    }

    private fun dropStale(now: Long) {
        while (awaitingLoot.firstOrNull()?.let { now - it.second > LOOT_WINDOW_MILLIS } == true) awaitingLoot.removeFirst()
    }

    private fun record(boss: String, change: (BossRecord) -> BossRecord) {
        val bosses = log.value.bosses
        log.value = BossLog(bosses + (boss to change(bosses[boss] ?: BossRecord())))
    }

    fun hud(now: Long): HudBlock? {
        val boss = last ?: return null
        val record = log.value.bosses[boss] ?: return null
        return feature.block {
            row(boss) { value("#$position"); muted(" with $damage") }
            for ((amount, item) in loot) row("Loot") { value("${amount}x $item") }
            row("Fights") { value(fights.toLong()); muted(" (${record.fights} all-time on this boss)") }
        }
    }

    private companion object {
        const val LOOT_MILLIS = 1_000L

        /** @keep In `dragons-and-protector` the loot header follows DOWN by about ten seconds, and the longest of those gaps
         * is 10.015s */
        const val LOOT_WINDOW_MILLIS = 15_000L
    }
}
