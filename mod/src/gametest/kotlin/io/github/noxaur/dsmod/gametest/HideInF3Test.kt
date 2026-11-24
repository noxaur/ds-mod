package io.github.noxaur.dsmod.gametest

import io.github.noxaur.dsmod.core.feature.ui.hud.hideinf3.HideInF3
import io.github.noxaur.dsmod.edge.DsModClient
import io.github.noxaur.dsmod.edge.Hud
import io.github.noxaur.dsmod.gametest.InventoryGrid.ID
import io.github.noxaur.dsmod.gametest.InventoryGrid.panelColor
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext
import org.lwjgl.glfw.GLFW

class HideInF3Test : FabricClientGameTest {
    override fun runTest(context: ClientGameTestContext) = InventoryGrid.with(context) {
        val panel = panelColor(context, context.takeScreenshot("hud-before-f3"))
        fun shows(name: String): Boolean {
            context.waitTicks(3)
            return panelColor(context, context.takeScreenshot(name)) == panel
        }
        try {
            context.input.pressKey(GLFW.GLFW_KEY_F3)
            context.waitFor { it.debugOverlay.showDebugScreen() }
            check(shows("hud-shown-in-f3")) { "The HUD hid under F3 with Hide In F3 off" }
            val grid = context.computeOnClient<Hud.Placed, Nothing> { checkNotNull(Hud.find(ID)) }
            context.runOnClient<Nothing> { DsModClient.feature<HideInF3>().feature.enabled = true }
            check(!shows("hud-hidden-in-f3")) { "The HUD drew under F3 with Hide In F3 on" }
            context.runOnClient<Nothing> {
                check(Hud.at(grid.x + 9.0, grid.y + 9.0) == null && Hud.itemAt(grid.x + 16.0, grid.y + 16.0) == null && Hud.placed().isEmpty()) {
                    "The HUD hidden in F3 can still be pointed at, dragged or read a tooltip from"
                }
            }
            context.input.pressKey(GLFW.GLFW_KEY_F3)
            context.waitFor { !it.debugOverlay.showDebugScreen() }
            check(shows("hud-after-f3")) { "The HUD stayed hidden after F3 closed" }
        } finally {
            context.runOnClient<Nothing> {
                DsModClient.feature<HideInF3>().feature.enabled = false
                it.debugEntries.setOverlayVisible(false)
            }
        }
    }
}
