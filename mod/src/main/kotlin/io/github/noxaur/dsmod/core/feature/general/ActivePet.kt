package io.github.noxaur.dsmod.core.feature.general

import io.github.noxaur.dsmod.core.container.SlotMark
import io.github.noxaur.dsmod.core.config.DsModConfig
import io.github.noxaur.dsmod.core.config.Store
import io.github.noxaur.dsmod.core.feature.Feature
import io.github.noxaur.dsmod.core.game.ActionBar
import io.github.noxaur.dsmod.core.game.Area
import io.github.noxaur.dsmod.core.game.CombatXp
import io.github.noxaur.dsmod.core.game.ContainerOpened
import io.github.noxaur.dsmod.core.game.EntityDied
import io.github.noxaur.dsmod.core.game.Item
import io.github.noxaur.dsmod.core.game.LeftServer
import io.github.noxaur.dsmod.core.game.Pet
import io.github.noxaur.dsmod.core.game.PetCard
import io.github.noxaur.dsmod.core.game.PetItem
import io.github.noxaur.dsmod.core.game.PetNear
import io.github.noxaur.dsmod.core.game.PetProgress
import io.github.noxaur.dsmod.core.game.ServerSpan
import io.github.noxaur.dsmod.core.game.ServerText
import io.github.noxaur.dsmod.core.game.Sidebar
import io.github.noxaur.dsmod.core.game.SlotChanged
import io.github.noxaur.dsmod.core.hud.HudBlock
import io.github.noxaur.dsmod.core.text.Role
import io.github.noxaur.dsmod.core.text.TextBuilder
import io.github.noxaur.dsmod.core.text.amount
import io.github.noxaur.dsmod.core.text.duration
import kotlinx.serialization.Serializable
import kotlin.math.roundToInt
import kotlin.math.roundToLong

/** How a pet's head looks, to draw it again in a later session: its item, a head's skin, and its model. */
@Serializable
data class PetIcon(val id: String = "minecraft:player_head", val skin: String? = null, val model: String? = null)

/**
 * What the Pets menu last showed of one pet: its icon, the item it holds, its rarity, the level it maxed at, and its
 * category, such as `COMBAT`.
 */
@Serializable
data class KnownPet(
    val icon: PetIcon? = null,
    val held: PetItem? = null,
    val rarity: Role? = null,
    val maxLevel: Int? = null,
    val category: String? = null,
)

/** Each pet by its key, a uuid or its name, and the key of the pet of each name that was last active. */
@Serializable
data class KnownPets(val pets: Map<String, KnownPet> = emptyMap(), val latest: Map<String, String> = emptyMap())

/**
 * Shows the pet the player has out: its head, `[Lvl 100] Black Cat` in its rarity, the item it holds, how far it is to
 * its next level, how fast it gets there, and when it will.
 *
 * The sidebar's `Pet:` line says which pet is out and its level, live, and `Pet: None` that none is, as does the
 * `Selected Pet:` line of the Pets button in the Dragon Sim Menu. A dungeon's sidebar has no such line, so there the
 * pet follows chat and the Pets menu. The Pets menu shows the active pet's item, which gives the icon, the held item
 * and the XP. Chat follows a summon (`Activated [Lvl 100] Black Cat!`), Autopet's swap and a new held item (`Equipped
 * Gold Claws on [Lvl 100] Black Cat!`). Until the menu was opened, the icon is the head of a pet of that name floating
 * near the player. What the menu showed is kept by pet between sessions, keyed by the item's uuid when the server
 * sends one and else by the pet's name, so two pets of one name share it.
 *
 * Each read of the menu is an exact XP point in time. Two reads at one level give the rate, and from it the time to
 * the next level. A combat pet gains XP as the player gains Combat XP, which the action bar shows behind each kill, so
 * from the second read on the Combat XP since the last read moves the XP live, at the rate of pet XP per Combat XP the
 * reads showed, and each read sets it right again. A switch of pet and a level up start over.
 */
