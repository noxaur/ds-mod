package io.github.noxaur.dsmod.edge

import io.github.noxaur.dsmod.core.text.Text
import net.minecraft.client.Minecraft
import net.minecraft.client.resources.sounds.SimpleSoundInstance
import net.minecraft.sounds.SoundEvents

/** Short-lived cues for something that needs the player now. Call them on the client thread. */
object Alerts {
    /** Large center text with a sound. [chat] says which feature fired, so the player can turn it off. */
    fun title(title: Text, chat: Text) {
        Minecraft.getInstance().gui.hud.setTitle(title.toComponent())
        Chat.send(chat)
        sound()
    }

    /** The small line above the hotbar. */
    fun actionBar(text: Text) {
        Minecraft.getInstance().gui.hud.setOverlayMessage(text.toComponent(), false)
    }

    fun sound() {
        Minecraft.getInstance().soundManager.play(SimpleSoundInstance.forUI(SoundEvents.EXPERIENCE_ORB_PICKUP, 1f))
    }
}
