package io.github.noxaur.dsmod.edge

import java.util.concurrent.Executors

object Background {
    val io = Executors.newSingleThreadExecutor { Thread(it, "dsmod-io").apply { isDaemon = true } }
}
