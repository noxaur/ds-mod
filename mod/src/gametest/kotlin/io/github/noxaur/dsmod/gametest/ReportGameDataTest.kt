package io.github.noxaur.dsmod.gametest

import io.github.noxaur.dsmod.core.feature.contribute.ReportGameData
import io.github.noxaur.dsmod.edge.DsModClient
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext

class ReportGameDataTest : FabricClientGameTest {
    private fun listens(context: ClientGameTestContext): Boolean {
        context.waitTick()
        return context.computeOnClient<Boolean, Nothing> { DsModClient.game.metrics.onUnparsed != null }
    }

    override fun runTest(context: ClientGameTestContext) {
        check(!listens(context)) { "Report Game Data listens for unread lines while off" }
        try {
            context.runOnClient<Nothing> { DsModClient.feature<ReportGameData>().feature.enabled = true }
            check(listens(context)) { "Report Game Data does not listen for unread lines while on" }
            context.runOnClient<Nothing> { DsModClient.anyServer = false }
            check(!listens(context)) { "Report Game Data listens off dragsim.co" }
        } finally {
            context.runOnClient<Nothing> {
                DsModClient.anyServer = true
                DsModClient.feature<ReportGameData>().feature.enabled = false
            }
        }
        check(!listens(context)) { "Report Game Data still listens after it was turned off" }
    }
}
