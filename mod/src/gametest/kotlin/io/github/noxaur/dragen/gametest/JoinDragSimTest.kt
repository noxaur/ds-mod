package io.github.noxaur.dragen.gametest

import io.github.noxaur.dragen.debug.JoinDragSim
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext
import net.minecraft.client.gui.components.AbstractWidget
import net.minecraft.client.gui.screens.TitleScreen
import net.minecraft.network.chat.contents.TranslatableContents

class JoinDragSimTest : FabricClientGameTest {
    override fun runTest(context: ClientGameTestContext) {
        context.setScreen { TitleScreen() }
        val buttons = ServerMenus.widgets<AbstractWidget>(context)
        val join = buttons.single { it.message.string == JoinDragSim.LABEL }
        val singleplayer = buttons.single { (it.message.contents as? TranslatableContents)?.key == "menu.singleplayer" }
        check(join.x == singleplayer.x && join.width == singleplayer.width && join.y == singleplayer.y - 24) {
            "Join DragSim is at ${join.x},${join.y} ${join.width} wide, Singleplayer at ${singleplayer.x},${singleplayer.y} ${singleplayer.width} wide"
        }
        context.takeScreenshot("join-dragsim")
        context.setScreen { null }
    }
}
