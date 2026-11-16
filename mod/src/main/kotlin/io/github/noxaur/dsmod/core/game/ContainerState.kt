package io.github.noxaur.dsmod.core.game

/** The open container, kept up to date by [ContainerOpened] and [SlotChanged]. A slot nothing has filled is null. */
class ContainerState {
    private var items = arrayOfNulls<Item>(0)

    // The player's own inventory as it was when another menu opened, for a [ContainerOpened] that keeps it.
    private var inventory = arrayOfNulls<Item>(0)

    var id = 0
        private set
    var title: ServerText? = null
        private set
    var slots: List<Item?> = items.asList()
        private set

    fun opened(event: ContainerOpened) {
        if (id == INVENTORY) inventory = items
        id = event.id
        title = event.title
        items = if (event.kept && event.id == INVENTORY && inventory.size == event.size) inventory else arrayOfNulls(event.size)
        slots = items.asList()
    }

    fun changed(event: SlotChanged) {
        if (event.slot in items.indices) items[event.slot] = event.item
    }

    companion object {
        /** The player's own inventory, which the client always numbers 0. */
        const val INVENTORY = 0
    }
}
