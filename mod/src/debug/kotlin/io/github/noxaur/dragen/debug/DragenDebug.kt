package io.github.noxaur.dragen.debug

import io.github.noxaur.dragen.edge.Hooks
import io.github.noxaur.dragen.core.brief.Brief
import io.github.noxaur.dragen.edge.capture.CaptureSession
import io.github.noxaur.dragen.edge.capture.Recorder
import io.github.noxaur.dragen.edge.capture.Recording
import net.fabricmc.api.ClientModInitializer
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback
import net.fabricmc.fabric.api.client.command.v2.ClientCommands
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.screens.Screen
import net.minecraft.network.chat.Component
import java.nio.file.Path

object DragenDebug : ClientModInitializer {
    private var capture: CaptureSession? = null

    fun briefName(): String? = Recording.name()

    fun toggleCapture(client: Minecraft, say: (String) -> Unit = {}): Path? {
        capture?.let { own ->
            capture = null
            Recorder.stop(own) { whole -> client.execute { say(if (whole) "Capture saved: ${own.file}" else "Capture not fully written: ${own.file}") } }
            return own.file
        }
        if (Recorder.running || Recording.busy) {
            say("A brief is on. Finish it or type /dragen cancel, then start a capture.")
            return null
        }
        return Recorder.start(client.gameDirectory.toPath().resolve("dragen/captures")).also { capture = it }.file.also { say("Capture started: $it") }
    }

    // @keep The chat screen closes after the command returns, so open on the next tick.
    private fun open(client: Minecraft, screen: () -> Screen): Int {
        client.schedule { client.setScreenAndShow(screen()) }
        return 1
    }

    override fun onInitializeClient() {
        JoinDragSim.register()
        // @keep The window does not exist yet, and takes its title when it opens.
        System.getProperty("dragen.brief")?.let { Recording.startAtLaunch(Brief.read(Path.of(it))) }
        ClientCommandRegistrationCallback.EVENT.register { dispatcher, _ ->
            dispatcher.register(
                ClientCommands.literal("dscapture")
                    .executes { context ->
                        toggleCapture(context.source.client) { context.source.sendFeedback(Component.literal(it)) }
                        1
                    },
            )
            dispatcher.register(
                ClientCommands.literal("dskit")
                    .executes { context ->
                        open(context.source.client) { KitGalleryScreen() }
                    }
                    .apply {
                        for (page in SettingsGalleryScreen.Page.entries) {
                            then(
                                ClientCommands.literal(page.name.lowercase()).executes { context ->
                                    open(context.source.client) { SettingsGalleryScreen(Hooks.hud, page) }
                                },
                            )
                        }
                    },
            )
        }
    }
}
