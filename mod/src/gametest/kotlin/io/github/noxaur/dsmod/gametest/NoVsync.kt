package io.github.noxaur.dsmod.gametest

import net.fabricmc.api.ClientModInitializer
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents

/** @keep With vsync on, a frame waits for the display, and with the monitors asleep it never comes, so the tests stall. Fabric
 * restores the default options before each test, so this turns it off again every tick. */
object NoVsync : ClientModInitializer {
    override fun onInitializeClient() {
        ClientTickEvents.START_CLIENT_TICK.register { client ->
            if (client.options.enableVsync().get()) client.options.enableVsync().set(false)
        }
    }
}
