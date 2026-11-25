package io.github.noxaur.dragen.core.feature.combat.abilitycooldowns

import io.github.noxaur.dragen.core.container.SlotMark
import io.github.noxaur.dragen.core.config.DragenConfig
import io.github.noxaur.dragen.core.config.Store
import io.github.noxaur.dragen.core.feature.Feature
import io.github.noxaur.dragen.core.feature.HasFeature
import io.github.noxaur.dragen.core.feature.SlotMarks
import io.github.noxaur.dragen.core.game.Abilities
import io.github.noxaur.dragen.core.game.ActionBar
import io.github.noxaur.dragen.core.game.Area
import io.github.noxaur.dragen.core.game.ContainerState.Companion.INVENTORY
import io.github.noxaur.dragen.core.game.ContainerState.Companion.WORN_SLOTS
import io.github.noxaur.dragen.core.game.Hit
import io.github.noxaur.dragen.core.game.Item
import io.github.noxaur.dragen.core.game.SetCharge
import io.github.noxaur.dragen.core.hud.HudBlock
import io.github.noxaur.dragen.core.text.fixed
import io.github.noxaur.dragen.core.text.secondsLeft
import io.github.noxaur.dragen.core.text.text
import java.util.IdentityHashMap
import kotlin.math.abs
import kotlin.math.ceil
import io.github.noxaur.dragen.core.feature.ContainerReader
import io.github.noxaur.dragen.core.feature.ServerClock

/** @keep A use is the action bar's mana part appearing, `-100 Mana (Giant's Slam)`, which stays up for a few of the server's half-second updates, so only its arrival counts. Some abilities say so in chat instead: `HOWL!` for the Tuba, `Soulcry! +` for a katana, `You used Void Infusion!` for the drill. A charging set bonus, `(4) Every 8s your next hit blinks you`, starts on the player's own hit and a hit while it charges does not restart it, so the server sends nothing for it. */
class AbilityCooldowns(config: DragenConfig) : HasFeature {
    override val feature = Feature(config, "ability-cooldowns", Area.ANYWHERE, Store.GENERAL, "Time your item abilities and Riftwalker's charged hit.", hud = ::hud, slots = ::slots)

    val list = feature.switch("cooldownList", "List", "List the abilities you are waiting on, on the HUD.", default = true)
    val onItems = feature.switch("cooldownOnItems", "On Items", "Drain a wash over the item and count down its seconds.", default = true)
    val shortest = feature.number("cooldownShortest", "Shortest Listed", "List only cooldowns at least this many seconds long.", 2f, 0f..20f, step = 0.5f, unit = "s")
    val readyAlert = feature.switch("cooldownReadyAlert", "Ready Alert", "Alert you when a long cooldown is over.", default = false)
    val alertFrom = feature.number("cooldownAlertFrom", "Alert From", "Alert only for cooldowns at least this many seconds long.", 20f, 1f..120f, step = 1f, unit = "s")

    private class Known(val name: String, val millis: Long)

    private class Timer(val key: String, val name: String, val lore: Long, val start: Long, var end: Long) {
        var alerted = false
    }

    private class Learned(val lore: Long, val millis: Long)

    private class Read(val keys: List<String>, val abilities: List<Known>, val charge: SetCharge?, val anySet: Boolean)

    private val clock = ServerClock(feature)
    private val reads = IdentityHashMap<Item, Read>()
    private val known = HashMap<String, Known>()
    private val timers = HashMap<String, Timer>()
    private val learned = HashMap<String, Learned>()

    private val loreNamed = HashSet<String>()

    private var charge: SetCharge? by feature.world { null }

    private var lastCast: String? by feature.world { null }

    private val items = ContainerReader(feature)
    private val marks = SlotMarks(feature, ::mark)
    private var marksClock = -1
    private var markAt = 0L

    private var quiet = true
    private var quietClock = -1

    init {
        feature.on<ActionBar> { bar, now ->
            val plain = bar.text.plain
            val cast = Abilities.cast(plain)
            if (cast == null && Abilities.castUnread(plain)) feature.unparsed(plain)
            if (cast != null && cast != lastCast) start(cast, now)
            lastCast = cast
            ready(now)
        }
        feature.line(Regex.fromLiteral(Abilities.REFUSAL), Abilities::refusalMillis) { left, now -> refused(left, now) }
        feature.line("HOWL!") { now -> start("Howl", now) }
        feature.line(SOULCRY, SOULCRY) { _, now -> start("Soulcry", now) }
        feature.line(USED, USED_LINE) { (ability), now -> start(ability, now) }
        feature.on<Hit> { _, now ->
            read()
            val bonus = charge ?: return@on
            val key = bonus.name.lowercase()
            val running = timers[key]
            val at = clock.millis(now)
            if (running != null && at < running.end) return@on
            timers[key] = Timer(key, bonus.name, bonus.millis, at, at + bonus.millis)
            moved()
        }
    }

    private fun start(name: String, now: Long) {
        read()
        val key = name.lowercase()
        val ability = (if (key in loreNamed) unheaded(name) else null) ?: known[key] ?: unheaded(name) ?: return
        val running = timers[key]
        val at = clock.millis(now)
        // @keep Soulcry's chat line and its action bar part tell of one use a tick or two apart
        if (running != null && at - running.start < SAME_USE_MILLIS && at < running.end) return
        val length = learned[key]?.takeIf { it.lore == ability.millis }?.millis ?: ability.millis
        timers[key] = Timer(key, ability.name, ability.millis, at, at + length)
        moved()
    }

