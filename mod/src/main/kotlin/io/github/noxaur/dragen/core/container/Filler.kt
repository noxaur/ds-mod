package io.github.noxaur.dragen.core.container

/** @keep The server's filler is a pane with a blank name, black in most menus and white in a profile. A colored pane
 * is not filler: a dungeon terminal is played on red, lime and magenta ones */
object Filler {
    private val PANES = setOf("minecraft:black_stained_glass_pane", "minecraft:white_stained_glass_pane")

    fun of(id: String, name: String?) = id in PANES && name?.isBlank() == true
}
