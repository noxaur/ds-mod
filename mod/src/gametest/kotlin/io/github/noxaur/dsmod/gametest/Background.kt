package io.github.noxaur.dsmod.gametest

import com.sun.jna.Library
import com.sun.jna.Native
import com.sun.jna.Platform
import org.lwjgl.glfw.GLFW
import java.nio.file.Path
import java.util.concurrent.TimeUnit
import kotlin.io.path.writeText

/**
 * Keeps a test run out of the way of whoever is at the machine. The window opens without taking the
 * keyboard, under a class of its own, so a window manager can tell it from the game the developer plays.
 * On Hyprland, which Omarchy runs, a rule sends that class to an empty workspace. The game's sound goes to
 * `audio.wav` in the run folder instead of the speakers, so a test's sound can still be checked.
 */
object Background {
    private const val WINDOW_CLASS = "dsmod-gametest"

    private interface LibC : Library {
        fun setenv(name: String, value: String, overwrite: Int): Int
    }

    /** Runs just before the game creates its window, which is also before it opens a sound device. */
    @JvmStatic
    fun beforeWindow() {
        GLFW.glfwWindowHint(GLFW.GLFW_FOCUSED, GLFW.GLFW_FALSE)
        GLFW.glfwWindowHint(GLFW.GLFW_FOCUS_ON_SHOW, GLFW.GLFW_FALSE)
        GLFW.glfwWindowHintString(GLFW.GLFW_X11_CLASS_NAME, WINDOW_CLASS)
        GLFW.glfwWindowHintString(GLFW.GLFW_X11_INSTANCE_NAME, WINDOW_CLASS)
        GLFW.glfwWindowHintString(GLFW.GLFW_WAYLAND_APP_ID, WINDOW_CLASS)
        if (System.getenv("HYPRLAND_INSTANCE_SIGNATURE") != null) sendToEmptyWorkspace()
        // Windows has no `setenv`, so there the game still plays through the speakers.
        if (!Platform.isWindows()) writeSoundToFile()
    }

    // The rule is named, so each run replaces the last one instead of adding another.
    private fun sendToEmptyWorkspace() {
        val rule = """hl.window_rule({ name = "$WINDOW_CLASS", match = { class = "^$WINDOW_CLASS$" }, """ +
            """workspace = "empty silent", no_initial_focus = true })"""
        val hyprctl = ProcessBuilder("hyprctl", "eval", rule).redirectErrorStream(true).start()
        val done = hyprctl.waitFor(5, TimeUnit.SECONDS)
        val said = if (done) hyprctl.inputStream.bufferedReader().readText().trim() else "no answer in 5 seconds"
        if (!done || hyprctl.exitValue() != 0) System.err.println("Background: the test window will open in front, hyprctl said: $said")
    }

    // OpenAL reads its config when the game opens the first device. With `wave` as the only driver, a
    // failure leaves the game silent instead of falling back to the speakers.
    private fun writeSoundToFile() {
        val config = Path.of("alsoft.conf").toAbsolutePath()
        config.writeText("[general]\ndrivers = wave\n\n[wave]\nfile = ${config.resolveSibling("audio.wav")}\n")
        Native.load("c", LibC::class.java).setenv("ALSOFT_CONF", config.toString(), 1)
    }
}
