package io.github.noxaur.dragen.gametest

import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext

fun ClientGameTestContext.autoClickWorld(body: (TestSingleplayerContext) -> Unit) = worldBuilder().create().use { world ->
    world.server.runCommand("gamemode creative @a")
    world.server.runCommand("clear @a")
    waitTicks(20)
    input.lookAt(0f, -90f)
    waitTicks(3)
    body(world)
}

fun ClientGameTestContext.autoClicks(walk: Boolean, key: Int?) = counted {
    if (walk) input.holdKey { it.keyUp }
    if (key != null) input.holdKey(key)
    waitTicks(20)
    if (key != null) input.releaseKey(key)
    if (walk) input.releaseKey { it.keyUp }
}

fun ClientGameTestContext.ownClicks(button: Int) = counted {
    repeat(20) { input.pressMouse(button) }
}

private fun ClientGameTestContext.counted(body: () -> Unit): Pair<Int, Int> {
    runOnClient<Nothing> { ActionSpy.clear() }
    body()
    waitTicks(3)
    return computeOnClient<Pair<Int, Int>, Nothing> { ActionSpy.attacks to ActionSpy.uses }
}
