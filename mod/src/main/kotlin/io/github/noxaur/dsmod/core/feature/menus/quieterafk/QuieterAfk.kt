package io.github.noxaur.dsmod.core.feature.menus.quieterafk

import io.github.noxaur.dsmod.core.config.DsModConfig
import io.github.noxaur.dsmod.core.config.Store
import io.github.noxaur.dsmod.core.feature.Feature
import io.github.noxaur.dsmod.core.feature.HasFeature
import io.github.noxaur.dsmod.core.game.Area
import io.github.noxaur.dsmod.core.game.ContainerOpened

class QuieterAfk(config: DsModConfig) : HasFeature {
    override val feature = Feature(config, "quieter-afk", Area.ANYWHERE, Store.INVENTORY, "Dim the screen and mute audio inside the /afk menu.")

    private var menu = NONE
    private var mouseX = Int.MIN_VALUE
    private var mouseY = Int.MIN_VALUE
    private var litUntil = Long.MIN_VALUE

    init {
        // @keep The server opens the menu again every second to count down in its title, as `AFK Rewards · 14:59`.
        feature.on<ContainerOpened> { opened, _ ->
            menu = if (opened.title?.plain?.startsWith(TITLE) == true) opened.id else NONE
            if (menu == NONE) mouseX = Int.MIN_VALUE
        }
    }

    val quiet: Boolean get() = menu != NONE && menu == feature.container.id && feature.runs

    /** @keep The menu lights up for a while after the mouse last moved, and the first frame in the menu counts as a move. */
    fun lit(now: Long, x: Int, y: Int): Boolean {
        if (x != mouseX || y != mouseY) {
            mouseX = x
            mouseY = y
            litUntil = now + LIT_MILLIS
        }
        return now < litUntil
    }

    companion object {
        const val TITLE = "AFK Rewards · "
        const val LIT_MILLIS = 30_000L
        private const val NONE = -1
    }
}
