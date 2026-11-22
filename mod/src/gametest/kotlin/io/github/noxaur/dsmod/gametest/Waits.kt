package io.github.noxaur.dsmod.gametest

import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext
import net.minecraft.client.Minecraft

fun ClientGameTestContext.waitUntil(what: String, ticks: Int = 200, done: (Minecraft) -> Boolean) {
    try {
        waitFor(done, ticks)
    } catch (e: RuntimeException) {
        throw AssertionError("Timed out waiting until $what", e)
    }
}
