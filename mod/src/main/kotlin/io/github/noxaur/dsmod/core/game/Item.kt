package io.github.noxaur.dsmod.core.game

import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

/**
 * An item stack as the server sent it. [name] and [lore] are the custom name and lore, which the server uses
 * for everything the player reads, and [data] is the whole `custom_data`, where the server keeps what it knows.
 * The rest is how the item looks, so the mod can draw it as the server does: [skin] is a head's texture, the last
 * part of its `textures.minecraft.net` address, [dye] is dyed leather's color, [glint] overrides the enchantment
 * shine, and [style] is the tooltip's frame, such as `dragsim:legendary`. [foil] is whether the game draws the shine
 * at all, from enchantments or the override, and [model] is an `item_model` other than [id]'s own. A capture keeps
 * each only when it is set.
 */
@Serializable
data class Item(
    val id: String,
    val count: Int,
    val name: ServerText? = null,
    val lore: List<ServerText> = emptyList(),
    val data: JsonObject = JsonObject(emptyMap()),
    val skin: String? = null,
    @Serializable(with = HexColor::class) val dye: Int? = null,
    val glint: Boolean? = null,
    val style: String? = null,
    val foil: Boolean = false,
    val model: String? = null,
) {
    @Transient private var cleanLoreRead: List<String>? = null

    val cleanLore: List<String>
        get() = cleanLoreRead ?: lore.map { withoutGlyphs(it.plain) }.also { cleanLoreRead = it }

    /** The rarity in `item_data`, such as `LEGENDARY`, or null for an item with none. */
    val rarity: String? get() = string("item_data", "rarity")

    fun section(header: String): List<String> {
        val lines = cleanLore
        val start = lines.indexOf(header) + 1
        if (start == 0) return emptyList()
        var end = start
        while (end < lines.size && lines[end].isNotEmpty()) end++
        return lines.subList(start, end)
    }

    inline fun sections(header: Regex, each: (heading: MatchResult, lines: List<String>) -> Unit) {
        val lines = cleanLore
        var i = 0
        while (i < lines.size) {
            val heading = header.matchEntire(lines[i++]) ?: continue
            val start = i
            while (i < lines.size && lines[i].isNotEmpty() && !header.matches(lines[i])) i++
            each(heading, lines.subList(start, i))
        }
    }

    fun string(vararg path: String): String? = (at(path) as? JsonPrimitive)?.takeIf { it.isString }?.contentOrNull

    fun objectAt(vararg path: String): JsonObject? = at(path) as? JsonObject

    private fun at(path: Array<out String>): JsonElement? {
        var at: JsonElement? = data
        for (key in path) at = (at as? JsonObject)?.get(key)
        return at
    }
}

private val GLYPHS = ''..''

private fun withoutGlyphs(line: String) = if (line.none { it in GLYPHS }) line.trim() else line.filterNot { it in GLYPHS }.trim()
