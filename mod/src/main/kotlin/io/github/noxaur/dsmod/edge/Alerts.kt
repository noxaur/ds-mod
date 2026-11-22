package io.github.noxaur.dsmod.edge

import io.github.noxaur.dsmod.core.text.Text
import net.minecraft.client.Minecraft
import net.minecraft.client.resources.sounds.SimpleSoundInstance
import net.minecraft.sounds.SoundEvents

object Alerts {
    fun title(title: Text, chat: Text) {
        Minecraft.getInstance().gui.hud.setTitle(title.toComponent())
        Chat.send(chat)
        sound()
    }

    fun actionBar(text: Text) {
        Minecraft.getInstance().gui.hud.setOverlayMessage(text.toComponent(), false)
    }

    fun sound() {
        Minecraft.getInstance().soundManager.play(SimpleSoundInstance.forUI(SoundEvents.EXPERIENCE_ORB_PICKUP, 1f))
    }
}
