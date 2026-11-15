package io.github.noxaur.dsmod.edge.capture

import io.github.noxaur.dsmod.core.brief.Brief
import io.github.noxaur.dsmod.core.brief.BriefRun
import io.github.noxaur.dsmod.core.hud.HudGroup
import io.github.noxaur.dsmod.core.text.text
import io.github.noxaur.dsmod.edge.Chat
import io.github.noxaur.dsmod.edge.Hud
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.minecraft.client.KeyMapping
import net.minecraft.client.Minecraft
import java.nio.file.Files

/**
 * Walks the player through a [Brief] and records it. The player's key starts the capture and moves each step on.
 * When a capture is already running, the steps go into it and it stays running after the last. Nothing here runs
 * when no brief is on: [tick] is one null check.
 */
object Recording {
    private var run: BriefRun? = null

    /** The capture this run started, until it ends. */
    private var capture: CaptureSession? = null
    private var shown: Any? = null
    private var saved: String? = null
    private lateinit var nextStep: KeyMapping

    /** The brief on screen, which the window title shows. */
    fun name(): String? = run?.brief?.name

    /** Shows [brief], and waits for the player's key to start recording. */
    fun start(brief: Brief) {
        startAtLaunch(brief)
        Minecraft.getInstance().updateTitle()
    }

    /** [start] for `-Pbrief`, before the window exists and takes its title when it opens. */
    fun startAtLaunch(brief: Brief) {
        cancel()
        run = BriefRun(brief)
    }

    /** Abandons the run and deletes the capture it started. False when there was no run. */
    fun cancel(): Boolean {
        if (run == null) return false
        capture?.let { unwanted -> Recorder.stop(unwanted) { Files.deleteIfExists(unwanted.file) } }
        run = null
        capture = null
        shown = null
        saved = null
        Hud.hide("brief")
        Minecraft.getInstance().updateTitle()
        return true
    }

    private fun tick(client: Minecraft) {
        val run = run ?: return
        val now = System.currentTimeMillis()
        var presses = 0
        while (nextStep.consumeClick()) presses++
        val steps = run.step(now, presses)
        if (steps.isNotEmpty()) {
            if (!Recorder.running) capture = Recorder.start(client.gameDirectory.toPath().resolve("dsmod/captures"))
            steps.forEach { step -> Recorder.record("step") { step } }
            if (run.done) capture?.let { own ->
                capture = null
                saved = own.file.fileName.toString()
                Recorder.stop(own) { whole ->
                    if (!whole) client.execute { Chat.send(text { bad("The capture was not fully written. Record it again.") }) }
                }
            }
        }
        val width = client.window.guiScaledWidth
        val state = listOf(run.current, run.remaining(now), width)
        if (state == shown) return
        shown = state
        val block = run.hud(now, nextStep.translatedKeyMessage.string, saved)
        val widest = (block.rows.map { it.text } + block.header).maxOf { client.font.width(it.plain) }
        // Under the status effects in the top right corner, and above the server's sidebar. The plate adds 7 pixels around the text.
        Hud.show("brief", HudGroup("brief", width - widest - 9, 54, background = true), block, placeable = false)
    }

    /** Called by whatever can start a brief, which brings the key that moves it on. */
    fun register(nextStep: KeyMapping) {
        this.nextStep = nextStep
        ClientTickEvents.END_CLIENT_TICK.register(::tick)
    }
}
