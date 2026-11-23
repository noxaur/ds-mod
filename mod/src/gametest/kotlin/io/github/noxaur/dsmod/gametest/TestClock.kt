package io.github.noxaur.dsmod.gametest

import net.fabricmc.api.ClientModInitializer
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.fabricmc.fabric.impl.client.gametest.FabricClientGameTestRunner
import net.fabricmc.loader.api.entrypoint.EntrypointContainer

/** @keep Fabric names the running test in a field and runs the client only between the test's steps, so a tick that sees a new
 * name ends the last test. */
object TestClock : ClientModInitializer {
    private const val CAP_SECONDS = 20.0

    private val LONG_BY_DESIGN = setOf("PerfTest")

    private var running: EntrypointContainer<*>? = null
    private var startedAt = 0L
    private var ticks = 0

    val slow = mutableListOf<String>()

    override fun onInitializeClient() {
        ClientTickEvents.START_CLIENT_TICK.register { lap(FabricClientGameTestRunner.currentlyRunningGameTest) }
        ClientLifecycleEvents.CLIENT_STOPPING.register {
            lap(null)
            for (line in slow) System.err.println("TestClock: $line")
        }
    }

    private fun lap(now: EntrypointContainer<*>?) {
        ticks++
        if (now === running) return
        val at = System.nanoTime()
        running?.let { test ->
            val seconds = (at - startedAt) / 1e9
            val name = test.definition.substringAfterLast('.')
            println("TestClock: $name took %.1fs".format(seconds))
            println("TestClock: $name ran $ticks ticks, %.0f ms each".format(seconds * 1000 / ticks))
            if (seconds > CAP_SECONDS && name !in LONG_BY_DESIGN) {
                slow += "$name took %.1fs, over the %.0fs cap. Wait for the condition the test needs instead of a fixed number of ticks, or split the test.".format(seconds, CAP_SECONDS)
            }
        }
        running = now
        startedAt = at
        ticks = 0
    }
}
