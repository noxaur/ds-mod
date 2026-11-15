package io.github.noxaur.dsmod.edge

import com.mojang.blaze3d.platform.InputConstants
import io.github.noxaur.dsmod.core.config.DsModConfig
import io.github.noxaur.dsmod.core.input.CommandBind.Message
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.minecraft.client.Minecraft

/**
 * Command Binds. A press comes from `KeyPressMixin`, where the game counts its own key binds' presses, so it
 * fires only in the world: a key typed into chat or bound on a screen sends nothing.
 */
object CommandKeys {
    private lateinit var settings: DsModConfig.General

    /** Keys that sent and are still down. The game reports a held key's repeats as presses, and those send nothing. */
    private val down = HashSet<Int>()

    fun register(settings: DsModConfig.General) {
        this.settings = settings
        ClientTickEvents.END_CLIENT_TICK.register { client -> if (down.isNotEmpty()) down.removeIf { !Keys.held(client, it) } }
    }

    fun pressed(key: InputConstants.Key) {
        if (!DsModClient.active || !settings.commandBinds) return
        val binds = settings.binds.filter { Keys.matches(key, it.key) }
        if (binds.isEmpty() || !down.add(binds.first().key)) return
        val connection = Minecraft.getInstance().connection ?: return
        // Every row on the key sends, in order, as the settings warn when two share one.
        for (bind in binds) {
            when (val message = bind.message) {
                is Message.Command -> connection.sendCommand(message.text) // rule:no-automation Command Binds
                is Message.Chat -> connection.sendChat(message.text) // rule:no-automation Command Binds
                null -> {}
            }
        }
    }
}
