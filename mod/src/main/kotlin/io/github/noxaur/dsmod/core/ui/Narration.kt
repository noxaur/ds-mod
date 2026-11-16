package io.github.noxaur.dsmod.core.ui

/** What the narrator says for the mod's own controls: short parts joined with commas, as vanilla's narration reads. */
object Narration {
    fun join(vararg parts: String?) = parts.filterNot { it.isNullOrEmpty() }.joinToString(", ")

    /** Where an item is among [size], as "2 of 3". */
    fun of(index: Int, size: Int) = "${index + 1} of $size"

    /** An item of a menu, with where it is and whether it is the value held now. */
    fun item(label: String, index: Int, size: Int, current: Boolean) = join(label, of(index, size), "current".takeIf { current })
}
