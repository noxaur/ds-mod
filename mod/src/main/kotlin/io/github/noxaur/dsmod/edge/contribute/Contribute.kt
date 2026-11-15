package io.github.noxaur.dsmod.edge.contribute

import io.github.noxaur.dsmod.core.backend.Backend
import io.github.noxaur.dsmod.core.backend.BackendException
import io.github.noxaur.dsmod.core.backend.Request
import io.github.noxaur.dsmod.core.capture.redactCapture
import io.github.noxaur.dsmod.core.contribute.Kept
import io.github.noxaur.dsmod.core.contribute.Requirements
import io.github.noxaur.dsmod.core.contribute.Unknowns
import io.github.noxaur.dsmod.core.feature.Game
import io.github.noxaur.dsmod.core.text.TextBuilder
import io.github.noxaur.dsmod.core.text.clock
import io.github.noxaur.dsmod.core.text.text
import io.github.noxaur.dsmod.edge.Account
import io.github.noxaur.dsmod.edge.Background
import io.github.noxaur.dsmod.edge.Chat
import io.github.noxaur.dsmod.edge.DsModClient
import io.github.noxaur.dsmod.edge.capture.Recorder
import io.github.noxaur.dsmod.edge.capture.Recording
import net.fabricmc.loader.api.FabricLoader
import net.minecraft.client.Minecraft
import org.slf4j.LoggerFactory
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import kotlin.io.path.fileSize
import kotlin.io.path.name

/**
 * What a player does for ds-mod's developers: record the requests they qualify for, and, when they switch it on,
 * report the lines the mod could not read. Nothing here contacts the backend until the player does one of these.
 * Network calls run on [Account.backend]'s thread, disk work on [Background.io], and what the player is told is
 * sent from the client thread. `specs/Contribute.tla` is the life of a run, and the code follows its names.
 */
object Contribute {
    private const val QUIT_SECONDS = 4L
    private val log = LoggerFactory.getLogger("dsmod")

    /** Statuses that say the backend will never take this recording, so keeping it would only fail again. */
    private val refused = setOf(400, 404, 409, 413)

    /** Kept files an upload holds, so a resend leaves them alone. */
    private val claimed = ConcurrentHashMap.newKeySet<Path>()
    private val uploads = ConcurrentHashMap.newKeySet<CompletableFuture<*>>()

    /** The startup sweep. Offers are fetched only after it, so it never deletes a capture a run is writing. */
    private lateinit var swept: CompletableFuture<Unit>

    private val unknowns = Unknowns { DsModClient.game.tabPlayers + Account.name }
    private val hear = { feature: String, sample: String -> unknowns.see(feature, sample) }

    private fun tell(build: TextBuilder.() -> Unit) = text(build).let { line -> Minecraft.getInstance().execute { Chat.send(line) } }

    /** The folder a request's run records into, and where a redacted recording waits to be sent. */
    fun dir(): Path = FabricLoader.getInstance().gameDir.resolve("dsmod/contributions")

    fun register() {
        // What a run left when the game stopped holds names and chat, and is never sent.
        swept = CompletableFuture.supplyAsync({ Kept.sweep(dir()) }, Background.io)
            .exceptionally { log.error("Could not clear what an earlier recording left in {}", dir(), it) }
    }

    /** Opens the screen of offers, and fills it in when the backend answers. */
    fun open() {
        val client = Minecraft.getInstance()
        val screen = ContributeScreen(client.gui.screen(), signingIn = !Account.backend.signedIn)
        client.setScreenAndShow(screen)
        val places = DsModClient.game.places.value
        swept.thenCompose { Account.backend.async { requests() } }.whenComplete { requests, error ->
            client.execute { if (error != null) screen.fail(Account.why(error)) else screen.offer(requests.filter { Requirements.qualifies(it, places) }) }
        }
    }

    /** Tells the backend this player does not want [request], so that it is not offered to them again. */
    fun skip(request: Request) {
        Account.backend.async { skip(request.id) }.whenComplete { _, error -> if (error != null) tell { bad("Could not hide that request. " + Account.why(error)) } }
    }

    /** Starts the brief of [request], which the player works through with the HUD. False when a run is already on. */
    fun accept(request: Request): Boolean {
        if (Recording.busy || Recorder.running) {
            Chat.send(text { bad("Finish the recording you have first, or type "); action("/dsmod cancel"); bad(".") })
            return false
        }
        Recording.contribute(request)
        Chat.send(
            text {
                body("Press "); value(Recording.key()); body(" to start recording, and again after each step. You have ")
                value(clock(request.timeoutSeconds * 1000L)); body(". "); action("/dsmod cancel"); body(" stops without sending.")
            },
        )
        return true
    }

