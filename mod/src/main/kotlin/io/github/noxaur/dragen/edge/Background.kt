package io.github.noxaur.dragen.edge

import java.util.concurrent.Executors

object Background {
    val io = Executors.newSingleThreadExecutor { Thread(it, "dragen-io").apply { isDaemon = true } }
}
