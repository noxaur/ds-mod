package io.github.noxaur.dsmod.core.feature.general

import io.github.noxaur.dsmod.core.container.SlotMark
import io.github.noxaur.dsmod.core.feature.Feature
import io.github.noxaur.dsmod.core.game.Abilities
import io.github.noxaur.dsmod.core.game.ActionBar
import io.github.noxaur.dsmod.core.game.Area
import io.github.noxaur.dsmod.core.game.ContainerOpened
import io.github.noxaur.dsmod.core.game.Hit
import io.github.noxaur.dsmod.core.game.Item
import io.github.noxaur.dsmod.core.game.SetCharge
import io.github.noxaur.dsmod.core.game.SlotChanged
import io.github.noxaur.dsmod.core.game.WorldChanged
import io.github.noxaur.dsmod.core.hud.HudBlock
import io.github.noxaur.dsmod.core.text.text
import java.util.IdentityHashMap
import java.util.Locale
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.reflect.KMutableProperty0

/**
 * Times every item ability the player uses, and Riftwalker's charging hit, as a HUD list and a wash over the item.
 *
 * A use is the action bar's mana part appearing, `-100 Mana (Giant's Slam)`, which stays up for a few of the server's
 * half-second updates, so only its arrival counts. Abilities without one say so in chat: `HOWL!` for the Tuba, `Soulcry! +`
 * for a katana, and `You used Void Infusion!` for the drill. Each names an ability whose length is the `Cooldown` line
 * under its heading in the lore of an item the player carries, so any ability is timed without a list of them. A press
 * too early is refused with what is left, which puts the timer where the server has it, and when the server's length is
 * not the lore's, such as under a Sky Mall cut, the measured one stands in while the lore stays the same.
 *
 * A charging set bonus, `(4) Every 8s your next hit blinks you`, starts on the player's own hit while the set is worn, and
 * a hit while it charges does not restart it, so the server sends nothing for it.
 */
