package io.github.noxaur.dsmod.edge

import io.github.noxaur.dsmod.core.feature.Game
import io.github.noxaur.dsmod.core.game.ChangeGate
import io.github.noxaur.dsmod.core.game.Sidebar
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.Hud as GameHud
import net.minecraft.network.chat.Component
import net.minecraft.world.scores.DisplaySlot
import net.minecraft.world.scores.PlayerTeam

private const val MAX_LINES = 15

class SidebarPoller(private val game: Game) {
    private val gate = ChangeGate(emptyList<Component>(), game.tap)

    fun forget() {
        gate.forget(listOf(Component.empty()))
    }

    fun poll(client: Minecraft) {
        if (!game.wants(Sidebar::class.java)) return
        val scoreboard = client.level?.scoreboard
        val objective = scoreboard?.getDisplayObjective(DisplaySlot.SIDEBAR)
        val shown = if (scoreboard == null || objective == null) {
            emptyList()
        } else {
            listOf(objective.displayName) + scoreboard.listPlayerScores(objective).filter { !it.isHidden }.sortedWith(GameHud.SCORE_DISPLAY_ORDER).take(MAX_LINES)
                .map { PlayerTeam.formatNameForTeam(scoreboard.getPlayersTeam(it.owner), it.ownerName()) }
        }
        if (!gate.passes(shown, game.tap)) return
        val event = if (shown.isEmpty()) Sidebar() else Sidebar(shown.first().toText(), shown.drop(1).map { it.toText() })
        game.post(System.currentTimeMillis(), event)
    }
}
