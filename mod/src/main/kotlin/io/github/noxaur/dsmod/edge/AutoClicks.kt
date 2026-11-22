package io.github.noxaur.dsmod.edge

import com.mojang.blaze3d.platform.InputConstants
import io.github.noxaur.dsmod.core.feature.general.AutoClick
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper
import net.minecraft.client.KeyMapping
import net.minecraft.client.Minecraft

object AutoClicks {
    private class Button(val click: AutoClick.Part, val mapping: KeyMapping)

    private lateinit var autoClick: AutoClick

    fun bind(autoClick: AutoClick) {
        this.autoClick = autoClick
    }

    private var clicking = false
    private val tapped = HashSet<AutoClick.Part>()

    private fun buttons(client: Minecraft) = listOf(Button(autoClick.left, client.options.keyAttack), Button(autoClick.right, client.options.keyUse))

    fun pressed(key: InputConstants.Key) {
        if (clicking) return
        for (button in buttons(Minecraft.getInstance())) if (Keys.matches(key, button.click.key.value)) tapped += button.click
    }

    fun allows(attack: Boolean): Boolean {
        val click = buttons(Minecraft.getInstance())[if (attack) 0 else 1].click
        return !click.runs || click.spend(System.nanoTime())
    }

    fun register() {
        ClientTickEvents.START_CLIENT_TICK.register { client ->
            val walking = client.player != null && client.gui.overlay() == null && client.gui.screen() == null &&
                client.options.let { it.keyUp.isDown || it.keyDown.isDown || it.keyLeft.isDown || it.keyRight.isDown }
            val clicks = buttons(client).mapNotNull { button ->
                val click = button.click
                val on = click.runs
                val pressed = click in tapped
                val due = click.tick(on, walking && on, pressed, Keys.held(client, click.key.value), System.nanoTime())
                KeyMappingHelper.getBoundKeyOf(button.mapping).takeIf { due && !button.mapping.isUnbound && !(pressed && Keys.matches(it, click.key.value)) }
            }
            tapped.clear()
            clicking = true
            for (bound in clicks) KeyMapping.click(bound)
            clicking = false
        }
    }
}
