package io.github.noxaur.dsmod.edge.input

import net.minecraft.world.inventory.Slot

@InputApproved(by = "noxaur", hash = "914db6700a7c")
object SlotPlaces {
    fun move(slot: Slot, x: Int, y: Int) {
        slot.x = x
        slot.y = y
    }
}
