package io.github.noxaur.dragen.gametest

import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext

fun ClientGameTestContext.withCommandMenu(name: String, rows: Int, title: String, goBack: Boolean = true, body: (TestSingleplayerContext) -> Unit) =
    worldBuilder().create().use { world ->
        ServerMenus.command(world, name, rows, title, goBack)
        body(world)
    }

fun ClientGameTestContext.sendMenuCommand(name: String, title: String) {
    runOnClient<Nothing> { it.connection!!.sendCommand(name) }
    ServerMenus.shows(this, title)
}

