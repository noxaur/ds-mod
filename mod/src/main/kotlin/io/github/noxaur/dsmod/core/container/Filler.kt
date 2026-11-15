package io.github.noxaur.dsmod.core.container

/**
 * The glass panes the server fills a menu's empty slots with: a pane with a blank name, black in most menus and white
 * in a player's profile. A colored pane is not filler, because a dungeon terminal is played on red, lime and magenta ones.
 */
object Filler {
    private val PANES = setOf("minecraft:black_stained_glass_pane", "minecraft:white_stained_glass_pane")

    /** Whether an item [id] with the custom [name] is filler. */
    fun of(id: String, name: String?) = id in PANES && name?.isBlank() == true
}
