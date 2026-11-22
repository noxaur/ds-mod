package io.github.noxaur.dsmod.edge

import io.github.noxaur.dsmod.core.feature.Game
import io.github.noxaur.dsmod.core.game.ChangeGate
import io.github.noxaur.dsmod.core.game.TabList
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
