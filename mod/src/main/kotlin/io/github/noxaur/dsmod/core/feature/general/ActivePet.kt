package io.github.noxaur.dsmod.core.feature.general

import io.github.noxaur.dsmod.core.container.SlotMark
import io.github.noxaur.dsmod.core.feature.Feature
import io.github.noxaur.dsmod.core.game.Area
import io.github.noxaur.dsmod.core.game.ContainerOpened
import io.github.noxaur.dsmod.core.game.Item
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
import io.github.noxaur.dsmod.core.text.amount
import kotlinx.serialization.Serializable
import kotlin.math.roundToInt
import kotlin.reflect.KMutableProperty0

/** How a pet's head looks, to draw it again in a later session: its item, a head's skin, and its model. */
@Serializable
data class PetIcon(val id: String = "minecraft:player_head", val skin: String? = null, val model: String? = null)

/** What the Pets menu last showed of a pet, by its name: its icon, the item it holds, its rarity, and the level it maxed at. */
@Serializable
data class KnownPet(val icon: PetIcon? = null, val held: PetItem? = null, val rarity: Role? = null, val maxLevel: Int? = null)

@Serializable
data class KnownPets(val pets: Map<String, KnownPet> = emptyMap())

/**
 * Shows the pet the player has out: its head, `[Lvl 100] Black Cat` in its rarity, the item it holds, and how far it
 * is to its next level.
 *
 * The sidebar's `Pet:` line says which pet is out and its level, live, and `Pet: None` that none is. A dungeon's
 * sidebar has no such line, so the last pet stays until another says otherwise. The Pets menu shows the active pet's
 * item, which gives the icon, the held item and the XP, and the chat lines `Activated [Lvl 100] Black Cat!` and
 * `Equipped Gold Claws on [Lvl 100] Black Cat!` follow a summon and a new held item. Until the menu was opened, the
 * icon is the head of a pet of that name floating near the player. The icon, the held item and the rarity are kept
 * by pet name between sessions, and the XP only for the level it was read at, since it rises in play.
 */
class ActivePet(
    toggle: KMutableProperty0<Boolean>,
    private val showIcon: KMutableProperty0<Boolean>,
    private val showItem: KMutableProperty0<Boolean>,
    private val showXp: KMutableProperty0<Boolean>,
    private val markInMenu: KMutableProperty0<Boolean>,
) : Feature("active-pet", toggle, Area.ANYWHERE) {
    private val known = saved(KnownPets())

    private var pet: Pet? = null

    // This session only: the XP each pet had at the level it was read at, its item in the Pets menu, whose tooltip the
    // HUD's icon shows, the head of each pet seen near the player, and the open Pets menu's slot of the active pet.
    private val progress = HashMap<String, Pair<Int, PetProgress>>()
    private val cards = HashMap<String, Item>()
    private val heads = HashMap<String, Item>()
    private var menuSlot = -1

    private data class Shown(val icon: Boolean, val item: Boolean, val xp: Boolean, val known: KnownPets)

    private var shown: Shown? = null
    private var block: HudBlock? = null

    init {
        on<Sidebar> { event, _ ->
            // Between worlds the sidebar is empty until the new one arrives, and the pet is still out.
            if (event.lines.all { it.plain.isBlank() }) return@on
            val line = event.lines.firstOrNull { it.plain.trimStart().startsWith(PREFIX) } ?: return@on
            val value = line.plain.trim().removePrefix(PREFIX).trim()
            if (value == NONE) return@on set(null)
            val read = Pet.of(line, value)
            if (read == null) unparsed(line.plain) else set(read)
        }
        on<ContainerOpened> { _, _ -> menuSlot = -1 }
        on<SlotChanged> { event, _ ->
            if (container.title?.plain != PetCard.MENU) return@on
            if (event.slot == menuSlot) menuSlot = -1
            val card = event.item?.let(PetCard::of) ?: return@on
            menuSlot = event.slot
            card.unread?.let(::unparsed)
            val name = card.pet.name
            card.progress?.let { progress[name] = card.pet.level to it }
            cards[name] = card.icon
            remember(name) {
                it.copy(icon = PetIcon(card.icon.id, card.icon.skin, card.icon.model), held = card.held, rarity = card.pet.rarity, maxLevel = card.pet.level.takeIf { card.progress?.max == true })
            }
            if (pet?.name != name) set(card.pet) else block = null
        }
        on<PetNear> { event, _ ->
            val name = Pet.nameOf(event.name.plain) ?: return@on
            if (heads.put(name, event.head) != event.head && name == pet?.name) block = null
        }
        line("Activated ", ACTIVATED) { (level, name), _ ->
            set(Pet(name, level.toInt(), known.value.pets[name]?.rarity ?: pet?.takeIf { it.name == name }?.rarity ?: Role.COMMON))
        }
        line("Equipped ", EQUIPPED) { (item, _, name), _ ->
            remember(name) { it.copy(held = PetItem(item)) }
        }
    }

    private fun set(next: Pet?) {
        if (next == pet) return
        pet = next
        block = null
        // A summon in chat names no rarity, so each pet's is kept from where it was last seen in color.
        if (next != null) remember(next.name) { it.copy(rarity = next.rarity) }
    }

    private fun remember(name: String, change: (KnownPet) -> KnownPet) {
        val before = known.value.pets[name] ?: KnownPet()
        val after = change(before)
        if (after != before) known.value = KnownPets(known.value.pets + (name to after))
        if (name == pet?.name) block = null
    }

    override fun slots(now: Long): Map<Int, SlotMark> =
        if (markInMenu.get() && menuSlot >= 0 && container.title?.plain == PetCard.MENU) mapOf(menuSlot to MARK) else emptyMap()

    override fun hud(now: Long): HudBlock? {
        val settings = Shown(showIcon.get(), showItem.get(), showXp.get(), known.value)
        if (settings != shown) {
            shown = settings
            block = null
        }
        val pet = pet ?: return null
        block?.let { return it }
        val about = known.value.pets[pet.name]
        val icon = if (!settings.icon) {
            null
        } else {
            cards[pet.name] ?: about?.icon?.let { Item(it.id, 1, ServerText(ServerSpan(pet.name, pet.rarity.rgb)), skin = it.skin, model = it.model) }
                ?: heads[pet.name]
        }
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
                val read = progress[pet.name]?.takeIf { it.first == pet.level }?.second
                    ?: PetProgress.MAX.takeIf { about?.maxLevel == pet.level }
                when {
                    read == null -> line { muted(OPEN_MENU) }
                    read.max -> row("XP") { good(MAX_LEVEL) }
                    else -> bar("XP", read.fraction) {
                        val percent = "${(read.fraction * 100).roundToInt()}%"
                        if (read.xp != null && read.needed != null) {
                            value(amount(read.xp))
                            muted(" / ${amount(read.needed)} ($percent)")
                        } else {
                            value(percent)
                        }
                    }
                }
            }
        }.also { block = it }
    }

    private companion object {
        const val PREFIX = "Pet:"
        const val NONE = "None"
        const val MAX_LEVEL = "MAX LEVEL"
        const val OPEN_MENU = "Open your Pets menu to read its XP."
        val ACTIVATED = Regex("""Activated \[Lvl (\d+)] (.+)!""")
        val EQUIPPED = Regex("""Equipped (.+) on \[Lvl (\d+)] (.+)!""")
        val MARK = SlotMark(Role.GOOD)
    }
}
