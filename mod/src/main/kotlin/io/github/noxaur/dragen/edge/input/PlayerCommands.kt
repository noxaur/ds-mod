package io.github.noxaur.dragen.edge.input

import net.minecraft.client.Minecraft

@InputApproved(hash = "c42f6fabcf39")
object PlayerCommands {
    fun send(command: String) {
        Minecraft.getInstance().connection?.sendCommand(command)
    }

    fun chat(line: String) {
        Minecraft.getInstance().connection?.sendChat(line)
    }
}
