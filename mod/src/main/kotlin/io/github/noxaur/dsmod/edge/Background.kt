package io.github.noxaur.dsmod.edge

import java.util.concurrent.Executors

/** The one thread for disk work that would hold up the client thread, such as finishing a capture. */
object Background {
    val io = Executors.newSingleThreadExecutor { Thread(it, "dsmod-io").apply { isDaemon = true } }
}
