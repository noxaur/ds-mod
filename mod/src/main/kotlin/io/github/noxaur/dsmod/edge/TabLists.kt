package io.github.noxaur.dsmod.edge

import io.github.noxaur.dsmod.core.feature.Game
import io.github.noxaur.dsmod.core.game.TabList
import net.minecraft.client.Minecraft
import net.minecraft.client.multiplayer.PlayerInfo
import net.minecraft.network.chat.Component
import net.minecraft.world.level.GameType

/**
 * The client sorts the tab list in a private comparator inside `PlayerTabOverlay`, so this repeats it:
 * a higher list order first, spectators last, then team name, then profile name ignoring case.
 * `TabListTest` checks it against the client's own order.
 */
private val tabOrder = compareBy<PlayerInfo>({ -it.tabListOrder }, { it.gameMode == GameType.SPECTATOR }, { it.team?.name.orEmpty() })
    .thenBy(String.CASE_INSENSITIVE_ORDER) { it.profile.name }

private const val MAX_ROWS = 80

/**
 * Reads the tab list the way the client draws it, and posts it to [game] when a row changes. A tab list
 * is also posted when [Game.tap] changes, so a capture that starts later still begins with the rows.
 */
class TabListPoller(private val game: Game) {
    private var last = emptyList<Component>()
    private var tapped = game.tap

    /** Posts the tab list at the next poll even when no row changed, after [Game] forgot it with the old world. */
    fun forget() {
        last = emptyList()
    }

    fun poll(client: Minecraft) {
        if (!game.wants(TabList::class.java)) return
        val overlay = client.gui.hud.tabList
        val rows = client.connection?.listedOnlinePlayers.orEmpty().sortedWith(tabOrder).take(MAX_ROWS).map(overlay::getNameForDisplay)
        if (rows == last && game.tap === tapped) return
        last = rows
        tapped = game.tap
        game.post(System.currentTimeMillis(), TabList(rows.map { it.toText() }))
    }
}
