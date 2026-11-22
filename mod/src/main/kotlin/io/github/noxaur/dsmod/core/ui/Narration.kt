package io.github.noxaur.dsmod.core.ui

object Narration {
    fun join(vararg parts: String?) = parts.filterNot { it.isNullOrEmpty() }.joinToString(", ")

    fun of(index: Int, size: Int) = "${index + 1} of $size"

    fun item(label: String, index: Int, size: Int, current: Boolean) = join(label, of(index, size), "current".takeIf { current })
}
