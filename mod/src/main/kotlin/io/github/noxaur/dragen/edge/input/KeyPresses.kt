package io.github.noxaur.dragen.edge.input

import net.minecraft.client.KeyMapping

@InputApproved(hash = "ff11c7232604")
object KeyPresses {
    fun take(mapping: KeyMapping): Int {
        var presses = 0
        while (mapping.consumeClick()) presses++
        return presses
    }
}
