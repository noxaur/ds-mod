package io.github.noxaur.dsmod.core.feature.general

import io.github.noxaur.dsmod.core.feature.Feature
import io.github.noxaur.dsmod.core.game.Area
import io.github.noxaur.dsmod.core.game.Item
import io.github.noxaur.dsmod.core.game.PetNear
import io.github.noxaur.dsmod.core.game.Sidebar
import io.github.noxaur.dsmod.core.hud.HudBlock
import io.github.noxaur.dsmod.core.text.Role
import kotlin.reflect.KMutableProperty0

/** How Active Pet names the pet. */
enum class PetLook(private val label: String) {
    ICON("Icon"),
    NAME("Name"),
    ;

    override fun toString() = label
}

/**
 * Shows the pet the player has out, from the sidebar's line `Pet: [Lvl 100] Ender Dragon`. [look] picks between the
 * pet's head, as it floats beside the player, and its name in the color the server gives its rarity. The icon is
 * the head of the nearest pet whose name is the sidebar's, and until one is seen the name shows instead. A sidebar
 * without the line, or with `Pet: None`, shows nothing, and an empty one, as between worlds, keeps what was shown.
 */
class ActivePet(toggle: KMutableProperty0<Boolean>, private val look: KMutableProperty0<PetLook>) :
    Feature("active-pet", toggle, Area.ANYWHERE) {
    private class Pet(val name: String, val level: Long, val rarity: Role)

    private var pet: Pet? = null

    // The head of the last pet seen near the player, by the pet's name without its level, which goes up in play.
    private var head: Pair<String, Item>? = null
    private var shownLook = look.get()
    private var block: HudBlock? = null

    init {
        on<Sidebar> { event, _ ->
            // Between worlds the sidebar is empty until the new one arrives, and the pet is still out.
            if (event.lines.all { it.plain.isBlank() }) return@on
            val line = event.lines.firstOrNull { it.plain.trimStart().startsWith(PREFIX) }
            val value = line?.plain?.trim()?.removePrefix(PREFIX)?.trim()
            val match = value?.let(PET::matchEntire)
            if (value != null && match == null && value != NONE) unparsed(line.plain)
            pet = match?.let {
                val (level, name) = it.destructured
                // The name's span carries the rarity, so the last span with text is the one to read.
                Pet(name, level.toLong(), Role.rarity(line.spans.lastOrNull { span -> span.text.isNotBlank() }?.color) ?: Role.COMMON)
            }
            block = build()
        }
        on<PetNear> { event, _ ->
            val name = PET.matchEntire(event.name.plain.trim())?.groupValues?.get(2) ?: return@on
            head = name to event.head
            block = build()
        }
    }

    override fun hud(now: Long): HudBlock? {
        if (shownLook != look.get()) {
            shownLook = look.get()
            block = build()
        }
        return block
    }

    private fun build(): HudBlock? {
        val pet = pet ?: return null
        val icon = head?.takeIf { shownLook == PetLook.ICON && it.first == pet.name }?.second
        return block {
            if (icon != null) items(listOf(icon)) else row("Pet") { rarity(pet.name, pet.rarity) }
            row("Level") { value(pet.level) }
        }
    }

    private companion object {
        const val PREFIX = "Pet:"
        const val NONE = "None"
        val PET = Regex("""\[Lvl (\d+)] (.+)""")
    }
}
