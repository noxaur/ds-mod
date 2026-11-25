package io.github.noxaur.dragen.edge

import io.github.noxaur.dragen.core.feature.Game
import io.github.noxaur.dragen.core.game.ChangeGate
import io.github.noxaur.dragen.core.game.TabList
import net.minecraft.client.Minecraft
import net.minecraft.network.chat.Component

class TabListPoller(private val game: Game) {
    private val gate = ChangeGate(emptyList<Component>(), game.tap)

    fun forget() {
        gate.forget(emptyList())
    }

    fun poll(client: Minecraft) {
        if (!game.wants(TabList::class.java)) return
        val overlay = client.gui.hud.tabList
        val rows = if (client.player == null) emptyList() else overlay.getPlayerInfos().map(overlay::getNameForDisplay)
        if (!gate.passes(rows, game.tap)) return
        game.post(System.currentTimeMillis(), TabList(rows.map { it.toText() }))
    }
}
