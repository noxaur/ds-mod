package io.github.noxaur.dragen.edge.input

import net.minecraft.world.inventory.Slot

@InputApproved(by = "noxaur", hash = "f6dd3cf280e0")
object SlotPlaces {
    fun move(slot: Slot, x: Int, y: Int) {
        slot.x = x
        slot.y = y
    }
}
