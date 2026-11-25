package io.github.noxaur.dragen.gametest

import com.sun.jna.Library
import com.sun.jna.Native
import net.fabricmc.api.ClientModInitializer
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents
import org.apache.logging.log4j.LogManager

/** @keep On a machine whose monitors are asleep, the game sometimes never returns from the C library's `exit`, so the Gradle
 * task hangs with the result unprinted. `_exit` skips the native teardown that waits. */
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
            LogManager.shutdown()
            Native.load("c", LibC::class.java)._exit(if (stopped && TestClock.slow.isEmpty()) 0 else 1)
        })
    }
}
