package io.github.noxaur.dsmod.gametest

import io.github.noxaur.dsmod.core.game.ServerSpan
import io.github.noxaur.dsmod.core.game.ServerText
import io.github.noxaur.dsmod.core.game.TabList
import io.github.noxaur.dsmod.edge.DsModClient
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext

/**
 * Puts the player in The End for the features locked to it, since a local world's tab list names no area. A
 * capture or a tap that just started makes the next poll post the real tab list again, so this waits one out.
 */
fun ClientGameTestContext.enterTheEnd() {
    waitTicks(6)
    runOnClient<Nothing> {
        DsModClient.game.post(System.currentTimeMillis(), TabList(listOf(ServerText(ServerSpan("Area: The End")))))
    }
}
