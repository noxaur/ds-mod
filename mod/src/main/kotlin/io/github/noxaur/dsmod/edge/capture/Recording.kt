package io.github.noxaur.dsmod.edge.capture

import io.github.noxaur.dsmod.core.backend.Request
import io.github.noxaur.dsmod.core.brief.Brief
import io.github.noxaur.dsmod.core.brief.BriefRun
import io.github.noxaur.dsmod.core.hud.HudGroup
import io.github.noxaur.dsmod.core.text.text
import io.github.noxaur.dsmod.edge.Chat
import io.github.noxaur.dsmod.edge.DsModClient
import io.github.noxaur.dsmod.edge.Hud
import io.github.noxaur.dsmod.edge.contribute.Contribute
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper
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

    /** Drops `]` presses that arrived while no run was taking them, so they cannot finish the next one. */
    private fun drainNextStep() {
        if (::nextStep.isInitialized) while (nextStep.consumeClick()) Unit
    }

    /** Abandons the run and deletes the capture it started. False when there was no run. */
    fun cancel(): Boolean {
        drainNextStep()
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

    /** The player left the server, so a request's run cannot go on, and is discarded. They are told once they are back in a world. */
    fun left() {
        if (request != null && cancel()) leftServer = true
    }

    /** Ends a request's run before its last step and sends what it recorded, unless it just ended. False when no request's run has started. */
    fun submit(): Boolean {
        val run = run ?: return false
        if (request == null || run.current < 0 || run.done) return false
        // A run that ran out of time a moment ago is not sent, and the player has been told why.
        if (ended(run, System.currentTimeMillis())) return true
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

    /**
     * Discards a request's run that ran out of time, and says whether it did. The tick asks first, and so does
     * [submit], which may come between the time limit and the next tick.
     */
    private fun ended(run: BriefRun?, now: Long): Boolean {
        if (run == null || !run.timedOut(now)) return false
        cancel()
        Chat.send(text { bad("The recording ran out of time, so nothing was sent. Open Contribute in the settings to try again.") })
        return true
    }

    private fun tick(client: Minecraft) {
        // A run that lost its connection during this tick must not finish and send now.
        DsModClient.leave()
        val now = System.currentTimeMillis()
        if (ended(run, now)) {
            drainNextStep()
            return
        }
        if (leftServer && client.player != null) {
            leftServer = false
            Chat.send(text { bad("Your recording was cancelled because you left the server. Nothing was sent.") })
        }
        val run = run ?: run {
            drainNextStep()
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
        ClientTickEvents.END_CLIENT_TICK.register(::tick)
    }
}
