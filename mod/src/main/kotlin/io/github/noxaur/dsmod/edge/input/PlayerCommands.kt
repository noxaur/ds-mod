package io.github.noxaur.dsmod.edge.input

import net.minecraft.client.Minecraft

@InputApproved(hash = "fcc36560e41d")
object PlayerCommands {
    fun send(command: String) {
        Minecraft.getInstance().connection?.sendCommand(command)
    }

    fun chat(line: String) {
        Minecraft.getInstance().connection?.sendChat(line)
    }
}
