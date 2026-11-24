package io.github.noxaur.dsmod.core.feature.general

import io.github.noxaur.dsmod.core.config.DsModConfig
import io.github.noxaur.dsmod.core.config.Store
import io.github.noxaur.dsmod.core.feature.Feature
import io.github.noxaur.dsmod.core.feature.HasFeature
import io.github.noxaur.dsmod.core.game.Area
import io.github.noxaur.dsmod.core.game.ContainerOpened
import io.github.noxaur.dsmod.core.game.Item
import io.github.noxaur.dsmod.core.game.Pet
import io.github.noxaur.dsmod.core.game.PetCard
import io.github.noxaur.dsmod.core.game.PetItem
import io.github.noxaur.dsmod.core.game.PetProgress
import io.github.noxaur.dsmod.core.game.ServerSpan
import io.github.noxaur.dsmod.core.game.ServerText
import io.github.noxaur.dsmod.core.game.SlotChanged
import io.github.noxaur.dsmod.core.hud.HudBlock
import io.github.noxaur.dsmod.core.text.Role
import io.github.noxaur.dsmod.core.text.TextBuilder
import io.github.noxaur.dsmod.core.text.amount
import io.github.noxaur.dsmod.core.text.count
import io.github.noxaur.dsmod.core.text.duration
import io.github.noxaur.dsmod.core.text.perHour
import kotlinx.serialization.Serializable
import kotlin.math.roundToInt
import kotlin.math.roundToLong

@Serializable
data class PetIcon(val id: String = "minecraft:player_head", val skin: String? = null, val model: String? = null)

@Serializable
data class KnownPet(
    val icon: PetIcon? = null,
    val held: PetItem? = null,
    val rarity: Role? = null,
    val maxLevel: Int? = null,
    val category: String? = null,
)

@Serializable
data class KnownPets(val pets: Map<String, KnownPet> = emptyMap(), val latest: Map<String, String> = emptyMap())

/** @keep The sidebar's `Pet:` line says which pet is out and its level, live, and `Pet: None` that none is. A
 * dungeon's sidebar has no such line, so there the pet follows chat and the Pets menu. Chat follows a summon
 * (`Activated [Lvl 100] Black Cat!`), Autopet's swap and a new held item (`Equipped Gold Claws on [Lvl 100]
 * Black Cat!`). A combat pet gains XP as the player gains Combat XP, which the action bar shows behind each
 * kill, so from the second read on the Combat XP since the last read moves the XP live */
class ActivePet(config: DsModConfig) : HasFeature {
    override val feature = Feature(config, "active-pet", Area.ANYWHERE, Store.GENERAL, "Show your pet, its level, held item and XP on the HUD.", default = false, hud = ::hud)

    val showIcon = feature.switch("activePetIcon", "Icon", "Show your pet's head beside its name.", default = true)
    val showItem = feature.switch("activePetItem", "Held Item", "Show the pet item your pet holds.", default = true)
    val showXp = feature.switch("activePetXp", "XP", "Show how far your pet is to its next level.", default = true)
    val showRate = feature.switch("activePetRate", "XP Rate", "Show how fast your pet gains XP.", default = true)
    val showNext = feature.switch("activePetNext", "Next Level", "Show how long until your pet reaches its next level.", default = true)
    val hideAutopet = feature.switch("hideAutopet", "Hide Autopet", "Hide the chat line Autopet sends when it swaps your pet.", default = false)

    private val known = feature.profiled(KnownPets())

    private val pets = CurrentPet(feature, ::changed, ::menuSlot) { if (it == pet?.name) block = null }
    private val pet: Pet? get() = pets.pet

    private class Pace(val key: String, val level: Int, val total: Double, val xp: Boolean, val start: Long, val from: Double) {
        var readAt = start
        var read = from
        var combat = 0.0
        var before = 0.0
        var ratio: Double? = null
        var gainAt = start

        val live: Double get() = minOf(total, read + (ratio ?: 0.0) * combat)
        val latest: Long get() = if (ratio != null && combat > 0) maxOf(readAt, gainAt) else readAt

        val perMilli: Double? get() = (latest - start).takeIf { it > 0 }?.let { (live - from) / it }
    }

    private val progress = HashMap<String, Pair<Int, PetProgress>>()
    private val cards = HashMap<String, Item>()
    private var menuSlot = -1
    private var pace: Pace? = null

    private data class Shown(val icon: Boolean, val item: Boolean, val xp: Boolean, val rate: Boolean, val next: Boolean, val known: KnownPets)

    private var shown: Shown? = null
    private var block: HudBlock? = null

    private var nextShown: String? = null

