package io.github.noxaur.dsmod.edge.input

import net.minecraft.client.KeyMapping

@InputApproved(hash = "391f5d9c0db7")
object KeyPresses {
    fun take(mapping: KeyMapping): Int {
        var presses = 0
        while (mapping.consumeClick()) presses++
        return presses
    }
}
