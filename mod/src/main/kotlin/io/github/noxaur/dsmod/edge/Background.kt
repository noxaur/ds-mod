package io.github.noxaur.dsmod.edge

import java.util.concurrent.Executors

/** The one thread for disk work that must not wait on the client thread, such as finishing and redacting a capture. */
object Background {
    val io = Executors.newSingleThreadExecutor { Thread(it, "dsmod-io").apply { isDaemon = true } }
}
