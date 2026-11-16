package io.github.noxaur.dsmod.gametest

import net.minecraft.world.inventory.ContainerInput

/** One `handleContainerInput`, which is one container-click packet. */
data class Click(val slot: Int, val button: Int, val input: ContainerInput)

/** Clicks recorded since [clear]. The mixin writes them as the game mode sends each packet. */
object ClickSpy {
    private val clicks = mutableListOf<Click>()

    fun clear() = clicks.clear()

    fun record(slot: Int, button: Int, input: ContainerInput) {
        clicks += Click(slot, button, input)
    }

    fun snapshot(): List<Click> = clicks.toList()
}