class AbilityCooldowns(
    toggle: KMutableProperty0<Boolean>,
    private val list: KMutableProperty0<Boolean>,
    private val onItems: KMutableProperty0<Boolean>,
    private val shortest: KMutableProperty0<Float>,
    private val readyAlert: KMutableProperty0<Boolean>,
    private val alertFrom: KMutableProperty0<Float>,
) : Feature("ability-cooldowns", toggle, Area.ANYWHERE) {
    /** An ability the lore gave a length, by its name in lower case. */
    private class Known(val name: String, val millis: Long)

    private class Timer(val key: String, val name: String, val lore: Long, val start: Long, var end: Long) {
        var alerted = false
    }

    /** A length the server ran in place of the lore's [lore]. */
    private class Learned(val lore: Long, val millis: Long)

    /** What an item's lore says, worked out once per stack: the keys of its abilities, its charge and whether it is any set's helmet. */
    private class Read(val keys: List<String>, val abilities: List<Known>, val charge: SetCharge?, val anySet: Boolean)

    private val reads = IdentityHashMap<Item, Read>()
    private val known = HashMap<String, Known>()
    private val timers = HashMap<String, Timer>()
    private val learned = HashMap<String, Learned>()

    /** The abilities found by [unheaded], whose items name them anywhere in the lore rather than under a heading. */
    private val loreNamed = HashSet<String>()

    /** The charging bonus the worn set has now, or null. */
    private var charge: SetCharge? = null

    /** The ability in the last action bar's mana part, lower case, so a part that stays up counts once. */
    private var lastCast: String? = null

    // Whether the container changed since its items were read, and whether a timer changed since the marks were built.
    private var stale = true
    private var marked = true
    private var marks = emptyMap<Int, SlotMark>()
    private var marksUntil = Long.MAX_VALUE

    init {
        on<ContainerOpened> { _, _ -> stale = true }
        on<SlotChanged> { _, _ -> stale = true }
        // Also run when the feature is switched off, which stops the container events, so it reads the items again.
        on<WorldChanged> { _, _ ->
            lastCast = null
            charge = null
            stale = true
        }
        on<ActionBar> { bar, now ->
            val plain = bar.text.plain
            val cast = Abilities.cast(plain)
            if (cast == null && Abilities.castUnread(plain)) unparsed(plain)
            if (cast != null && cast != lastCast) start(cast, now)
            lastCast = cast
            ready(now)
        }
        line(Regex.fromLiteral(Abilities.REFUSAL), Abilities::refusalMillis) { left, now -> refused(left, now) }
        line("HOWL!") { now -> start("Howl", now) }
        // `☠ Soulcry! +250 ⫽ Ferocity against Endermen for 4s.`
        line(SOULCRY, SOULCRY) { _, now -> start("Soulcry", now) }
        line(USED, USED_LINE) { (ability), now -> start(ability, now) }
        on<Hit> { _, now ->
            read()
            val bonus = charge ?: return@on
            val key = bonus.name.lowercase()
            val running = timers[key]
            if (running != null && now < running.end) return@on
            timers[key] = Timer(key, bonus.name, bonus.millis, now, now + bonus.millis)
            marked = true
        }
    }

    /** Starts the timer of the ability the server named [name], if an item the player carries gives its length. */
    private fun start(name: String, now: Long) {
        read()
        val key = name.lowercase()
        // A heading's length is read again with the items, and a loose one only here, so look it up from the items now.
        val ability = (if (key in loreNamed) unheaded(name) else null) ?: known[key] ?: unheaded(name) ?: return
        val running = timers[key]
        // Soulcry's chat line and its action bar part tell of one use a tick or two apart.
        if (running != null && now - running.start < SAME_USE_MILLIS && now < running.end) return
        val length = learned[key]?.takeIf { it.lore == ability.millis }?.millis ?: ability.millis
        timers[key] = Timer(key, ability.name, ability.millis, now, now + length)
        marked = true
    }

    /**
     * Puts the timer the refusal is for where the server says it ends. The line names no ability, so it is the timer whose
     * end is nearest the server's, among those it could be: one used lately, whose length is not far under what is left.
     * Two that close together are left alone.
     */
    private fun refused(left: Long, now: Long) {
        val end = now + left
        val near = timers.values
            .filter { left <= it.lore * 3 / 2 + SLACK_MILLIS && now - it.start <= it.lore * 2 + SLACK_MILLIS }
            .sortedBy { abs(it.end - end) }
        val timer = near.firstOrNull() ?: return
        if (near.size > 1 && abs(near[1].end - end) - abs(timer.end - end) < SLACK_MILLIS / 2) return
        timer.end = end
        // After a missed use, start to end spans two cooldowns and the gap, which is outside half to one and a half.
        val ran = end - timer.start
        if (ran in timer.lore / 2..timer.lore * 3 / 2 && abs(ran - timer.lore) >= maxOf(LEARN_MILLIS, timer.lore / 10)) {
            learned[timer.key] = Learned(timer.lore, ran)
        }
        if (now < end) timer.alerted = false
        marked = true
    }

    private fun ready(now: Long) {
        if (!readyAlert.get()) return
        val from = (alertFrom.get() * 1000).toLong()
        for (timer in timers.values) {
            if (timer.alerted || now < timer.end || timer.lore < from) continue
            timer.alerted = true
            // Only a cooldown that ended just now is news.
            if (now - timer.end > READY_MILLIS) continue
            alert(text { good("${timer.name} Ready!") }, text { value(timer.name); body(" is ready.") })
        }
    }

    /**
     * The length of an ability whose lore gives it no heading, from the `Cooldown` line of the item the player carries
     * that names it anywhere in its lore. A drill's ability is the one picked in its menu, so it may be written so.
     */
    private fun unheaded(name: String): Known? {
        val slots = container.slots
        for (i in own() until slots.size) {
            val item = slots[i] ?: continue
            if (item.lore.none { it.plain.contains(name, ignoreCase = true) }) continue
            val millis = Abilities.cooldown(item) ?: continue
            loreNamed += name.lowercase()
            return Known(name, millis).also { known[name.lowercase()] = it }
        }
        return null
    }

    /** Reads the open container's items, once after it changes, and the worn set's charge from the player's own. */
    private fun read() {
        if (!stale) return
        stale = false
        marked = true
        val slots = container.slots
        for (i in own() until slots.size) {
            val item = slots[i] ?: continue
            for (ability in read(item).abilities) known[ability.name.lowercase()] = ability
        }
        if (container.id != INVENTORY || slots.size <= BOOTS) return
        val worn = ARMOR.map { slots[it]?.let(::read) }
        val bonus = worn.firstNotNullOfOrNull { it?.charge }
        // The server's count of worn pieces is the lore's, and a helmet that counts as any set's stands in for the set's own.
        charge = bonus?.takeIf { b ->
            val pieces = worn.count { it?.charge?.name == b.name } + if (worn[0]?.let { it.anySet && it.charge == null } == true) 1 else 0
            b.worn >= b.pieces || pieces >= b.pieces
        }
    }

    /**
     * The first slot of the player's own items in the open container: all of their inventory, and the last four rows of
     * a menu, so another player's items in their profile neither set a length nor take a mark.
     */
    private fun own() = if (container.id == INVENTORY) 0 else maxOf(0, container.slots.size - PLAYER_SLOTS)

    private fun read(item: Item): Read = reads.getOrPut(item) {
        if (reads.size > MAX_READS) reads.clear()
        val abilities = Abilities.of(item).map { Known(it.name, it.cooldownMillis) }
        val charge = Abilities.charge(item)
        Read(abilities.map { it.name.lowercase() } + listOfNotNull(charge?.name?.lowercase()), abilities, charge, Abilities.anySetHelmet(item))
    }

    override fun hud(now: Long): HudBlock? {
        read()
        if (!list.get()) return null
        val from = (shortest.get() * 1000).toLong()
        val shown = timers.values.filter { it.lore >= from && now - it.end < READY_MILLIS }.sortedBy { it.end }
        if (shown.isEmpty()) return null
        return block {
            for (timer in shown) {
                val left = timer.end - now
                if (left > 0) {
                    bar(timer.name, left.toFloat() / (timer.end - timer.start)) { value(seconds(left)) }
                } else {
                    row(timer.name) { good("Ready") }
                }
            }
        }
    }

    override fun slots(now: Long): Map<Int, SlotMark> {
        read()
        if (!onItems.get()) return emptyMap()
        if (!marked && now < marksUntil) return marks
        marked = false
        val next = HashMap<Int, SlotMark>()
        var until = Long.MAX_VALUE
        val slots = container.slots
        for (slot in own() until slots.size) {
            val item = slots[slot] ?: continue
            var timer: Timer? = null
            for (key in read(item).keys) {
                val t = timers[key] ?: continue
                if (now < t.end && (timer == null || t.end > timer.end)) timer = t
            }
            for (key in loreNamed) {
                val t = timers[key] ?: continue
                if (now >= t.end || timer != null && t.end <= timer.end || item.lore.none { it.plain.contains(t.name, ignoreCase = true) }) continue
                timer = t
            }
            timer ?: continue
            next[slot] = SlotMark.cooldown(timer.start, timer.end)
            until = minOf(until, timer.end)
        }
        marks = next
        marksUntil = until
        return marks
    }

    private companion object {
        const val INVENTORY = 0
        const val PLAYER_SLOTS = 36

        /** The inventory's helmet, chestplate, leggings and boots slots. */
        val ARMOR = listOf(5, 6, 7, 8)
        const val BOOTS = 8

        const val USED = "You used "
        val USED_LINE = Regex("""You used (.+?)[!.]?""")
        val SOULCRY = Regex("""(?:☠ )?Soulcry! \+.*""")
        const val SAME_USE_MILLIS = 500L
        const val SLACK_MILLIS = 1000L
        const val LEARN_MILLIS = 300L

        /** How long a ready ability stays on the list, and how late a ready alert may still come. */
        const val READY_MILLIS = 2000L
        const val MAX_READS = 512

        /** `3.2s` under ten seconds, `45s` under a minute, then `1m 48s`, each rounded up so Ready is never early. */
        fun seconds(millis: Long): String {
            if (millis < 10_000) return String.format(Locale.US, "%.1fs", ceil(millis / 100.0) / 10)
            val whole = ceil(millis / 1000.0).toLong()
            return if (whole < 60) "${whole}s" else "${whole / 60}m ${whole % 60}s"
        }
    }
}
