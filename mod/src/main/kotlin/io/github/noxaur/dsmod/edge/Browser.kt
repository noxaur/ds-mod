package io.github.noxaur.dsmod.edge

import java.util.concurrent.TimeUnit

/** @keep Minecraft's openUri logs the address when it fails, and a login address holds a token that must not reach a log. */
object Browser {
    private const val WAIT_SECONDS = 3L

    fun open(url: String): Boolean {
        val os = System.getProperty("os.name").lowercase()
        val command = when {
            "win" in os -> listOf("rundll32", "url.dll,FileProtocolHandler", url)
            "mac" in os -> listOf("open", url)
            else -> listOf("xdg-open", url)
        }
        return try {
            val opener = ProcessBuilder(command).redirectErrorStream(true).redirectOutput(ProcessBuilder.Redirect.DISCARD).start()
            // @keep An opener that stays running is handing the address to a browser that is starting.
            !opener.waitFor(WAIT_SECONDS, TimeUnit.SECONDS) || opener.exitValue() == 0
        } catch (_: Exception) {
            false
        }
    }
}
