package io.github.noxaur.dsmod.edge.input

import net.minecraft.network.chat.ClickEvent

@InputApproved(hash = "4d235b389ada")
object CommandLinks {
    fun runs(command: String): ClickEvent = ClickEvent.RunCommand(command)
}
