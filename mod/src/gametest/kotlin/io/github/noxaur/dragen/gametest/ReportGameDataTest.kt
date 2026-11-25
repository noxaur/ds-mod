package io.github.noxaur.dragen.gametest

import io.github.noxaur.dragen.core.feature.contribute.reportgamedata.ReportGameData
import io.github.noxaur.dragen.edge.DragenClient
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext

class ReportGameDataTest : FabricClientGameTest {
    private fun listens(context: ClientGameTestContext): Boolean {
        context.waitTick()
        return context.computeOnClient<Boolean, Nothing> { DragenClient.game.metrics.onUnparsed != null }
    }

    override fun runTest(context: ClientGameTestContext) {
        check(!listens(context)) { "Report Game Data listens for unread lines while off" }
        try {
            context.runOnClient<Nothing> { DragenClient.feature<ReportGameData>().feature.enabled = true }
            check(listens(context)) { "Report Game Data does not listen for unread lines while on" }
            context.runOnClient<Nothing> { DragenClient.anyServer = false }
            check(!listens(context)) { "Report Game Data listens off dragsim.co" }
        } finally {
            context.runOnClient<Nothing> {
                DragenClient.anyServer = true
                DragenClient.feature<ReportGameData>().feature.enabled = false
            }
        }
        check(!listens(context)) { "Report Game Data still listens after it was turned off" }
    }
}
