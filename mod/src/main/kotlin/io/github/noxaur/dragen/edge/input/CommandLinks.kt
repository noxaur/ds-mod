package io.github.noxaur.dragen.edge.input

import net.minecraft.network.chat.ClickEvent

@InputApproved(hash = "ebf8060e5459")
object CommandLinks {
    fun runs(command: String): ClickEvent = ClickEvent.RunCommand(command)
}
