package io.github.noxaur.dragen.gametest

import io.github.noxaur.dragen.core.game.ServerSpan
import io.github.noxaur.dragen.core.game.ServerText
import io.github.noxaur.dragen.core.game.TabList
import io.github.noxaur.dragen.edge.DragenClient
import io.github.noxaur.dragen.edge.Hud
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext

/** @keep A local world's tab list names no area. */
fun ClientGameTestContext.enterTheEnd() {
    waitTicks(6)
    runOnClient<Nothing> {
        DragenClient.game.post(System.currentTimeMillis(), TabList(listOf(ServerText(ServerSpan("Area: The End")))))
    }
}

const val DRAGON_TIMER = "dragon-timer"

fun forgetHudPlace(id: String) {
    DragenClient.settings.hud.positions.value -= id
}

fun ClientGameTestContext.inWorldWithoutTimerPlace(test: (TestSingleplayerContext) -> Unit) = worldBuilder().create().use { world ->
    forgetHudPlace(DRAGON_TIMER)
    test(world)
}

fun TestSingleplayerContext.eggSpawns() = server.runCommand("""tellraw @a "§5✦ §dThe Egg has Spawned!"""")

fun ClientGameTestContext.showDragonTimer(world: TestSingleplayerContext) {
    enterTheEnd()
    world.eggSpawns()
    waitUntil("the dragon timer is on the HUD") { Hud.find(DRAGON_TIMER) != null }
}

fun ClientGameTestContext.readout(id: String) = computeOnClient<List<String>, Nothing> {
    DragenClient.game.readouts.single { it.feature.id == id }.hud?.rows?.map { row -> row.text.plain } ?: emptyList()
}
