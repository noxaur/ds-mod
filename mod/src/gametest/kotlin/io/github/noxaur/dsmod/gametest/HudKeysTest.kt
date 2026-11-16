package io.github.noxaur.dsmod.gametest

import io.github.noxaur.dsmod.edge.DsModClient
import io.github.noxaur.dsmod.edge.Hud
import io.github.noxaur.dsmod.edge.HudEditorScreen
import io.github.noxaur.dsmod.gametest.InventoryGrid.ID
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext
import org.lwjgl.glfw.GLFW
import kotlin.math.roundToInt

/**
 * The HUD Editor Key opens the editor, where + and - resize the block picked last, and Hide In F3 takes the whole HUD
 * off while the debug screen is open. The Inventory Preview's grid is the block, since it shows anywhere.
 */
class HudKeysTest : FabricClientGameTest {
    override fun runTest(context: ClientGameTestContext) = InventoryGrid.with(context) {
        val hud = DsModClient.settings.hud
        val guiScale = context.computeOnClient<Int, Nothing> { it.window.guiScale }
        fun pixels() = context.computeOnClient<Int, Nothing> { (checkNotNull(Hud.find(ID)).scale * guiScale).roundToInt() }
        try {
            hud.editKey = GLFW.GLFW_KEY_J
            context.input.pressKey(GLFW.GLFW_KEY_J)
            context.waitForScreen(HudEditorScreen::class.java)

            // A click picks the grid, and + and - resize it a screen pixel per HUD pixel at a time.
            val block = context.computeOnClient<Hud.Placed, Nothing> { checkNotNull(Hud.find(ID)) }
            context.input.setCursorPos((block.x + 5.0) * guiScale, (block.y + 5.0) * guiScale)
            context.input.pressMouse(GLFW.GLFW_MOUSE_BUTTON_LEFT)
            context.waitTick()
            val before = pixels()
            context.input.pressKey(GLFW.GLFW_KEY_EQUAL)
            context.waitTick()
            check(pixels() == before + 1) { "+ did not make the grid bigger: ${pixels()} pixels, not ${before + 1}" }
            context.input.pressKey(GLFW.GLFW_KEY_MINUS)
            context.waitTick()
            check(pixels() == before) { "- did not make the grid smaller: ${pixels()} pixels, not $before" }
            context.setScreen { null }
        } finally {
            hud.editKey = -1
            hud.positions.remove(ID)
        }

        // Hide In F3 skips the HUD's whole draw while the debug screen is open, and only then.
        fun draws(): Long {
            context.runOnClient<Nothing> { DsModClient.game.metrics.flush() }
            context.waitTicks(3)
            return context.computeOnClient<Long, Nothing> { DsModClient.game.metrics.snapshot().timers["hud.draw"]?.count ?: 0L }
        }
        try {
            context.input.pressKey(GLFW.GLFW_KEY_F3)
            context.waitFor { it.debugOverlay.showDebugScreen() }
            check(draws() > 0) { "The HUD hid under F3 with Hide In F3 off" }
            val grid = context.computeOnClient<Hud.Placed, Nothing> { checkNotNull(Hud.find(ID)) }
            hud.hideInF3 = true
            check(draws() == 0L) { "The HUD drew under F3 with Hide In F3 on" }
            // What the player can't see, the chat's mouse can't point at, drag or read a tooltip from.
            context.runOnClient<Nothing> {
                check(Hud.at(grid.x + 9.0, grid.y + 9.0) == null && Hud.itemAt(grid.x + 16.0, grid.y + 16.0) == null && Hud.placed().isEmpty()) {
                    "The HUD hidden in F3 can still be pointed at"
                }
            }
            context.takeScreenshot("hud-hidden-in-f3")
            context.input.pressKey(GLFW.GLFW_KEY_F3)
            context.waitFor { !it.debugOverlay.showDebugScreen() }
            check(draws() > 0) { "The HUD stayed hidden after F3 closed" }
        } finally {
            hud.hideInF3 = false
            context.runOnClient<Nothing> { it.debugEntries.setOverlayVisible(false) }
        }
    }
}