    init {
        feature.on<ContainerOpened> { _, _ -> menuSlot = -1 }
        feature.on<SlotChanged> { event, _ ->
            val item = event.item
            if (item?.name?.plain == PetCard.MENU) item.lore.firstOrNull { it.plain.startsWith(SELECTED) }?.let { pets.read(it, SELECTED) }
        }
        OwnKills(feature, { true }) { _, xp, now ->
            val pace = pace?.takeIf { known.value.pets[it.key]?.category == COMBAT } ?: return@OwnKills
            pace.combat += xp ?: return@OwnKills
            pace.gainAt = now
            if (pace.ratio != null) block = null
        }
        feature.line("Activated ", ACTIVATED) { (level, name), _ -> summoned(name, count(level) ?: return@line) }
        feature.line(Regex(AUTOPET), { it }) { text, _ ->
            val match = AUTOPET_LINE.matchEntire(text)
            if (match == null) feature.unparsed(text) else match.destructured.let { (level, name) -> count(level)?.let { summoned(name, it) } }
            if (hideAutopet.value) feature.hide()
        }
        feature.line("Equipped ", EQUIPPED) { (item, _, name), _ ->
            remember(keyOf(name)) { it.copy(held = PetItem(item)) }
        }
    }

    private fun keyOf(name: String) = known.value.latest[name] ?: name

    private fun menuSlot(event: SlotChanged, card: PetCard?, now: Long) {
        if (event.slot == menuSlot && card == null) {
            menuSlot = -1
            /** @keep The active pet's item turning into a summon button is a despawn, which a dungeon's sidebar would not say.
             * Another pet's item there is the menu turning a page */
            if (event.item?.name?.plain?.let(Pet::nameOf) == pet?.name) pets.set(null)
        }
        if (card != null) read(card, event.slot, now)
    }

    private fun read(card: PetCard, slot: Int, now: Long) {
        menuSlot = slot
        card.unread?.let(feature::unparsed)
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
        pets.set(card.pet)
        block = null
        val progress = card.progress ?: return
        this.progress[key] = card.pet.level to progress
        if (progress.max) return
        val read = progress.xp
        val needed = progress.needed
        val xp = read != null && needed != null
        val total = needed.takeIf { xp } ?: 1.0
        val at = read.takeIf { xp } ?: progress.fraction.toDouble()
        val pace = pace
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

    private fun summoned(name: String, level: Int) {
        pace = null
        progress.remove(keyOf(name))
        block = null
        pets.set(Pet(name, level, known.value.pets[keyOf(name)]?.rarity ?: pet?.takeIf { it.name == name }?.rarity ?: Role.COMMON))
    }

    private fun changed(before: Pet?) {
        val next = pet
        if (next?.name != before?.name || next?.level != before?.level) pace = null
        block = null
        // @keep A summon in chat names no rarity, so each pet's is kept from where it was last seen in color
        if (next != null) remember(keyOf(next.name)) { it.copy(rarity = next.rarity) }
    }

    private fun remember(key: String, change: (KnownPet) -> KnownPet) {
        val before = known.value.pets[key] ?: KnownPet()
        val after = change(before)
        if (after != before) known.value = known.value.copy(pets = known.value.pets + (key to after))
        if (pet?.let { keyOf(it.name) } == key) block = null
    }

    private fun pace(pet: Pet, key: String) = pace?.takeIf { it.key == key && it.level == pet.level }

    private fun nextLevel(pace: Pace?, now: Long): String? {
        val perMilli = pace?.perMilli?.takeIf { it > 0 } ?: return null
        val left = (pace.total - pace.live) / perMilli - (now - pace.latest)
        return duration(left.coerceAtLeast(0.0).roundToLong(), nearest = true)
    }

    fun hud(now: Long): HudBlock? {
        val settings = Shown(showIcon.value, showItem.value, showXp.value, showRate.value, showNext.value, known.value)
        if (settings != shown) {
            shown = settings
            block = null
        }
        val pet = pet ?: return null
        val key = keyOf(pet.name)
        val pace = pace(pet, key)
        if (settings.next && nextLevel(pace, now) != nextShown) block = null
        block?.let { return it }
        val about = known.value.pets[key]
        val icon = if (!settings.icon) {
            null
        } else {
            cards[key] ?: about?.icon?.let { Item(it.id, 1, ServerText(ServerSpan(pet.name, pet.rarity.rgb)), skin = it.skin, model = it.model) }
                ?: pets.head(pet.name)
        }
        val read = progress[key]?.takeIf { it.first == pet.level }?.second ?: PetProgress.MAX.takeIf { about?.maxLevel == pet.level }
        nextShown = nextLevel(pace, now)
        return feature.block(headed = false, icon = icon) {
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
                            val perHour = perHour(pace.live - pace.from, pace.latest - pace.start)
                            if (pace.xp) value(amount(perHour, places = 0)) else value("${amount(perHour * 100, trim = true)}%")
                        }
                    }
                    if (settings.next) row("Next Level") { nextShown?.let { value(it) } ?: muted(NOT_GAINING) }
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
        const val SELECTED = "Selected Pet:"
        const val COMBAT = "COMBAT"
        const val MAX_LEVEL = "MAX LEVEL"
        const val OPEN_MENU = "Open your Pets menu to read its XP."
        const val READ_AGAIN = "Open your Pets menu again to see a rate."
        const val NOT_GAINING = "No XP gained yet"
        const val AUTOPET = "Autopet "
        val ACTIVATED = Regex("""Activated \[Lvl (\d+)] (.+)!""")
        val AUTOPET_LINE = Regex("""Autopet equipped your \[Lvl (\d+)] (.+?)!.*""")
        val EQUIPPED = Regex("""Equipped (.+) on \[Lvl (\d+)] (.+)!""")
    }
}
