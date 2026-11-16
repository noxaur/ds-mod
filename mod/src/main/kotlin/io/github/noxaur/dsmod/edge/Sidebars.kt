package io.github.noxaur.dsmod.edge

import io.github.noxaur.dsmod.core.feature.Game
import io.github.noxaur.dsmod.core.game.ChangeGate
import io.github.noxaur.dsmod.core.game.Sidebar
import net.minecraft.client.Minecraft
import net.minecraft.network.chat.Component
import net.minecraft.world.scores.DisplaySlot
import net.minecraft.world.scores.PlayerScoreEntry
import net.minecraft.world.scores.PlayerTeam

/** The client sorts and cuts the sidebar inside `Gui`, so this repeats it: the highest score on top, then by owner, 15 lines. */
private val sidebarOrder = compareByDescending<PlayerScoreEntry> { it.value }.thenBy(String.CASE_INSENSITIVE_ORDER) { it.owner }

private const val MAX_LINES = 15

/**
 * Reads the sidebar the way the client draws it, and posts it to [game] when a line changes. Each line is the
 * team's prefix, the owner's name and the team's suffix. A sidebar is also posted when [Game.tap] changes, so a
 * capture that starts later still begins with the lines.
 */
class SidebarPoller(private val game: Game) {
    private val gate = ChangeGate(emptyList<Component>(), game.tap)

    /** Posts the sidebar at the next poll even when no line changed, after [Game] forgot it with the old world. */
    fun forget() {
        gate.forget(listOf(Component.empty()))
    }

    fun poll(client: Minecraft) {
        if (!game.wants(Sidebar::class.java)) return
        val scoreboard = client.level?.scoreboard
        val objective = scoreboard?.getDisplayObjective(DisplaySlot.SIDEBAR)
        // The title is first, so one comparison covers it and the lines.
        val shown = if (scoreboard == null || objective == null) {
            emptyList()
        } else {
            listOf(objective.displayName) + scoreboard.listPlayerScores(objective).filter { !it.isHidden }.sortedWith(sidebarOrder).take(MAX_LINES)
                .map { PlayerTeam.formatNameForTeam(scoreboard.getPlayersTeam(it.owner), it.ownerName()) }
        }
        if (!gate.passes(shown, game.tap)) return
        val event = if (shown.isEmpty()) Sidebar() else Sidebar(shown.first().toText(), shown.drop(1).map { it.toText() })
        game.post(System.currentTimeMillis(), event)
    }
}
