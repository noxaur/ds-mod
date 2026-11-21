package io.github.noxaur.dsmod.edge

import net.minecraft.client.Minecraft

object PlayerCommands {
    fun send(command: String) {
        Minecraft.getInstance().connection?.sendCommand(command)
    }
}
