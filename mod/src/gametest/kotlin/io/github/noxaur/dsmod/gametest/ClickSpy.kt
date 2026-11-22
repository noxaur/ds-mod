package io.github.noxaur.dsmod.gametest

import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext
import net.minecraft.world.inventory.ContainerInput

data class Click(val slot: Int, val button: Int, val input: ContainerInput)

object ClickSpy {
    private val clicks = mutableListOf<Click>()

    fun record(slot: Int, button: Int, input: ContainerInput) {
        clicks += Click(slot, button, input)
    }

    fun during(context: ClientGameTestContext, act: () -> Unit): List<Click> {
        context.runOnClient<Nothing> { clicks.clear() }
        act()
        return context.computeOnClient<List<Click>, Nothing> { clicks.toList() }
    }
}
