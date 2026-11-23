package io.github.noxaur.dsmod.core.game

class ContainerState {
    private var items = arrayOfNulls<Item>(0)

    private var inventory = arrayOfNulls<Item>(0)

    var id = 0
        private set
    var title: ServerText? = null
        private set
    var slots: List<Item?> = items.asList()
        private set
    var menuSlots = 0 until 0
        private set
    var playerSlots = 0 until 0
        private set

    // @keep The player's own inventory by its slot index, as it was when another menu opened over it. A view, so copy it to keep it.
    val inventorySlots: List<Item?> get() = (if (id == INVENTORY) items else inventory).asList()

    fun opened(event: ContainerOpened) {
        if (id == INVENTORY) inventory = items
        id = event.id
        title = event.title
        items = if (event.kept && event.id == INVENTORY && inventory.size == event.size) inventory else arrayOfNulls(event.size)
        slots = items.asList()
        val menu = if (id == INVENTORY) 0 else maxOf(0, items.size - PLAYER_SLOTS)
        menuSlots = 0 until menu
        playerSlots = menu until items.size
    }

    fun changed(event: SlotChanged) {
        if (event.slot in items.indices) items[event.slot] = event.item
    }

    companion object {
        const val INVENTORY = 0
        const val PLAYER_SLOTS = 36
        const val ROW_SLOTS = 9
        val WORN_SLOTS = 5..8
        val CARRIED_SLOTS = WORN_SLOTS.last + 1 until WORN_SLOTS.last + 1 + PLAYER_SLOTS
        val OFFHAND_SLOT = CARRIED_SLOTS.last + 1
    }
}
