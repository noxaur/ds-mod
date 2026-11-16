package io.github.noxaur.dsmod.core.game

import io.github.noxaur.dsmod.core.text.Role
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

/**
 * A pet as dragsim.co names it everywhere: `[Lvl 100] Black Cat`, with the name in its rarity's color. The sidebar
 * writes the active one as `Pet: [Lvl 100] Black Cat`, and `Pet: None` when none is out.
 */
data class Pet(val name: String, val level: Int, val rarity: Role) {
    companion object {
        val LINE = Regex("""\[Lvl (\d+)] (.+)""")

        /** The pet [text] names, its rarity from the color of the last span with text, which is the name's. */
        fun of(text: ServerText, plain: String = text.plain.trim()): Pet? {
            val (level, name) = LINE.matchEntire(plain)?.destructured ?: return null
            val color = text.spans.lastOrNull { it.text.isNotBlank() }?.color
            return Pet(name, level.toIntOrNull() ?: return null, Role.rarity(color) ?: Role.COMMON)
        }

        /** The name of the pet [plain] names, without its level, which goes up in play. */
        fun nameOf(plain: String) = LINE.matchEntire(plain.trim())?.groupValues?.get(2)
    }
}

/** The pet item a pet holds, such as Gold Claws, in its rarity when the server gave one. */
@Serializable
data class PetItem(val name: String = "", val rarity: Role? = null)

/** How far a pet is through its level: [MAX] at its last, or [fraction] from 0 to 1 with the XP when the menu writes it. */
data class PetProgress(val fraction: Float, val xp: Double? = null, val needed: Double? = null) {
    val max: Boolean get() = this === MAX

    companion object {
        val MAX = PetProgress(1f)
    }
}

/**
 * The active pet's item in the Pets menu: the only pet whose lore says [ACTIVE]. Its lore names the item it holds
 * under `Held Item`, then `LEVEL 100` and either `MAX LEVEL` or how far it is to the next level, and ends in its
 * rarity and [category], as `LEGENDARY COMBAT PET`. dragsim.co draws a rule after `Held Item` and a bar before
 * `LEVEL 100` with its resource pack's glyphs, which sit in Unicode's private use area, so each line is read without
 * them. [key] tells this pet from another of its name: the item's uuid when the server sends one, else the name.
 */
data class PetCard(
    val pet: Pet,
    val icon: Item,
    val held: PetItem?,
    val progress: PetProgress?,
    val unread: String? = null,
    val key: String = pet.name,
    val category: String? = null,
) {
    companion object {
        const val MENU = "Pets"

        /** The line under the active pet in the Pets menu, which no other pet has. */
        const val ACTIVE = "Click to deactivate!"

        private const val HELD = "Held Item"
        private const val MAX_LEVEL = "MAX LEVEL"
        private val LEVEL = Regex("""LEVEL \d+""")
        private val XP = Regex("""([\d,.]+)\s*/\s*([\d,.]+)""")
        private val PERCENT = Regex("""(\d+(?:\.\d+)?)%""")
        private val FOOTER = Regex("""(?:VERY SPECIAL|SPECIAL|DIVINE|MYTHIC|LEGENDARY|EPIC|RARE|UNCOMMON|COMMON)\s*([A-Z ]+?) PET""")

        /**
         * The card [item] is, when it is the active pet in the Pets menu. The lines between its level and the click
         * hint are read leniently, since only a pet at its max level was seen: an `xp/needed` pair, or else a percent.
         * A pet below its max level whose lines hold neither has a null progress and those lines in [unread].
         */
        fun of(item: Item): PetCard? {
            val lore = item.lore.map { clean(it.plain) }
            if (ACTIVE !in lore) return null
            val pet = item.name?.let(Pet::of) ?: return null
            val key = uuid(item) ?: pet.name
            val category = lore.firstNotNullOfOrNull { FOOTER.matchEntire(it)?.groupValues?.get(1)?.trim() }
            val heldAt = lore.indexOf(HELD)
            val held = item.lore.getOrNull(heldAt + 1)?.takeIf { heldAt >= 0 && lore[heldAt + 1].isNotEmpty() }?.let {
                PetItem(lore[heldAt + 1], Role.rarity(it.spans.lastOrNull { span -> span.text.isNotBlank() }?.color))
            }
            val levelAt = lore.indexOfFirst { LEVEL.matches(it) }
            if (levelAt < 0) return PetCard(pet, item, held, null, key = key, category = category)
            val lines = lore.drop(levelAt + 1)
                .takeWhile { it != "ACTIVE" && !it.startsWith("Click") }
                .filter { it.isNotEmpty() }
            if (MAX_LEVEL in lines) return PetCard(pet, item, held, PetProgress.MAX, key = key, category = category)
            val xp = lines.firstNotNullOfOrNull { XP.find(it) }?.destructured?.let { (have, need) ->
                val a = have.replace(",", "").toDoubleOrNull()
                val b = need.replace(",", "").toDoubleOrNull()
                if (a != null && b != null && b > 0) PetProgress((a / b).toFloat().coerceIn(0f, 1f), a, b) else null
            }
            val percent = lines.firstNotNullOfOrNull { PERCENT.find(it) }?.groupValues?.get(1)?.toFloatOrNull()
                ?.let { PetProgress((it / 100f).coerceIn(0f, 1f)) }
            val progress = xp ?: percent
            val unread = if (progress == null && lines.isNotEmpty()) lines.joinToString(" / ") else null
            return PetCard(pet, item, held, progress, unread, key, category)
        }

        /** The uuid dragsim.co gives each pet item in `extra.item_uuid`, as on a pet in the Auction House. */
        private fun uuid(item: Item) =
            (((item.data["extra"] as? JsonObject)?.get("item_uuid") as? JsonObject)?.get("uuid") as? JsonPrimitive)?.contentOrNull

        private fun clean(line: String) = line.filterNot { it in '\uE000'..'\uF8FF' }.trim()
    }
}

/**
 * The Combat XP the action bar says the player just gained, as `+40 Combat (12.5%)`. The server sends it right behind
 * the death of a mob the player killed, within [KILL_WINDOW_MILLIS], and repeats it for about a second after.
 */
object CombatXp {
    const val MARK = " Combat ("
    const val KILL_WINDOW_MILLIS = 20L
    private val GAIN = Regex("""\+([\d,.]+) Combat \(""")

    fun gained(plain: String): Double? = GAIN.find(plain)?.groupValues?.get(1)?.replace(",", "")?.toDoubleOrNull()
}
