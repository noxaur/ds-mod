package io.github.noxaur.dsmod.debug

import io.github.noxaur.dsmod.core.brief.Brief
import io.github.noxaur.dsmod.core.capture.CaptureIncompleteException
import io.github.noxaur.dsmod.edge.Background
import io.github.noxaur.dsmod.edge.DsModClient
import io.github.noxaur.dsmod.edge.capture.Recorder
import io.github.noxaur.dsmod.edge.capture.Recording
import net.fabricmc.api.ClientModInitializer
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback
import net.fabricmc.fabric.api.client.command.v2.ClientCommands
import net.minecraft.client.Minecraft
import net.minecraft.network.chat.Component
import java.nio.file.Path

/** The developer's tools: `/dscapture` records without a brief, `/dskit` shows the kit, and `-Pbrief` starts a brief at launch. */
object DsModDebug : ClientModInitializer {
    /** The capture `/dscapture` started, while it runs. */
    private var file: Path? = null

    /** Read by BriefTitleMixin for the window title. */
    fun briefName(): String? = Recording.name()

    /**
     * Starts a capture, or ends the one it started, and returns the file. The file is finished off the client
     * thread, and [say] hears on the client thread how each went. A recording that a brief or a contribution
     * started is theirs to end, so this refuses and returns null.
     */
    fun toggleCapture(client: Minecraft, say: (String) -> Unit = {}): Path? {
        val own = file
        if (own == null) {
            if (Recorder.running) {
                say("A brief is recording. Finish it or type /dsmod cancel, then start a capture.")
                return null
            }
            return Recorder.start(client.gameDirectory.toPath().resolve("dsmod/captures")).also {
                file = it
                say("Capture started: $it")
            }
        }
        file = null
        // A brief that started during this capture recorded into it, and ended it with its last step.
        val session = Recorder.stop() ?: return own.also { say("Capture saved: $own") }
        Background.io.execute {
            val said = try {
                session.close()
                "Capture saved: $own"
            } catch (_: CaptureIncompleteException) {
                "Capture not fully written: $own"
            }
            client.execute { say(said) }
        }
        return own
    }

    override fun onInitializeClient() {
        // A developer tests in singleplayer and on servers of their own, so the debug build runs everywhere.
        DsModClient.anyServer = true
        // `./gradlew runClient -Pbrief=<file>` sets this. The window does not exist yet, and takes its title when it opens.
        System.getProperty("dsmod.brief")?.let { Recording.startAtLaunch(Brief.read(Path.of(it))) }
        ClientCommandRegistrationCallback.EVENT.register { dispatcher, _ ->
            dispatcher.register(
                ClientCommands.literal("dscapture").executes { context ->
                    toggleCapture(context.source.client) { context.source.sendFeedback(Component.literal(it)) }
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
    }
}
