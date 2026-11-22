package io.github.noxaur.dsmod.edge

import com.mojang.blaze3d.platform.InputConstants
import io.github.noxaur.dsmod.core.feature.general.AutoClick
import io.github.noxaur.dsmod.edge.input.Clicks
import io.github.noxaur.dsmod.edge.input.InputApproved
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper
import net.minecraft.client.KeyMapping
import net.minecraft.client.Minecraft

object AutoClicks {
    private class Button(val click: AutoClick.Part, val mapping: KeyMapping)

    private var clicking = false
    private val tapped = HashSet<AutoClick.Part>()

    private fun buttons(client: Minecraft) = DsModClient.feature<AutoClick>().let { listOf(Button(it.left, client.options.keyAttack), Button(it.right, client.options.keyUse)) }

    fun pressed(key: InputConstants.Key) {
        if (clicking) return
        for (button in buttons(Minecraft.getInstance())) if (Keys.matches(key, button.click.key.value)) tapped += button.click
    }

    @InputApproved(by = "noxaur", reaches = ["Clicks.press"])
    fun register() {
        ClientTickEvents.START_CLIENT_TICK.register { client ->
            val walking = client.player != null && client.gui.overlay() == null && client.gui.screen() == null &&
                client.options.let { it.keyUp.isDown || it.keyDown.isDown || it.keyLeft.isDown || it.keyRight.isDown }
            val due = buttons(client).filter { button ->
                val click = button.click
                val on = click.runs
                val pressed = click in tapped
                click.tick(on, walking && on, pressed, Keys.held(client, click.key.value)) &&
                    !(pressed && Keys.matches(KeyMappingHelper.getBoundKeyOf(button.mapping), click.key.value))
            }
            tapped.clear()
            clicking = true
            for (button in due) Clicks.press(button.mapping)
            clicking = false
        }
    }
}
