package io.github.noxaur.dsmod.edge

import com.mojang.blaze3d.platform.InputConstants
import io.github.noxaur.dsmod.core.feature.general.AutoClicker
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper
import net.minecraft.client.KeyMapping
import net.minecraft.client.Minecraft

object AutoClicks {
    private var clicking = false
    private val tapped = HashSet<AutoClicker.Trigger>()

    private fun triggers(client: Minecraft) = DsModClient.feature<AutoClicker>().let { listOf(it.left to client.options.keyAttack, it.right to client.options.keyUse) }

    fun pressed(key: InputConstants.Key) {
        if (clicking) return
        for ((trigger, _) in triggers(Minecraft.getInstance())) if (Keys.matches(key, trigger.key.value)) tapped += trigger
    }

    fun register() {
        ClientTickEvents.START_CLIENT_TICK.register { client ->
            val on = DsModClient.runs<AutoClicker>()
            val walking = on && client.player != null && client.gui.screen() == null && client.options.let { it.keyUp.isDown || it.keyDown.isDown || it.keyLeft.isDown || it.keyRight.isDown }
            val clicks = triggers(client).filter { (trigger, _) -> trigger.tick(on, walking, trigger in tapped, Keys.held(client, trigger.key.value)) }
            tapped.clear()
            clicking = true
            for ((_, mapping) in clicks) KeyMapping.click(KeyMappingHelper.getBoundKeyOf(mapping))
            clicking = false
        }
    }
}
