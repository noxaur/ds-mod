package io.github.noxaur.dsmod.gametest

import com.sun.jna.Library
import com.sun.jna.Native
import net.fabricmc.api.ClientModInitializer
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents

/**
 * Ends the process once the game has decided to exit. On a machine whose monitors are asleep, the game
 * sometimes never returns from the C library's `exit`, after a pass and after a failure alike, and the Gradle
 * task hangs with the result unprinted. `_exit` skips the native teardown that waits. The status is 0 when
 * the game stopped the normal way, which it does when every test passed, and 1 when a failed test crashed it.
 */
object HardExit : ClientModInitializer {
    @Suppress("FunctionName")
    private interface LibC : Library {
        fun _exit(status: Int)
    }

    @Volatile
    private var stopped = false

    override fun onInitializeClient() {
        ClientLifecycleEvents.CLIENT_STOPPING.register { stopped = true }
        Runtime.getRuntime().addShutdownHook(Thread {
            // The log's own hook runs beside this one and needs a moment to write the last lines.
            Thread.sleep(2000)
            Native.load("c", LibC::class.java)._exit(if (stopped) 0 else 1)
        })
    }
}
