package io.github.noxaur.dsmod.gametest

import com.mojang.blaze3d.platform.InputConstants
import io.github.noxaur.dsmod.core.camera.KeyMode
import io.github.noxaur.dsmod.edge.DsModClient
import io.github.noxaur.dsmod.edge.Hud
import io.github.noxaur.dsmod.edge.InventoryKey
import io.github.noxaur.dsmod.gametest.InventoryGrid.ID
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext
import org.lwjgl.glfw.GLFW

/**
 * A key the player binds shows and hides the grid, by a press under Toggle and while it is held under Hold, and with
 * none the grid always shows. Hide In F3 takes the whole HUD off while the debug screen is open.
 */
class InventoryKeyTest : FabricClientGameTest {
    override fun runTest(context: ClientGameTestContext) = InventoryGrid.with(context) {
        val general = DsModClient.settings.general
        val key = GLFW.GLFW_KEY_K
        fun shown() = context.computeOnClient<Boolean, Nothing> { Hud.find(ID) != null }

        general.inventoryKey = key
        context.waitTick()
        check(shown()) { "Binding a key hid the grid before its first press" }
        context.input.pressKey(key)
        context.waitFor { Hud.find(ID) == null }
        // A key held down repeats its event, as the game sends it, and a repeat is not another press.
        context.input.holdKey(key)
        context.waitFor { Hud.find(ID) != null }
        context.runOnClient<Nothing> { InventoryKey.pressed(InputConstants.Type.KEYSYM.getOrCreate(key)) }
        context.waitTicks(2)
        check(shown()) { "A repeat of the held key toggled the grid again" }
        context.input.releaseKey(key)
        context.waitTick()
        context.input.pressKey(key)
        context.waitTicks(2)
        check(!shown()) { "Toggle's press did not hide the grid" }
        context.input.pressKey(key)
        context.waitTicks(2)
        check(shown()) { "Toggle's second press did not show the grid" }

        general.inventoryKeyMode = KeyMode.HOLD
        context.waitTicks(2)
        check(!shown()) { "Under Hold the grid shows with the key up" }
        context.input.holdKey(key)
        context.waitTicks(2)
        check(shown()) { "Under Hold the grid does not show while the key is down" }
        context.input.releaseKey(key)
        context.waitTicks(2)
        check(!shown()) { "Under Hold the grid stays after the release" }
        general.inventoryKey = -1
        context.waitTicks(2)
        check(shown()) { "With no key the grid does not always show" }

        // Hide In F3 skips the HUD's whole draw while the debug screen is open, and only then.
        val hud = DsModClient.settings.hud
        fun draws(): Long {
            context.runOnClient<Nothing> { DsModClient.game.metrics.flush() }
            context.waitTicks(3)
            return context.computeOnClient<Long, Nothing> { DsModClient.game.metrics.snapshot().timers["hud.draw"]?.count ?: 0L }
        }
        try {
            context.input.pressKey(GLFW.GLFW_KEY_F3)
            context.waitFor { it.debugOverlay.showDebugScreen() }
            check(draws() > 0) { "The HUD hid under F3 with Hide In F3 off" }
            hud.hideInF3 = true
            check(draws() == 0L) { "The HUD drew under F3 with Hide In F3 on" }
            context.takeScreenshot("inventory-preview-hidden-in-f3")
            context.input.pressKey(GLFW.GLFW_KEY_F3)
            context.waitFor { !it.debugOverlay.showDebugScreen() }
            check(draws() > 0) { "The HUD stayed hidden after F3 closed" }
        } finally {
            hud.hideInF3 = false
            context.runOnClient<Nothing> { it.debugEntries.setOverlayVisible(false) }
        }
    }
}
