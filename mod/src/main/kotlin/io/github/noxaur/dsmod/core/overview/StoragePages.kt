package io.github.noxaur.dsmod.core.overview

import io.github.noxaur.dsmod.core.feature.Feature
import io.github.noxaur.dsmod.core.feature.Saved
import io.github.noxaur.dsmod.core.feature.menus.storageoverview.StorageOverviewState
import io.github.noxaur.dsmod.core.game.ContainerOpened
import io.github.noxaur.dsmod.core.game.ContainerState
import io.github.noxaur.dsmod.core.game.SlotChanged
import io.github.noxaur.dsmod.core.player.Seen
import io.github.noxaur.dsmod.core.text.count

/** @keep A page not bought has no icon, so the count comes from the root menu's page icons and a page's `(2/7)`. */
class StoragePages(private val feature: Feature, private val state: Saved<StorageOverviewState>) {
    private var root by feature.session { false }
    private var rootSize by feature.session { 0 }
    private var read: Map<String, Seen>? = null
    private var changes = 0

    val owned: Int get() = state.value.pages

    val version: Int
        get() {
            val now = feature.player.containers
            if (now !== read) {
                read = now
                changes++
            }
            return changes
        }

    fun page(id: String): Seen? = feature.player.containers[id]

    val any: Boolean get() = feature.player.containers.keys.any { it == Shelf.ENDER_CHEST_SHELF.id || number(it) != null }

    fun shown(title: String?): List<Shelf> {
        val opening = Shelf.of(title)
        val kept = feature.player.containers.keys.mapNotNull(::number).maxOrNull()
        val last = listOfNotNull(owned, opening?.pages, opening?.id?.let(::number), kept).max()
        return listOf(Shelf.ENDER_CHEST_SHELF) + (1..last).map { Shelf.storage(it) }
    }

    init {
        feature.on<ContainerOpened> { opened, _ ->
            val title = opened.title?.plain
            root = title == ROOT
            rootSize = opened.size - ContainerState.PLAYER_SLOTS
            Shelf.of(title)?.pages?.let(::count)
        }
        feature.on<SlotChanged> { changed, _ ->
            if (!root || changed.slot >= rootSize) return@on
            changed.item?.name?.plain?.let(::pageOf)?.let(::number)?.let(::count)
        }
    }

    private fun count(pages: Int) {
        if (pages <= owned) return
        state.value = state.value.copy(pages = pages)
        changes++
    }

    companion object {
        const val ROOT = "Storage"

        private const val STORAGE = "storage:"

        fun number(id: String): Int? = if (id.startsWith(STORAGE)) count(id.removePrefix(STORAGE))?.plus(1) else null
        private val ICON = Regex("Storage Page (\\d+)")

        fun pageOf(icon: String): String? {
            if (icon == Shelf.ENDER_CHEST_SHELF.title) return Shelf.ENDER_CHEST_SHELF.id
            val page = ICON.matchEntire(icon)?.groupValues?.get(1)?.let(::count)?.takeIf { it > 0 } ?: return null
            return "$STORAGE${page - 1}"
        }
    }
}
