package io.github.noxaur.dsmod.edge

import io.github.noxaur.dsmod.edge.input.InputApproved
import io.github.noxaur.dsmod.edge.input.Refusals
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.input.MouseButtonEvent

class TakenButtons {
    private val held = HashSet<Int>()

    @InputApproved(by = "noxaur", reaches = ["Refusals.allowMouseClick", "Refusals.allowMouseRelease"])
    fun register(screen: Screen, released: (Int) -> Unit = {}, takes: (MouseButtonEvent) -> Boolean) {
        val client = Minecraft.getInstance()
        ScreenEvents.afterTick(screen).register { held.removeIf { !Keys.held(client, it) } }
        Refusals.allowMouseClick(screen).register { _, event ->
            val taken = takes(event)
            if (taken) held += event.button()
            !taken
        }
        Refusals.allowMouseRelease(screen).register { _, event ->
            released(event.button())
            !held.remove(event.button())
        }
    }
}
