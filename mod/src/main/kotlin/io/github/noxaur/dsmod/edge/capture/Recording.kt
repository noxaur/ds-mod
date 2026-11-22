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

/** @keep `specs/Contribute.tla` is the life of a request's run. */
object Recording {
    private const val BELOW_STATUS_EFFECTS = 54
    private const val PLATE_AND_MARGIN = 9

    private var run: BriefRun? = null
    private var request: Request? = null
    private var capture: CaptureSession? = null
    private var shown: Any? = null
    private var saved: String? = null
    private var leftServer = false
    private lateinit var nextStep: KeyMapping

    fun name(): String? = run?.brief?.name

    fun key(): String = nextStep.translatedKeyMessage.string

    val busy get() = run?.done == false

    fun startAtLaunch(brief: Brief) {
        cancel()
        run = BriefRun(brief)
    }

    fun contribute(request: Request) {
        cancel()
        run = BriefRun(request.brief, System.currentTimeMillis(), request.timeoutSeconds)
        this.request = request
        Minecraft.getInstance().updateTitle()
    }

    private fun drainNextStep() {
        if (::nextStep.isInitialized) while (nextStep.consumeClick()) Unit
    }

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

    fun left() {
        if (request != null && cancel()) leftServer = true
    }

    fun submit(): Boolean {
        val run = run ?: return false
        if (request == null || run.current < 0 || run.done) return false
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

    private fun ended(run: BriefRun?, now: Long): Boolean {
        if (run == null || !run.timedOut(now)) return false
        cancel()
        Chat.send(text { bad("The recording ran out of time, so nothing was sent. Open Contribute in the settings to try again.") })
        return true
    }

    private fun tick(client: Minecraft) {
        // @keep A run that lost its connection during this tick must not finish and send now.
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
            // @keep A request's run always has a capture of its own, which only Contribute may hand out.
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
        Hud.show("brief", HudGroup("brief", width - widest - PLATE_AND_MARGIN, BELOW_STATUS_EFFECTS, background = true), block, placeable = false)
    }

    fun quit() {
        if (request != null) cancel()
    }

    fun register() {
        nextStep = KeyMappingHelper.registerKeyMapping(KeyMapping("key.dsmod.next_step", GLFW.GLFW_KEY_RIGHT_BRACKET, KeyMapping.Category.MISC))
        ClientTickEvents.END_CLIENT_TICK.register(::tick)
    }
}
