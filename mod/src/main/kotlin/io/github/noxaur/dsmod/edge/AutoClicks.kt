package io.github.noxaur.dsmod.edge

import com.mojang.blaze3d.platform.InputConstants
import io.github.noxaur.dsmod.core.feature.general.AutoClick
import io.github.noxaur.dsmod.core.feature.general.AutoLeftClick
import io.github.noxaur.dsmod.core.feature.general.AutoRightClick
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper
import net.minecraft.client.KeyMapping
import net.minecraft.client.Minecraft

object AutoClicks {
    private class Button(val click: AutoClick, val on: Boolean, val mapping: KeyMapping)

    private var clicking = false
    private val tapped = HashSet<AutoClick>()

    private fun buttons(client: Minecraft) = listOf(
        Button(DsModClient.feature<AutoLeftClick>().click, DsModClient.runs<AutoLeftClick>(), client.options.keyAttack),
        Button(DsModClient.feature<AutoRightClick>().click, DsModClient.runs<AutoRightClick>(), client.options.keyUse),
    )

    fun pressed(key: InputConstants.Key) {
        if (clicking) return
        for (button in buttons(Minecraft.getInstance())) if (Keys.matches(key, button.click.key.value)) tapped += button.click
    }

    fun allows(attack: Boolean): Boolean {
        val button = buttons(Minecraft.getInstance())[if (attack) 0 else 1]
        return !button.on || button.click.spend(System.nanoTime())
    }

    fun register() {
        ClientTickEvents.START_CLIENT_TICK.register { client ->
            val walking = client.player != null && client.gui.overlay() == null && client.gui.screen() == null &&
                client.options.let { it.keyUp.isDown || it.keyDown.isDown || it.keyLeft.isDown || it.keyRight.isDown }
            val clicks = buttons(client).mapNotNull { button ->
                val click = button.click
                val pressed = click in tapped
                val due = click.tick(button.on, walking && button.on, pressed, Keys.held(client, click.key.value), System.nanoTime())
                KeyMappingHelper.getBoundKeyOf(button.mapping).takeIf { due && !button.mapping.isUnbound && !(pressed && Keys.matches(it, click.key.value)) }
            }
            tapped.clear()
            clicking = true
            for (bound in clicks) KeyMapping.click(bound)
            clicking = false
        }
    }
}