    /** `/dsmod submit`: ends the run and sends it, or sends what earlier uploads kept. */
    fun submit() {
        when {
            Recording.submit() -> {}
            Recording.busy -> Chat.send(text { bad("Nothing is recorded yet. Press "); value(Recording.key()); bad(" to start recording.") })
            else -> resend()
        }
    }

    /** `/dsmod cancel`. */
    fun cancel() {
        Chat.send(if (Recording.cancel()) text { body("Recording cancelled. Nothing was sent.") } else text { bad("No recording to cancel.") })
    }

    /**
     * What to do with [raw] once the recorder has finished it, for a run of [request]. Call it on the client thread
     * and hand the result to `Recorder.stop`, which calls it on the io thread: a whole capture is redacted into a
     * `.part`, claimed, renamed into place and uploaded, and the raw file is deleted either way.
     */
    fun seal(request: Request, raw: Path): (whole: Boolean) -> Unit {
        val own = setOf(Account.name)
        val gz = raw.resolveSibling(Kept.name(request.id, raw.name))
        val part = raw.resolveSibling(gz.name + ".part")
        return seal@{ whole ->
            try {
                if (!whole) return@seal tell { bad("The recording was not fully written, so it was thrown away. Accept the request again to record it.") }
                redactCapture(raw, part, own)
                if (part.fileSize() > Backend.MAX_UPLOAD) {
                    Files.delete(part)
                    return@seal tell { bad("The recording is too long to send, so it was thrown away. Finish sooner next time.") }
                }
                claimed.add(gz)
                Files.move(part, gz, StandardCopyOption.ATOMIC_MOVE)
                tell { body("Sending your recording.") }
                upload(request.id, gz)
            } catch (e: Exception) {
                log.error("Could not redact the recording {}", raw, e)
                claimed.remove(gz)
                Files.deleteIfExists(part)
                tell { bad("The recording could not be prepared, so nothing was sent.") }
            } finally {
                Files.deleteIfExists(raw)
            }
        }
    }

    /** Sends a claimed [gz] and says how it went. A recording the backend refuses for good is deleted, and any other failure keeps it. */
    private fun upload(requestId: String, gz: Path) {
        val upload = Account.backend.async { submit(requestId, gz) }.whenComplete { _, error ->
            val failure = (error as? CompletionException)?.cause ?: error
            try {
                when {
                    error == null -> Files.deleteIfExists(gz).also { tell { good("Sent. Thank you!") } }
                    failure is BackendException && failure.status in refused ->
                        Files.deleteIfExists(gz).also { tell { bad("Not sent, and deleted. " + Account.why(failure)) } }
                    else -> tell { bad("Not sent. " + Account.why(failure!!) + " Your recording is kept. "); action("/dsmod submit"); bad(" tries again.") }
                }
            } finally {
                claimed.remove(gz)
            }
        }
        uploads.add(upload)
        upload.whenComplete { _, _ -> uploads.remove(upload) }
    }

    /** Sends the recordings earlier uploads kept, except one an upload already holds. */
    private fun resend() {
        Account.backend.async { Kept.waiting(dir()).filter { claimed.add(it) } }.whenComplete { files, error ->
            when {
                error != null -> tell { bad("Could not read the kept recordings.") }
                files.isEmpty() -> tell { body("Nothing is waiting to be sent. Open Contribute in the settings to record a request.") }
                else -> {
                    tell { body("Sending ${files.size} kept recording" + if (files.size == 1) "." else "s.") }
                    files.forEach { upload(Kept.requestId(it)!!, it) }
                }
            }
        }
    }

    /** Quitting, once the io thread is done: gives an upload in progress a few seconds. One that does not finish is kept for a resend. */
    fun quit() {
        runCatching { CompletableFuture.allOf(*uploads.toTypedArray()).get(QUIT_SECONDS, TimeUnit.SECONDS) }
    }

    /** Called every tick. Listens for lines the mod could not read while the player allows it, and sends them rarely. */
    fun tick(game: Game, reportUnknowns: Boolean, now: Long) {
        if ((game.metrics.onUnparsed != null) != reportUnknowns) game.metrics.onUnparsed = if (reportUnknowns) hear else null
        if (!unknowns.waiting) return
        if (!reportUnknowns) {
            unknowns.drop()
            return
        }
        if (!unknowns.due(now)) return
        val sightings = unknowns.take()
        Account.backend.async { unknowns(sightings) }.whenComplete { _, error ->
            Minecraft.getInstance().execute {
                unknowns.finished(sightings, delivered = error == null)
                if (error != null) game.metrics.fail("contribute.unknowns", RuntimeException(Account.why(error)))
            }
        }
    }
}
