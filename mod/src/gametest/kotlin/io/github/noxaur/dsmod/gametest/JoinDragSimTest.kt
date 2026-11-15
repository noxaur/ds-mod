package io.github.noxaur.dsmod.gametest

import io.github.noxaur.dsmod.debug.JoinDragSim
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext
import net.fabricmc.fabric.api.client.screen.v1.Screens
import net.minecraft.client.gui.screens.TitleScreen
import net.minecraft.network.chat.contents.TranslatableContents

/** The debug build's title screen has Join DragSim in Singleplayer's column, one row above it. It is not pressed, since that joins the live server. */
class JoinDragSimTest : FabricClientGameTest {
    override fun runTest(context: ClientGameTestContext) {
        context.setScreen { TitleScreen() }
        context.runOnClient<Nothing> { client ->
            val buttons = Screens.getWidgets(client.gui.screen()!!)
            val join = buttons.single { it.message.string == JoinDragSim.LABEL }
            val singleplayer = buttons.single { (it.message.contents as? TranslatableContents)?.key == "menu.singleplayer" }
            check(join.x == singleplayer.x && join.width == singleplayer.width && join.y == singleplayer.y - 24) {
                "Join DragSim is at ${join.x},${join.y} ${join.width} wide, Singleplayer at ${singleplayer.x},${singleplayer.y} ${singleplayer.width} wide"
            }
        }
        context.takeScreenshot("join-dragsim")
        context.setScreen { null }
    }
}
