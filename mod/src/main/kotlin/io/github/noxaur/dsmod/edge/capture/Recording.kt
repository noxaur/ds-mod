package io.github.noxaur.dsmod.edge.capture

import io.github.noxaur.dsmod.core.brief.Brief
import io.github.noxaur.dsmod.core.brief.BriefRun
import io.github.noxaur.dsmod.core.capture.CaptureIncompleteException
import io.github.noxaur.dsmod.core.hud.HudGroup
import io.github.noxaur.dsmod.core.text.text
import io.github.noxaur.dsmod.edge.Background
import io.github.noxaur.dsmod.edge.Chat
import io.github.noxaur.dsmod.edge.Hud
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper
import net.minecraft.client.KeyMapping
import net.minecraft.client.Minecraft
import org.lwjgl.glfw.GLFW
import org.slf4j.LoggerFactory
import java.nio.file.Files
import java.nio.file.Path

/**
 * Walks the player through a [Brief] and records it. The player's key starts the capture and moves each step on. A
 * developer's run, from `-Pbrief`, keeps its capture in the game folder. Nothing here runs when no brief is on: [tick]
 * is one null check.
 */
object Recording {
    private var run: BriefRun? = null
    private var file: Path? = null
    private var shown: Any? = null
    private var saved: String? = null
    private lateinit var nextStep: KeyMapping

    /** The brief on screen, which the window title shows. */
    fun name(): String? = run?.brief?.name

    /** Shows [brief], and waits for the player's key to start recording. */
    fun start(brief: Brief) {
        open(brief)
        Minecraft.getInstance().updateTitle()
    }

    /** [start] for `-Pbrief`, before the window exists and takes its title when it opens. */
    fun startAtLaunch(brief: Brief) = open(brief)

    private fun open(brief: Brief) {
        end()
        run = BriefRun(brief)
    }

    /** Abandons the run and deletes a capture that is still recording. False when there was no run. */
    fun cancel(): Boolean {
        if (run == null) return false
        discard()
        return true
    }

    private fun end() {
        run = null
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
        saved = file?.fileName?.toString()
        shown = null
        if (session != null) Background.io.execute {
            try {
                session.close()
            } catch (_: CaptureIncompleteException) {
                client.execute { Chat.send(text { bad("The capture was not fully written. Record it again.") }) }
            }
        }
    }

    private fun tick(client: Minecraft) {
        val run = run ?: return
        val now = System.currentTimeMillis()
        var presses = 0
        while (nextStep.consumeClick()) presses++
        val steps = run.step(now, presses)
        if (steps.isNotEmpty()) {
            if (!Recorder.running) file = Recorder.start(client.gameDirectory.toPath().resolve("dsmod/captures"))
            steps.forEach { step -> Recorder.record("step") { step } }
            if (run.done) finish(client)
            if (this.run == null) return
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

    /** Quitting: a developer's capture is saved. */
    private fun stopping() {
        try {
            Recorder.stop()?.close()
        } catch (e: CaptureIncompleteException) {
            LoggerFactory.getLogger("dsmod").error("The capture was not fully written", e)
        }
    }

    fun register() {
        ClientLifecycleEvents.CLIENT_STOPPING.register { stopping() }
        nextStep = KeyMappingHelper.registerKeyMapping(KeyMapping("key.dsmod.next_step", GLFW.GLFW_KEY_RIGHT_BRACKET, KeyMapping.Category.MISC))
        ClientTickEvents.END_CLIENT_TICK.register(::tick)
    }
}