    /** @keep The server's refusal names no ability, so it is matched to the timer whose end is nearest. */
    private fun refused(left: Long, now: Long) {
        val at = clock.millis(now)
        val end = at + left
        val near = timers.values
            .filter { left <= it.lore * 3 / 2 + SLACK_MILLIS && at - it.start <= it.lore * 2 + SLACK_MILLIS }
            .sortedBy { abs(it.end - end) }
        val timer = near.firstOrNull() ?: return
        if (near.size > 1 && abs(near[1].end - end) - abs(timer.end - end) < SLACK_MILLIS / 2) return
        timer.end = end
        val ran = end - timer.start
        if (ran in timer.lore / 2..timer.lore * 3 / 2 && abs(ran - timer.lore) >= maxOf(LEARN_MILLIS, timer.lore / 10)) {
            learned[timer.key] = Learned(timer.lore, ran)
        }
        if (at < end) timer.alerted = false
        moved()
    }

    private fun moved() {
        quiet = false
        marks.invalidate()
    }

    private fun live(now: Long): Long? {
        if (quiet && clock.updates == quietClock) return null
        val at = clock.millis(now)
        quiet = timers.values.none { at - it.end < READY_MILLIS }
        quietClock = clock.updates
        return at.takeUnless { quiet }
    }

    private fun ready(now: Long) {
        if (!readyAlert.value) return
        val at = clock.millis(now)
        val from = (alertFrom.value * 1000).toLong()
        for (timer in timers.values) {
            if (timer.alerted || at < timer.end || timer.lore < from) continue
            timer.alerted = true
            if (at - timer.end > READY_MILLIS) continue
            feature.alert(text { good("${timer.name} Ready!") }, text { value(timer.name); body(" is ready.") })
        }
    }

    // @keep A drill's ability is the one picked in its menu, so its name may be written anywhere in the lore
    private fun unheaded(name: String): Known? {
        val slots = feature.container.slots
        for (i in feature.container.playerSlots) {
            val item = slots[i] ?: continue
            if (item.lore.none { it.plain.contains(name, ignoreCase = true) }) continue
            val millis = Abilities.cooldown(item) ?: continue
            loreNamed += name.lowercase()
            return Known(name, millis).also { known[name.lowercase()] = it }
        }
        return null
    }

    private fun read() {
        if (!items.changed()) return
        marks.invalidate()
        val slots = feature.container.slots
        for (i in feature.container.playerSlots) {
            val item = slots[i] ?: continue
            for (ability in read(item).abilities) known[ability.name.lowercase()] = ability
        }
        if (feature.container.id != INVENTORY || slots.size <= WORN_SLOTS.last) return
        val worn = WORN_SLOTS.map { slots[it]?.let(::read) }
        val bonus = worn.firstNotNullOfOrNull { it?.charge }
        charge = bonus?.takeIf { b ->
            val pieces = worn.count { it?.charge?.name == b.name } + if (worn[0]?.let { it.anySet && it.charge == null } == true) 1 else 0
            b.worn >= b.pieces || pieces >= b.pieces
        }
    }

    private fun read(item: Item): Read = reads.getOrPut(item) {
        if (reads.size > MAX_READS) reads.clear()
        val abilities = Abilities.of(item).map { Known(it.name, it.cooldownMillis) }
        val charge = Abilities.charge(item)
        Read(abilities.map { it.name.lowercase() } + listOfNotNull(charge?.name?.lowercase()), abilities, charge, Abilities.anySetHelmet(item))
    }

    fun hud(now: Long): HudBlock? {
        read()
        if (!list.value) return null
        val at = live(now) ?: return null
        val from = (shortest.value * 1000).toLong()
        val shown = timers.values.filter { it.lore >= from && at - it.end < READY_MILLIS }.sortedBy { it.end }
        if (shown.isEmpty()) return null
        return feature.block {
            for (timer in shown) {
                val left = timer.end - at
                if (left > 0) {
                    bar(timer.name, left.toFloat() / (timer.end - timer.start)) { value(seconds(clock.wall(left))) }
                } else {
                    row(timer.name) { good("Ready") }
                }
            }
        }
    }

    fun slots(now: Long): Map<Int, SlotMark> {
        read()
        if (!onItems.value) return emptyMap()
        markAt = live(now) ?: return emptyMap()
        if (clock.updates != marksClock) {
            marksClock = clock.updates
            marks.invalidate()
        }
        return marks.marks(now)
    }

    private fun mark(slot: Int, item: Item, now: Long): SlotMark? {
        if (slot !in feature.container.playerSlots) return null
        val at = markAt
        var timer: Timer? = null
        for (key in read(item).keys) {
            val t = timers[key] ?: continue
            if (at < t.end && (timer == null || t.end > timer.end)) timer = t
        }
        for (key in loreNamed) {
            val t = timers[key] ?: continue
            if (at >= t.end || timer != null && t.end <= timer.end || item.lore.none { it.plain.contains(t.name, ignoreCase = true) }) continue
            timer = t
        }
        timer ?: return null
        val end = now + clock.wall(timer.end - at)
        marks.invalidateAt(end)
        return SlotMark.cooldown(end - clock.wall(timer.end - timer.start), end)
    }

    private companion object {
        const val USED = "You used "
        val USED_LINE = Regex("""You used (.+?)[!.]?""")
        val SOULCRY = Regex("""(?:☠ )?Soulcry! \+.*""")
        const val SAME_USE_MILLIS = 500L
        const val SLACK_MILLIS = 1000L
        const val LEARN_MILLIS = 300L

        const val READY_MILLIS = 2000L
        const val MAX_READS = 512

        fun seconds(millis: Long): String {
            if (millis < 10_000) return "${fixed(ceil(millis / 100.0) / 10, 1)}s"
            val whole = secondsLeft(millis)
            return if (whole < 60) "${whole}s" else "${whole / 60}m ${whole % 60}s"
        }
    }
}
