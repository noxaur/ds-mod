package io.github.noxaur.dsmod.gametest

import io.github.noxaur.dsmod.core.game.ServerSpan
import io.github.noxaur.dsmod.core.game.ServerText
import io.github.noxaur.dsmod.core.game.TabList
import io.github.noxaur.dsmod.edge.DsModClient
import io.github.noxaur.dsmod.edge.Hud
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext

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

const val DRAGON_TIMER = "dragon-timer"

fun TestSingleplayerContext.eggSpawns() = server.runCommand("""tellraw @a "§5✦ §dThe Egg has Spawned!"""")

fun ClientGameTestContext.showDragonTimer(world: TestSingleplayerContext) {
    enterTheEnd()
    world.eggSpawns()
    waitUntil("the dragon timer is on the HUD") { Hud.find(DRAGON_TIMER) != null }
}
