package io.github.noxaur.dsmod.edge

import net.minecraft.client.Minecraft

object PlayerCommands {
    fun send(command: String) {
        val connection = Minecraft.getInstance().connection ?: return
        MenuBack.sent(command)
        connection.sendCommand(command)
    }
}
