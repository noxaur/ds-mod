package io.github.noxaur.dsmod.debug

import io.github.noxaur.dsmod.core.hud.HudGroup
import io.github.noxaur.dsmod.debug.brief.Brief
import io.github.noxaur.dsmod.debug.brief.BriefRun
import io.github.noxaur.dsmod.debug.capture.CaptureSession
import io.github.noxaur.dsmod.debug.capture.toCaptureJson
import io.github.noxaur.dsmod.edge.DsModClient
import io.github.noxaur.dsmod.edge.Hud
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import net.fabricmc.api.ClientModInitializer
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback
import net.fabricmc.fabric.api.client.command.v2.ClientCommands
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientEntityEvents
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents
import net.minecraft.client.KeyMapping
import net.minecraft.client.Minecraft
import net.minecraft.network.chat.Component
import net.minecraft.network.protocol.Packet
import org.lwjgl.glfw.GLFW
import java.nio.file.Path

object DsModDebug : ClientModInitializer {
    @Volatile
    private var capture: CaptureSession? = null

    private var brief: BriefRun? = null
    private var briefShown: Any? = null
    private var briefSaved: Path? = null

    /** Read by BriefTitleMixin for the window title. */
    fun briefName(): String? = brief?.brief?.name

    /** Shows [brief] on the HUD and in the window title, or takes it away for null. The developer's key starts it, and with it a capture. */
    fun showBrief(client: Minecraft, brief: Brief?) {
        this.brief = brief?.let(::BriefRun)
        briefShown = null
        briefSaved = null
        if (brief == null) Hud.hide("brief")
        client.updateTitle()
    }

    /** Moves the brief on for the key and for a timer that ran out, marks each step in the capture, and saves the capture after the last. */
    private fun tickBrief(client: Minecraft, nextStep: KeyMapping) {
        val run = brief ?: return
        val now = System.currentTimeMillis()
        var moved = run.tick(now)
        while (nextStep.consumeClick()) moved = run.advance(now) || moved
        if (moved) {
            if (capture == null) toggleCapture(client)
            capture?.record("step") { run.record() }
            if (run.done) briefSaved = toggleCapture(client)
        }
        val width = client.window.guiScaledWidth
        val shown = Triple(run.current, run.remaining(now), width)
        if (shown == briefShown) return
        briefShown = shown
        val block = run.hud(now, nextStep.translatedKeyMessage.string, briefSaved?.fileName?.toString())
        val widest = (block.rows.map { it.text } + block.header).maxOf { client.font.width(it.plain) }
        // Under the status effects in the top right corner, and above the server's sidebar. The plate adds 7 pixels around the text.
        Hud.show("brief", HudGroup("brief", width - widest - 9, 54, background = true), block, placeable = false)
    }

    /** Starts a capture, or saves the running one. Returns the capture file either way. */
    fun toggleCapture(client: Minecraft): Path {
        val running = capture
        if (running != null) {
            capture = null
            running.close()
            return running.file
        }
        return CaptureSession(client.gameDirectory.toPath().resolve("dsmod/captures"), DsModClient.game).also { capture = it }.file
    }

    /** Called by CapturePacketsMixin on the client thread. */
    fun onPacket(packet: Packet<*>) {
        capture?.packet(packet)
    }

    /** Called by CaptureInputMixin on the client thread, for `swing` or `use`. */
    fun onInput(client: Minecraft, type: String) {
        capture?.input(client, type)
    }

    override fun onInitializeClient() {
        // A developer tests in singleplayer and on servers of their own, so the debug build runs everywhere.
        DsModClient.anyServer = true
        // `./gradlew runClient -Pbrief=<file>` sets this. The window does not exist yet, and takes its title when it opens.
        System.getProperty("dsmod.brief")?.let { brief = BriefRun(Brief.read(Path.of(it))) }
        val nextStep = KeyMappingHelper.registerKeyMapping(KeyMapping("key.dsmod_debug.next_step", GLFW.GLFW_KEY_RIGHT_BRACKET, KeyMapping.Category.MISC))
        ClientEntityEvents.ENTITY_UNLOAD.register { entity, _ -> capture?.unload(entity) }
        ClientReceiveMessageEvents.CHAT.register { message, _, sender, _, _ ->
            capture?.record("chat") {
                buildJsonObject {
                    sender?.let { put("sender", it.name) }
                    message.toCaptureJson().forEach { (key, value) -> put(key, value) }
                }
            }
        }
        ClientTickEvents.END_CLIENT_TICK.register { client ->
            capture?.tick(client)
            tickBrief(client, nextStep)
        }
        ClientCommandRegistrationCallback.EVENT.register { dispatcher, _ ->
            dispatcher.register(
                ClientCommands.literal("dscapture").executes { context ->
                    val wasRunning = capture != null
                    val file = toggleCapture(context.source.client)
                    context.source.sendFeedback(Component.literal(if (wasRunning) "Capture saved: $file" else "Capture started: $file"))
                    1
                },
            )
            dispatcher.register(
                ClientCommands.literal("dskit").executes { context ->
                    // The chat screen closes after the command returns, so open on the next tick.
                    context.source.client.schedule { context.source.client.setScreenAndShow(KitGalleryScreen()) }
                    1
                },
            )
        }
        ClientLifecycleEvents.CLIENT_STOPPING.register { capture?.close() }
    }
}
