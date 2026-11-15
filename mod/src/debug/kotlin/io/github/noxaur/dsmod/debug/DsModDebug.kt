package io.github.noxaur.dsmod.debug

import io.github.noxaur.dsmod.core.brief.Brief
import io.github.noxaur.dsmod.edge.DsModClient
import io.github.noxaur.dsmod.edge.SettingsScreen
import io.github.noxaur.dsmod.edge.capture.CaptureSession
import io.github.noxaur.dsmod.edge.capture.Recorder
import io.github.noxaur.dsmod.edge.capture.Recording
import net.fabricmc.api.ClientModInitializer
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback
import net.fabricmc.fabric.api.client.command.v2.ClientCommands
import net.minecraft.client.Minecraft
import net.minecraft.network.chat.Component
import java.nio.file.Path

/**
 * The developer's tools: `/dscapture` records without a brief, `/dskit` shows the kit and `/dskit rows` its settings
 * rows, `/dssettings` opens the settings screen being built, `-Pbrief` starts a brief at launch, and the title screen
 * has a button that joins DragSim.
 */
object DsModDebug : ClientModInitializer {
    /** The capture `/dscapture` started, while it runs. */
    private var capture: CaptureSession? = null

    /** Read by BriefTitleMixin for the window title. */
    fun briefName(): String? = Recording.name()

    /**
     * Starts a capture, or ends the one it started, and returns the file. The file is finished off the client
     * thread, and [say] hears on the client thread how each went. While a brief is on, its capture is the brief's
     * to start and end, so this refuses and returns null.
     */
    fun toggleCapture(client: Minecraft, say: (String) -> Unit = {}): Path? {
        capture?.let { own ->
            capture = null
            Recorder.stop(own) { whole -> client.execute { say(if (whole) "Capture saved: ${own.file}" else "Capture not fully written: ${own.file}") } }
            return own.file
        }
        if (Recorder.running || Recording.busy) {
            say("A brief is on. Finish it or type /dsmod cancel, then start a capture.")
            return null
        }
        return Recorder.start(client.gameDirectory.toPath().resolve("dsmod/captures")).also { capture = it }.file.also { say("Capture started: $it") }
    }

    override fun onInitializeClient() {
        // A developer tests in singleplayer and on servers of their own, so the debug build runs everywhere.
        DsModClient.anyServer = true
        JoinDragSim.register()
        // `./gradlew runClient -Pbrief=<file>` sets this. The window does not exist yet, and takes its title when it opens.
        System.getProperty("dsmod.brief")?.let { Recording.startAtLaunch(Brief.read(Path.of(it))) }
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
                        // The chat screen closes after the command returns, so open on the next tick.
                        context.source.client.schedule { context.source.client.setScreenAndShow(KitGalleryScreen()) }
                        1
                    }
                    .then(
                        ClientCommands.literal("rows").executes { context ->
                            context.source.client.schedule { context.source.client.setScreenAndShow(SettingsGalleryScreen()) }
                            1
                        },
                    ),
            )
            dispatcher.register(
                ClientCommands.literal("dssettings").executes { context ->
                    // ds-mod's own settings screen, which replaces MoulConfig's once its rows have controls.
                    context.source.client.schedule { context.source.client.setScreenAndShow(SettingsScreen(null)) }
                    1
                },
            )
        }
    }
}
