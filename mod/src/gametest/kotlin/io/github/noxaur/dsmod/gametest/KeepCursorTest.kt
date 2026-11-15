package io.github.noxaur.dsmod.gametest

import com.mojang.blaze3d.platform.Window
import io.github.noxaur.dsmod.edge.DsModClient
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext

/**
 * Opens one menu from another, as the server does with a close and an open in one tick, and checks where the cursor
 * ends up. The game takes and releases the mouse only while its window has focus, which a test window never has, so the
 * test tells the game it has focus for the length of the run.
 */
class KeepCursorTest : FabricClientGameTest {
    private val focused = Window::class.java.getDeclaredField("focused").apply { isAccessible = true }

    private fun cursorAfterNext(context: ClientGameTestContext, world: TestSingleplayerContext): Pair<Double, Double> {
        ServerMenus.open(context, world, 6, "First")
        context.input.setCursorPos(40.0, 50.0)
        context.waitTicks(2)
        ServerMenus.open(context, world, 4, "Second")
        return context.computeOnClient<Pair<Double, Double>, Nothing> { it.mouseHandler.xpos() to it.mouseHandler.ypos() }
    }

    override fun runTest(context: ClientGameTestContext) {
        context.worldBuilder().create().use { world ->
            context.runOnClient<Nothing> { focused.setBoolean(it.window, true) }
            try {
                check(cursorAfterNext(context, world) == 40.0 to 50.0) { "Keep Cursor let the cursor move" }

                context.runOnClient<Nothing> { DsModClient.settings.inventory.keepCursor = false }
                val centered = cursorAfterNext(context, world)
                val middle = context.computeOnClient<Pair<Double, Double>, Nothing> {
                    (it.window.screenWidth / 2).toDouble() to (it.window.screenHeight / 2).toDouble()
                }
                check(centered == middle) { "With Keep Cursor off the game should center the cursor at $middle, as vanilla does, and put it at $centered" }
            } finally {
                context.runOnClient<Nothing> {
                    DsModClient.settings.inventory.keepCursor = true
                    // Without focus the game leaves the mouse free as the menu closes, as it does for every other test.
                    focused.setBoolean(it.window, false)
                    it.player!!.closeContainer()
                }
            }
        }
    }
}
