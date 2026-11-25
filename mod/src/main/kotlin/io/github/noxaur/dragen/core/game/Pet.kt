package io.github.noxaur.dragen.core.game

import io.github.noxaur.dragen.core.text.Role
import io.github.noxaur.dragen.core.text.count

// @keep The sidebar writes the active pet as `Pet: [Lvl 100] Black Cat`, and `Pet: None` when none is out
data class Pet(val name: String, val level: Int, val rarity: Role) {
    companion object {
        val LINE = Regex("""\[Lvl (\d+)] (.+)""")

        fun of(text: ServerText, plain: String = text.plain.trim()): Pet? {
            val (level, name) = LINE.matchEntire(plain)?.destructured ?: return null
            val color = text.spans.lastOrNull { it.text.isNotBlank() }?.color
            return Pet(name, count(level) ?: return null, Role.rarity(color) ?: Role.COMMON)
        }

        fun nameOf(plain: String) = LINE.matchEntire(plain.trim())?.groupValues?.get(2)
    }
}

data class PetItem(val name: String = "", val rarity: Role? = null)

/** @keep Its lore names the item it holds under `Held Item`, then `LEVEL 100` and either `MAX LEVEL` or how far it is
 * to the next level, and ends in its rarity and category, as `LEGENDARY COMBAT PET`. dragsim.co draws a rule
 * after `Held Item` and a bar before `LEVEL 100` with its resource pack's glyphs, which sit in Unicode's
 * private use area, so each line is read without them */
data class PetCard(
    val pet: Pet,
    val icon: Item,
    val held: PetItem?,
) {
    companion object {
        const val MENU = "Pets"
        const val ACTIVE = "Click to deactivate!"
        val FOOTER = Regex("""(VERY SPECIAL|SPECIAL|DIVINE|MYTHIC|LEGENDARY|EPIC|RARE|UNCOMMON|COMMON)\s*([A-Z ]+?) PET""")

        fun rarity(item: Item): Role? {
            val rarity = item.cleanLore.firstNotNullOfOrNull { FOOTER.matchEntire(it) }?.groupValues?.get(1) ?: return null
            return Role.RARITIES.firstOrNull { it.name == rarity }
        }
    }
}
