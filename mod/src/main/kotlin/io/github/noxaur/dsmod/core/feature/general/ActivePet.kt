package io.github.noxaur.dsmod.core.feature.general

import io.github.noxaur.dsmod.core.feature.Feature
import io.github.noxaur.dsmod.core.game.Area
import io.github.noxaur.dsmod.core.game.Sidebar
import io.github.noxaur.dsmod.core.hud.HudBlock
import io.github.noxaur.dsmod.core.text.Role
import kotlin.reflect.KMutableProperty0

/**
 * Shows the pet the player has out, from the sidebar's line `Pet: [Lvl 100] Ender Dragon`, with the name in the
 * color the server gives its rarity. A sidebar without the line, or with `Pet: None`, shows nothing, and an empty one,
 * as between worlds, keeps what was shown.
 */
class ActivePet(toggle: KMutableProperty0<Boolean>) : Feature("active-pet", toggle, Area.ANYWHERE) {
    private var block: HudBlock? = null

    init {
        on<Sidebar> { event, _ ->
            // Between worlds the sidebar is empty until the new one arrives, and the pet is still out.
            if (event.lines.all { it.plain.isBlank() }) return@on
            val line = event.lines.firstOrNull { it.plain.trimStart().startsWith(PREFIX) }
            val value = line?.plain?.trim()?.removePrefix(PREFIX)?.trim()
            val pet = value?.let(PET::matchEntire)
            if (value != null && pet == null && value != NONE) unparsed(line.plain)
            block = pet?.let { match ->
                val (level, name) = match.destructured
                // The name's span carries the rarity, so the last span with text is the one to read.
                val rarity = Role.rarity(line.spans.lastOrNull { it.text.isNotBlank() }?.color) ?: Role.COMMON
                block {
                    row("Pet") { rarity(name, rarity) }
                    row("Level") { value(level.toLong()) }
                }
            }
        }
    }

    override fun hud(now: Long): HudBlock? = block

    private companion object {
        const val PREFIX = "Pet:"
        const val NONE = "None"
        val PET = Regex("""\[Lvl (\d+)] (.+)""")
    }
}
