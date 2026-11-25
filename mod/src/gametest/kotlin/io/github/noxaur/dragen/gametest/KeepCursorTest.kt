package io.github.noxaur.dragen.gametest

import io.github.noxaur.dragen.core.feature.menus.keepcursor.KeepCursor
import io.github.noxaur.dragen.edge.DragenClient
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext
import kotlin.math.abs

class KeepCursorTest : FabricClientGameTest {
    private fun cursor(context: ClientGameTestContext) = context.computeOnClient<Pair<Double, Double>, Nothing> { it.mouseHandler.xpos() to it.mouseHandler.ypos() }

    private fun cursorAfterNext(context: ClientGameTestContext, world: TestSingleplayerContext): Pair<Pair<Double, Double>, Pair<Double, Double>> {
        ServerMenus.open(context, world, 6, "First")
        context.point(20, 25, settle = 2)
        val before = cursor(context)
        val gui = context.computeOnClient<Pair<Double, Double>, Nothing> { before.first * it.window.guiScaledWidth / it.window.screenWidth to before.second * it.window.guiScaledHeight / it.window.screenHeight }
        check(abs(gui.first - 20) < 0.01 && abs(gui.second - 25) < 0.01) { "The cursor went to $gui in GUI pixels, not 20, 25" }
        ServerMenus.open(context, world, 4, "Second")
        return before to cursor(context)
    }

    override fun runTest(context: ClientGameTestContext) {
        context.worldBuilder().create().use { world ->
            try {
                val (before, after) = cursorAfterNext(context, world)
                check(after == before) { "Keep Cursor let the cursor move" }

                context.runOnClient<Nothing> { DragenClient.feature<KeepCursor>().feature.enabled = false }
                val centered = cursorAfterNext(context, world).second
                val middle = context.computeOnClient<Pair<Double, Double>, Nothing> {
                    (it.window.screenWidth / 2).toDouble() to (it.window.screenHeight / 2).toDouble()
                }
                check(centered == middle) { "With Keep Cursor off the game should center the cursor at $middle, as vanilla does, and put it at $centered" }
            } finally {
                context.runOnClient<Nothing> {
                    DragenClient.feature<KeepCursor>().feature.enabled = true
                    it.player!!.closeContainer()
                }
            }
        }
    }
}
