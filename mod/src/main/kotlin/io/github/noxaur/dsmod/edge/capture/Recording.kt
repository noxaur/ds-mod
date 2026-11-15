package io.github.noxaur.dsmod.edge.capture

import io.github.noxaur.dsmod.core.brief.Brief
import io.github.noxaur.dsmod.core.brief.BriefRun
import io.github.noxaur.dsmod.core.capture.CaptureIncompleteException
import io.github.noxaur.dsmod.core.hud.HudGroup
import io.github.noxaur.dsmod.core.text.text
import io.github.noxaur.dsmod.edge.Background
import io.github.noxaur.dsmod.edge.Chat
import io.github.noxaur.dsmod.edge.Hud
import io.github.noxaur.dsmod.edge.contribute.Contribute
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper
import net.minecraft.client.KeyMapping
import net.minecraft.client.Minecraft
import org.lwjgl.glfw.GLFW
import org.slf4j.LoggerFactory
import java.nio.file.Files
import java.nio.file.Path

/**
 * Walks the player through a [Brief] and records it. The player's key starts the capture and moves each step on, and
 * a run for a request ends in [Contribute.send]. A developer's run, from `-Pbrief`, keeps its capture in the game
 * folder instead. Nothing here runs when no brief is on: [tick] is one null check.
 */
object Recording {
    /** The request this run answers, or null for a developer's brief. */
    private class Target(val requestId: String)

    private var run: BriefRun? = null
    private var target: Target? = null
    private var file: Path? = null
    private var shown: Any? = null
    private var saved: String? = null
    private var cancelledByDisconnect = false
    private lateinit var nextStep: KeyMapping

    /** The brief on screen, which the window title shows. */
    fun name(): String? = run?.brief?.name

    /** The name of the key that moves the run on. */
    fun key(): String = nextStep.translatedKeyMessage.string

    /** Whether a brief is on screen, including a finished developer's one. */
    val active get() = run != null

    /** Whether a run is still being worked through. A finished developer's brief stays on screen and is not busy. */
    val busy get() = run?.done == false

    /**
     * Shows [brief], and waits for the player's key to start recording. With a [requestId] the run is a contribution,
     * and [timeoutSeconds] is how long it may take. Without, it is a developer's brief with no limit.
     */
    fun start(brief: Brief, requestId: String? = null, timeoutSeconds: Int? = null) {
        open(brief, requestId, timeoutSeconds)
        Minecraft.getInstance().updateTitle()
    }

    /** [start] for `-Pbrief`, before the window exists and takes its title when it opens. */
    fun startAtLaunch(brief: Brief) = open(brief, null, null)

    private fun open(brief: Brief, requestId: String?, timeoutSeconds: Int?) {
        end()
        run = BriefRun(brief, System.currentTimeMillis(), timeoutSeconds)
        target = requestId?.let(::Target)
    }

    /** Ends the run early and sends what was recorded. */
    fun submit(): Boolean {
        val run = run ?: return false
        if (run.current < 0 || run.done) return false
        run.finish(System.currentTimeMillis())
        val done = run.record()
        Recorder.record("step") { done }
        finish(Minecraft.getInstance())
        return true
    }

    /** Abandons the run and deletes a capture that is still recording. False when there was no run. */
    fun cancel(): Boolean {
        if (run == null) return false
        discard()
        return true
    }

    private fun end() {
        run = null
        target = null
        file = null
        shown = null
        saved = null
        Hud.hide("brief")
    }

    private fun discard() {
        val session = Recorder.stop()
        val unwanted = file
        end()
        Minecraft.getInstance().updateTitle()
        if (session != null) Background.io.execute {
            try {
                session.close()
            } catch (_: CaptureIncompleteException) {
                // Nobody wants the file, whole or not.
            } finally {
                unwanted?.let(Files::deleteIfExists)
            }
        }
    }

    private fun finish(client: Minecraft) {
        val session = Recorder.stop()
        val requestId = target?.requestId
        val recorded = file
        if (requestId == null) {
            saved = recorded?.fileName?.toString()
            shown = null
            if (session != null) Background.io.execute {
                try {
                    session.close()
                } catch (_: CaptureIncompleteException) {
                    client.execute { Chat.send(text { bad("The capture was not fully written. Record it again.") }) }
                }
            }
            return
        }
        end()
        client.updateTitle()
        if (session != null && recorded != null) Contribute.send(requestId, session, recorded)
    }

    private fun tick(client: Minecraft) {
        if (cancelledByDisconnect && client.player != null) {
            cancelledByDisconnect = false
            Chat.send(text { bad("Your recording was cancelled because you left the server. Nothing was sent.") })
        }
        val run = run ?: return
        val now = System.currentTimeMillis()
        if (run.timedOut(now)) {
            discard()
            Chat.send(text { bad("The recording ran out of time, so nothing was sent.") })
            return
        }
        var presses = 0
        while (nextStep.consumeClick()) presses++
        val steps = run.step(now, presses)
        if (steps.isNotEmpty()) {
            if (!Recorder.running) {
                val dir = if (target == null) client.gameDirectory.toPath().resolve("dsmod/captures") else Contribute.dir()
                file = Recorder.start(dir)
            }
            steps.forEach { step -> Recorder.record("step") { step } }
            if (run.done) finish(client)
            if (this.run == null) return
        }
        val width = client.window.guiScaledWidth
        val state = listOf(run.current, run.remaining(now), width, run.timeLeft(now))
        if (state == shown) return
        shown = state
        val block = run.hud(now, nextStep.translatedKeyMessage.string, saved)
        val widest = (block.rows.map { it.text } + block.header).maxOf { client.font.width(it.plain) }
        // Under the status effects in the top right corner, and above the server's sidebar. The plate adds 7 pixels around the text.
        Hud.show("brief", HudGroup("brief", width - widest - 9, 54, background = true), block, placeable = false)
    }

    /** Quitting: a contribution is discarded, because the player cannot finish it, and a developer's capture is saved. */
    private fun stopping() {
        if (target != null) {
            discard()
        } else {
            try {
                Recorder.stop()?.close()
            } catch (e: CaptureIncompleteException) {
                LoggerFactory.getLogger("dsmod").error("The capture was not fully written", e)
            }
        }
        Contribute.settle()
    }

    fun register() {
        // A change of world on the same server is no disconnect, and briefs include warps.
        ClientPlayConnectionEvents.DISCONNECT.register { _, _ ->
            if (target != null) {
                discard()
                cancelledByDisconnect = true
            }
        }
        ClientLifecycleEvents.CLIENT_STOPPING.register { stopping() }
        nextStep = KeyMappingHelper.registerKeyMapping(KeyMapping("key.dsmod.next_step", GLFW.GLFW_KEY_RIGHT_BRACKET, KeyMapping.Category.MISC))
        ClientTickEvents.END_CLIENT_TICK.register(::tick)
    }
}
