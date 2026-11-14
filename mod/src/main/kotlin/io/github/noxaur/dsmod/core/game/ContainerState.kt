package io.github.noxaur.dsmod.core.game

/** The open container, kept up to date by [ContainerOpened] and [SlotChanged]. A slot nothing has filled is null. */
class ContainerState {
    private var items = arrayOfNulls<Item>(0)

    var id = 0
        private set
    var title: ServerText? = null
        private set
    var slots: List<Item?> = items.asList()
        private set

    fun opened(event: ContainerOpened) {
        id = event.id
        title = event.title
        items = arrayOfNulls(event.size)
        slots = items.asList()
    }

    fun changed(event: SlotChanged) {
        if (event.slot in items.indices) items[event.slot] = event.item
    }
}
