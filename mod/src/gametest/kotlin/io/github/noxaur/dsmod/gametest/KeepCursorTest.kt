package io.github.noxaur.dsmod.gametest

import io.github.noxaur.dsmod.core.feature.inventory.KeepCursor
import io.github.noxaur.dsmod.edge.DsModClient
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext

/**
 * Opens one menu from another, as the server does with a close and an open in one tick, and checks where the cursor
 * ends up. The game takes and releases the mouse only while its window has focus, which Fabric gives every test.
 */
class KeepCursorTest : FabricClientGameTest {
    private fun cursorAfterNext(context: ClientGameTestContext, world: TestSingleplayerContext): Pair<Double, Double> {
        ServerMenus.open(context, world, 6, "First")
        context.input.setCursorPos(40.0, 50.0)
        context.waitTicks(2)
        ServerMenus.open(context, world, 4, "Second")
        return context.computeOnClient<Pair<Double, Double>, Nothing> { it.mouseHandler.xpos() to it.mouseHandler.ypos() }
    }

    override fun runTest(context: ClientGameTestContext) {
        context.worldBuilder().create().use { world ->
            try {
                check(cursorAfterNext(context, world) == 40.0 to 50.0) { "Keep Cursor let the cursor move" }

                context.runOnClient<Nothing> { DsModClient.feature<KeepCursor>().feature.enabled = false }
                val centered = cursorAfterNext(context, world)
                val middle = context.computeOnClient<Pair<Double, Double>, Nothing> {
                    (it.window.screenWidth / 2).toDouble() to (it.window.screenHeight / 2).toDouble()
                }
                check(centered == middle) { "With Keep Cursor off the game should center the cursor at $middle, as vanilla does, and put it at $centered" }
            } finally {
                context.runOnClient<Nothing> {
                    DsModClient.feature<KeepCursor>().feature.enabled = true
                    it.player!!.closeContainer()
                }
            }
        }
    }
}
