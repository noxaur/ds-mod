package io.github.noxaur.dsmod.edge.capture

import io.github.noxaur.dsmod.core.backend.Request
import io.github.noxaur.dsmod.core.brief.Brief
import io.github.noxaur.dsmod.core.brief.BriefRun
import io.github.noxaur.dsmod.core.hud.HudGroup
import io.github.noxaur.dsmod.core.text.text
import io.github.noxaur.dsmod.edge.Chat
import io.github.noxaur.dsmod.edge.Hud
import io.github.noxaur.dsmod.edge.contribute.Contribute
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents
import net.minecraft.client.KeyMapping
import net.minecraft.client.Minecraft
import org.lwjgl.glfw.GLFW
import java.nio.file.Files

/**
 * Walks the player through a [Brief] and records it. The player's key starts the capture and moves each step on.
 * A run for a [Request] records into its own capture, has the request's time limit, and ends in [Contribute.seal].
 * A developer's brief, from `-Pbrief`, records into a capture that is already running if there is one, and keeps
 * its file in the game folder. Nothing here runs when no brief is on: [tick] is one null check. Every call is on
 * the client thread. `specs/Contribute.tla` is the life of a request's run.
 */
object Recording {
    private var run: BriefRun? = null

    /** The request this run answers, or null for a developer's brief. */
    private var request: Request? = null

    /** The capture this run started, until it ends. */
    private var capture: CaptureSession? = null
    private var shown: Any? = null
    private var saved: String? = null
    private var leftServer = false

    @Volatile
    private var disconnected = false
    private lateinit var nextStep: KeyMapping

    /** The brief on screen, which the window title shows. */
    fun name(): String? = run?.brief?.name

    /** The name of the key that moves a run on. */
    fun key(): String = nextStep.translatedKeyMessage.string

    /** Whether a run is still being worked through. A developer's finished brief stays on screen and is not busy. */
    val busy get() = run?.done == false

    /** Shows a developer's [brief], and waits for the player's key to start recording. */
    fun start(brief: Brief) {
        startAtLaunch(brief)
        Minecraft.getInstance().updateTitle()
    }

    /** [start] for `-Pbrief`, before the window exists and takes its title when it opens. */
    fun startAtLaunch(brief: Brief) {
        cancel()
        run = BriefRun(brief)
    }

    /** Shows the brief of [request], with the request's time limit counted from now. */
    fun contribute(request: Request) {
        cancel()
        run = BriefRun(request.brief, System.currentTimeMillis(), request.timeoutSeconds)
        this.request = request
        Minecraft.getInstance().updateTitle()
    }

    /** Abandons the run and deletes the capture it started. False when there was no run. */
    fun cancel(): Boolean {
        if (run == null) return false
        capture?.let { unwanted -> Recorder.stop(unwanted) { Files.deleteIfExists(unwanted.file) } }
        end()
        return true
    }

    private fun end() {
        run = null
        request = null
        capture = null
        shown = null
        saved = null
        Hud.hide("brief")
        Minecraft.getInstance().updateTitle()
    }

    /** Ends a request's run before its last step and sends what it recorded. False when no request's run has started. */
    fun submit(): Boolean {
        val run = run ?: return false
        if (request == null || run.current < 0 || run.done) return false
        run.finish(System.currentTimeMillis())
        Recorder.record("step") { run.record() }
        finish(Minecraft.getInstance(), run)
        return true
    }

    private fun finish(client: Minecraft, run: BriefRun) {
        val own = capture ?: return
        capture = null
        val request = request
        if (request != null) {
            end()
            Recorder.stop(own, Contribute.seal(request, own.file))
            return
        }
        Recorder.stop(own) { whole ->
            client.execute {
                if (!whole) Chat.send(text { bad("The capture was not fully written. Record it again.") })
                else if (this.run === run) {
                    saved = own.file.fileName.toString()
                    shown = null
                }
            }
        }
    }

    private fun tick(client: Minecraft) {
        if (disconnected) {
            disconnected = false
            if (request != null && cancel()) leftServer = true
        }
        if (leftServer && client.player != null) {
            leftServer = false
            Chat.send(text { bad("Your recording was cancelled because you left the server. Nothing was sent.") })
        }
        val run = run ?: return
        val now = System.currentTimeMillis()
        if (run.timedOut(now)) {
            cancel()
            Chat.send(text { bad("The recording ran out of time, so nothing was sent. Open Contribute in the settings to try again.") })
            return
        }
        var presses = 0
        while (nextStep.consumeClick()) presses++
        val steps = run.step(now, presses)
        if (steps.isNotEmpty()) {
            // A request's run always has a capture of its own, which only Contribute may hand out.
            if (capture == null && (request != null || !Recorder.running)) {
                capture = Recorder.start(request?.let { Contribute.dir() } ?: client.gameDirectory.toPath().resolve("dsmod/captures"))
            }
            steps.forEach { step -> Recorder.record("step") { step } }
            if (run.done) finish(client, run)
            if (this.run == null) return
        }
        val width = client.window.guiScaledWidth
        val state = listOf(run.current, run.remaining(now), run.timeLeft(now), width, saved)
        if (state == shown) return
        shown = state
        val block = run.hud(now, key(), saved)
        val widest = (block.rows.map { it.text } + block.header).maxOf { client.font.width(it.plain) }
        // Under the status effects in the top right corner, and above the server's sidebar. The plate adds 7 pixels around the text.
        Hud.show("brief", HudGroup("brief", width - widest - 9, 54, background = true), block, placeable = false)
    }

    /** Quitting: a request's run cannot be finished later, so it is discarded. A developer's capture is the recorder's to save. */
    fun quit() {
        if (request != null) cancel()
    }

    fun register() {
        nextStep = KeyMappingHelper.registerKeyMapping(KeyMapping("key.dsmod.next_step", GLFW.GLFW_KEY_RIGHT_BRACKET, KeyMapping.Category.MISC))
        // Fabric says so on the network thread, and the tick reads it on the client thread. A change of world on the
        // same connection is no disconnect, and a brief's warps change the world.
        ClientPlayConnectionEvents.DISCONNECT.register { _, _ -> disconnected = true }
        ClientTickEvents.END_CLIENT_TICK.register(::tick)
    }
}
