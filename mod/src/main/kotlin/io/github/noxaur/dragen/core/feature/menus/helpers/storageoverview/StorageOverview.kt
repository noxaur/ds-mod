package io.github.noxaur.dragen.core.feature.menus.helpers.storageoverview

import io.github.noxaur.dragen.core.config.DragenConfig
import io.github.noxaur.dragen.core.config.Store
import io.github.noxaur.dragen.core.feature.Feature
import io.github.noxaur.dragen.core.feature.HasFeature
import io.github.noxaur.dragen.core.game.Area
import io.github.noxaur.dragen.core.game.ContainerOpened
import io.github.noxaur.dragen.core.overview.StoragePages
import io.github.noxaur.dragen.core.text.text
import kotlinx.serialization.Serializable

enum class StorageMode(private val label: String) {
    PREVIEW("Preview"),
    OVERVIEW("Overview"),
    ;

    override fun toString() = label
}

@Serializable
data class StorageOverviewState(val told: Boolean = false, val pages: Int = 0)

class StorageOverview(config: DragenConfig) : HasFeature {
    override val feature = Feature(config, "storage-overview", Area.ANYWHERE, Store.INVENTORY, "Show what your Storage pages hold before you open them.")

    val mode = feature.choice("storageMode", "Mode", "Preview a page's items in its tooltip, or see every page at once.", StorageMode.PREVIEW)

    private val state = feature.profiled(StorageOverviewState())

    val pages = StoragePages(feature, state)

    init {
        feature.on<ContainerOpened> { opened, _ ->
            if (state.value.told || mode.value != StorageMode.PREVIEW || opened.title?.plain != StoragePages.ROOT || !pages.any) return@on
            state.value = state.value.copy(told = true)
            feature.notice(text { body("Hover a page to see its items, and hold Shift for the server's text. Turn off "); value("Storage Overview"); body(" in /dragen to stop it.") })
        }
    }
}