class ActivePet(config: DsModConfig) :
    Feature(config, "active-pet", Area.ANYWHERE, Store.GENERAL, "Show your pet, its level, held item and XP on the HUD.", default = false) {
    val showIcon = switch("activePetIcon", "Icon", "Show your pet's head beside its name.", default = true)
    val showItem = switch("activePetItem", "Held Item", "Show the pet item your pet holds.", default = true)
    val showXp = switch("activePetXp", "XP", "Show how far your pet is to its next level.", default = true)
    val showRate = switch("activePetRate", "XP Rate", "Show how fast your pet gains XP.", default = true)
    val showNext = switch("activePetNext", "Next Level", "Show how long until your pet reaches its next level.", default = true)
    val markInMenu = switch("markActivePet", "Mark In Menu", "Mark your active pet in the Pets menu.", default = true)
    val hideAutopet = switch("hideAutopet", "Hide Autopet", "Hide the chat line Autopet sends when it swaps your pet.", default = false)

    private val known = saved(KnownPets())

    private var pet: Pet? = null

    /**
     * The XP of the pet [key] at [level] since its first read: [from] at [start], and the last read, [read] at [readAt].
     * [total] is the XP the level needs, or 1 when the menu writes only a percent. [combat] is the Combat XP since the
     * last read and [before] the Combat XP between the first and the last, which give [ratio], the pet XP per Combat XP.
     */
    private class Pace(val key: String, val level: Int, val total: Double, val xp: Boolean, val start: Long, val from: Double) {
        var readAt = start
        var read = from
        var combat = 0.0
        var before = 0.0
        var ratio: Double? = null
        var gainAt = start

        val live: Double get() = minOf(total, read + (ratio ?: 0.0) * combat)
        val latest: Long get() = if (ratio != null && combat > 0) maxOf(readAt, gainAt) else readAt

        /** XP per millisecond, once a second read or a live gain is in. */
        val perMilli: Double? get() = (latest - start).takeIf { it > 0 }?.let { (live - from) / it }
    }

    // This session only: the XP each pet had at the level it was read at, its item in the Pets menu, whose tooltip the
    // HUD's icon shows, the head of each pet seen near the player, the open Pets menu's slot of the active pet, the
    // current pet's pace, and when the last mob died.
    private val progress = HashMap<String, Pair<Int, PetProgress>>()
    private val cards = HashMap<String, Item>()
    private val heads = HashMap<String, Item>()
    private var menuSlot = -1
    private var pace: Pace? = null
    private var died = -1L

    private data class Shown(val icon: Boolean, val item: Boolean, val xp: Boolean, val rate: Boolean, val next: Boolean, val known: KnownPets)

    private var shown: Shown? = null
    private var block: HudBlock? = null

    // The minutes to the next level the block shows, so it is rebuilt when they tick down.
    private var nextShown = -1L

    init {
        on<Sidebar> { event, _ ->
            // Between worlds the sidebar is empty until the new one arrives, and the pet is still out.
            if (event.lines.all { it.plain.isBlank() }) return@on
            event.lines.firstOrNull { it.plain.trimStart().startsWith(SIDEBAR) }?.let { read(it, SIDEBAR) }
        }
        on<ContainerOpened> { _, _ -> menuSlot = -1 }
        // The next session may bring another pet, and the sidebar names it only when it arrives.
        on<LeftServer> { _, _ -> set(null) }
        on<SlotChanged> { event, now ->
            val item = event.item
            if (item?.name?.plain == PetCard.MENU) item.lore.firstOrNull { it.plain.startsWith(SELECTED) }?.let { read(it, SELECTED) }
            if (container.title?.plain != PetCard.MENU) return@on
            val card = item?.let(PetCard::of)
            if (event.slot == menuSlot && card == null) {
                menuSlot = -1
                // The active pet's item turning into a summon button is a despawn, which a dungeon's sidebar would not say.
                // Another pet's item there is the menu turning a page.
                if (item?.name?.plain?.let(Pet::nameOf) == pet?.name) set(null)
            }
            if (card != null) read(card, event.slot, now)
        }
        on<PetNear> { event, _ ->
            val name = Pet.nameOf(event.name.plain) ?: return@on
            if (heads.put(name, event.head) != event.head && name == pet?.name) block = null
        }
        on<EntityDied> { _, now -> died = now }
        on<ActionBar> { bar, now ->
            val text = bar.text.plain
            if (CombatXp.MARK !in text || died < 0 || now - died !in 0..CombatXp.KILL_WINDOW_MILLIS) return@on
            died = -1
            val pace = pace?.takeIf { known.value.pets[it.key]?.category == COMBAT } ?: return@on
            val gain = CombatXp.gained(text) ?: return@on unparsed(text)
            pace.combat += gain
            pace.gainAt = now
            if (pace.ratio != null) block = null
        }
        line("Activated ", ACTIVATED) { (level, name), _ -> summoned(name, level.toInt()) }
        line(Regex(AUTOPET), { it }) { text, _ ->
            val match = AUTOPET_LINE.matchEntire(text)
            if (match == null) unparsed(text) else match.destructured.let { (level, name) -> summoned(name, level.toInt()) }
            if (hideAutopet.value) hide()
        }
        line("Equipped ", EQUIPPED) { (item, _, name), _ ->
            remember(keyOf(name)) { it.copy(held = PetItem(item)) }
        }
    }

    /** The key of the pet of [name] that was last active. */
    private fun keyOf(name: String) = known.value.latest[name] ?: name

    /** Reads a `Pet:` or `Selected Pet:` line: a pet, or `None`. */
    private fun read(line: ServerText, prefix: String) {
        val value = line.plain.trim().removePrefix(prefix).trim()
        if (value == NONE) return set(null)
        val read = Pet.of(line, value)
        if (read == null) unparsed(line.plain) else set(read)
    }

    private fun read(card: PetCard, slot: Int, now: Long) {
        menuSlot = slot
        card.unread?.let(::unparsed)
        val key = card.key
        val name = card.pet.name
        if (known.value.latest[name] != key) known.value = known.value.copy(latest = known.value.latest + (name to key))
        cards[key] = card.icon
        remember(key) {
            it.copy(
                icon = PetIcon(card.icon.id, card.icon.skin, card.icon.model),
                held = card.held,
                rarity = card.pet.rarity,
                maxLevel = card.pet.level.takeIf { card.progress?.max == true },
                category = card.category ?: it.category,
            )
        }
        // The menu is as live as the sidebar, and the only word on the level where a dungeon's sidebar names no pet.
        set(card.pet)
        block = null
        val progress = card.progress ?: return
        this.progress[key] = card.pet.level to progress
        if (progress.max) return
        val xp = progress.xp != null && progress.needed != null
        val total = if (xp) progress.needed!! else 1.0
        val at = if (xp) progress.xp!! else progress.fraction.toDouble()
        val pace = pace
        // Less XP than the last read is another pet of the same name, summoned in the menu.
        if (pace == null || pace.key != key || pace.level != card.pet.level || pace.total != total || pace.xp != xp || at < pace.read) {
            this.pace = Pace(key, card.pet.level, total, xp, now, at)
            return
        }
        pace.before += pace.combat
        pace.combat = 0.0
        if (pace.before > 0 && at > pace.from) pace.ratio = (at - pace.from) / pace.before
        pace.read = at
        pace.readAt = now
    }

    /**
     * A summon in chat, or Autopet's. A pet of the same name and level may be another one, so its XP is read again and
     * its rate starts over.
     */
    private fun summoned(name: String, level: Int) {
        pace = null
        progress.remove(keyOf(name))
        block = null
        set(Pet(name, level, known.value.pets[keyOf(name)]?.rarity ?: pet?.takeIf { it.name == name }?.rarity ?: Role.COMMON))
    }

    private fun set(next: Pet?) {
        if (next == pet) return
        if (next?.name != pet?.name || next?.level != pet?.level) pace = null
        pet = next
        block = null
        // A summon in chat names no rarity, so each pet's is kept from where it was last seen in color.
        if (next != null) remember(keyOf(next.name)) { it.copy(rarity = next.rarity) }
    }

    private fun remember(key: String, change: (KnownPet) -> KnownPet) {
        val before = known.value.pets[key] ?: KnownPet()
        val after = change(before)
        if (after != before) known.value = known.value.copy(pets = known.value.pets + (key to after))
        if (pet?.let { keyOf(it.name) } == key) block = null
    }

    override fun slots(now: Long): Map<Int, SlotMark> =
        if (markInMenu.value && menuSlot >= 0 && container.title?.plain == PetCard.MENU) mapOf(menuSlot to MARK) else emptyMap()

    /** The current pet's pace, when one was read at the level it is now. */
    private fun pace(pet: Pet, key: String) = pace?.takeIf { it.key == key && it.level == pet.level }

    /** Whole minutes to the next level at [pace]'s rate, or -1 with no rate to tell it. */
    private fun minutesToNext(pace: Pace?, now: Long): Long {
        val perMilli = pace?.perMilli?.takeIf { it > 0 } ?: return -1
        val left = (pace.total - pace.live) / perMilli - (now - pace.latest)
        return (left.coerceAtLeast(0.0) / MILLIS_PER_MINUTE).roundToLong()
    }

    override fun hud(now: Long): HudBlock? {
        val settings = Shown(showIcon.value, showItem.value, showXp.value, showRate.value, showNext.value, known.value)
        if (settings != shown) {
            shown = settings
            block = null
        }
        val pet = pet ?: return null
        val key = keyOf(pet.name)
        val pace = pace(pet, key)
        if (settings.next && minutesToNext(pace, now) != nextShown) block = null
        block?.let { return it }
        val about = known.value.pets[key]
        val icon = if (!settings.icon) {
            null
        } else {
            cards[key] ?: about?.icon?.let { Item(it.id, 1, ServerText(ServerSpan(pet.name, pet.rarity.rgb)), skin = it.skin, model = it.model) }
                ?: heads[pet.name]
        }
        val read = progress[key]?.takeIf { it.first == pet.level }?.second ?: PetProgress.MAX.takeIf { about?.maxLevel == pet.level }
        nextShown = minutesToNext(pace, now)
        return block(headed = false, icon = icon) {
            line {
                muted("[Lvl ${pet.level}] ")
                rarity(pet.name, pet.rarity)
            }
            val held = about?.held
            if (settings.item && held != null) {
                row("Item") { if (held.rarity != null) rarity(held.name, held.rarity) else value(held.name) }
            }
            if (settings.xp) {
                when {
                    read == null -> line { muted(OPEN_MENU) }
                    read.max -> row("XP") { good(MAX_LEVEL) }
                    pace != null -> {
                        val fraction = (pace.live / pace.total).toFloat()
                        bar("XP", fraction) { xp(pace.xp, pace.live, pace.total, fraction) }
                    }
                    else -> bar("XP", read.fraction) { xp(read.xp != null && read.needed != null, read.xp ?: 0.0, read.needed ?: 1.0, read.fraction) }
                }
            }
            if ((settings.rate || settings.next) && read != null && !read.max) {
                val perMilli = pace?.perMilli
                if (pace == null || perMilli == null) {
                    line { muted(READ_AGAIN) }
                } else {
                    if (settings.rate) {
                        row("XP per Hour") {
                            val perHour = perMilli * MILLIS_PER_HOUR
                            if (pace.xp) value(amount(perHour, places = 0)) else value("${amount(perHour * 100, trim = true)}%")
                        }
                    }
                    if (settings.next) row("Next Level") { if (nextShown < 0) muted(NOT_GAINING) else value(duration(nextShown * MILLIS_PER_MINUTE.toLong())) }
                }
            }
        }.also { block = it }
    }

    private fun TextBuilder.xp(exact: Boolean, xp: Double, total: Double, fraction: Float) {
        val percent = "${(fraction * 100).roundToInt()}%"
        if (exact) {
            value(amount(xp, places = 0))
            muted(" / ${amount(total)} ($percent)")
        } else {
            value(percent)
        }
    }

    private companion object {
        const val SIDEBAR = "Pet:"
        const val SELECTED = "Selected Pet:"
        const val NONE = "None"
        const val COMBAT = "COMBAT"
        const val MAX_LEVEL = "MAX LEVEL"
        const val OPEN_MENU = "Open your Pets menu to read its XP."
        const val READ_AGAIN = "Open your Pets menu again to see a rate."
        const val NOT_GAINING = "No XP gained yet"
        const val AUTOPET = "Autopet "
        const val MILLIS_PER_MINUTE = 60_000.0
        const val MILLIS_PER_HOUR = 3_600_000.0
        val ACTIVATED = Regex("""Activated \[Lvl (\d+)] (.+)!""")
        val AUTOPET_LINE = Regex("""Autopet equipped your \[Lvl (\d+)] (.+?)!.*""")
        val EQUIPPED = Regex("""Equipped (.+) on \[Lvl (\d+)] (.+)!""")
        val MARK = SlotMark(Role.GOOD)
    }
}
