package io.github.noxaur.dsmod.edge.input

import net.minecraft.world.inventory.Slot

/** @keep A slot's place is where a click on it lands, so moving one is input. The storage overview moves the open page's
 * slots into the page's cells, and the slots it hides off the screen. */
@InputApproved(by = "noxaur", hash = "914db6700a7c")
object SlotPlaces {
    fun move(slot: Slot, x: Int, y: Int) {
        slot.x = x
        slot.y = y
    }
}
