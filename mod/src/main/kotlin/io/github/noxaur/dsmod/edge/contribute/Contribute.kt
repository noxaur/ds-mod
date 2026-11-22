package io.github.noxaur.dsmod.edge.contribute

import io.github.noxaur.dsmod.core.feature.contribute.ReportGameData
import io.github.noxaur.dsmod.core.backend.Backend
import io.github.noxaur.dsmod.core.backend.Request
import io.github.noxaur.dsmod.core.backend.Upload
import io.github.noxaur.dsmod.core.capture.redactCapture
import io.github.noxaur.dsmod.core.contribute.Batch
import io.github.noxaur.dsmod.core.contribute.ItemSightings
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
import io.github.noxaur.dsmod.edge.capture.Recorder
import io.github.noxaur.dsmod.edge.capture.Recording
import net.fabricmc.loader.api.FabricLoader
import net.minecraft.client.Minecraft
import org.slf4j.LoggerFactory
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import kotlin.io.path.fileSize
import kotlin.io.path.name

/** @keep Network calls run on [Account.backend]'s thread, disk work on [Background.io], and chat lines go out from the client thread. `specs/Contribute.tla` is the life of a run, and the code follows its names. */
object Contribute {
    private const val QUIT_SECONDS = 4L
    private val log = LoggerFactory.getLogger("dsmod")

    private val claimed = ConcurrentHashMap.newKeySet<Path>()
    private val uploads = ConcurrentHashMap.newKeySet<CompletableFuture<*>>()

    /** @keep Offers are fetched only after the startup sweep, so it never deletes a capture a run is writing. */
    private lateinit var swept: CompletableFuture<Unit>

    private lateinit var game: Game
    private lateinit var reportGameData: ReportGameData

    fun bind(game: Game, reportGameData: ReportGameData) {
        this.game = game
        this.reportGameData = reportGameData
    }

    private val names = { game.tabPlayers + Account.name }
    private val unknowns = Unknowns(names)
    private val items = ItemSightings(names)
    private val sights = InventorySights(items)
    private val hear = { feature: String, sample: String -> unknowns.see(feature, sample) }

    private fun tell(build: TextBuilder.() -> Unit) = text(build).let { line -> Minecraft.getInstance().execute { Chat.send(line) } }

    fun dir(): Path = FabricLoader.getInstance().gameDir.resolve("dsmod/contributions")

    fun register() {
        // @keep What a run left when the game stopped holds names and chat, and is never sent.
        swept = CompletableFuture.supplyAsync({ Kept.sweep(dir()) }, Background.io)
            .exceptionally { log.error("Could not clear what an earlier recording left in {}", dir(), it) }
    }

    fun open() {
        val client = Minecraft.getInstance()
        val screen = ContributeScreen(signingIn = !Account.backend.signedIn, parent = client.gui.screen())
        client.setScreenAndShow(screen)
        val places = game.places.value
        swept.thenCompose { Account.backend.async { requests() } }.whenComplete { requests, error ->
            client.execute { if (error != null) screen.fail(Account.why(error)) else screen.offer(requests.filter { Requirements.qualifies(it, places) }) }
        }
    }

    fun skip(request: Request) {
        Account.backend.async { skip(request.id) }.whenComplete { _, error -> if (error != null) tell { bad("Could not hide that request. " + Account.why(error)) } }
    }

    fun accept(request: Request): Boolean {
        if (Recording.busy || Recorder.running) {
            Chat.send(text { bad("Finish the recording you have first, or type "); action("/dsmod cancel"); bad(".") })
            return false
        }
        Recording.contribute(request)
        Chat.send(
            text {
                body("Press "); value(Recording.key()); body(" to start recording, and again after each step. You have ")
                body(clock(request.timeoutSeconds * 1000L) + ". "); action("/dsmod cancel"); body(" stops without sending.")
            },
        )
        return true
    }

    fun submit() {
        when {
            Recording.submit() -> {}
            Recording.busy -> Chat.send(text { bad("Nothing is recorded yet. Press "); value(Recording.key()); bad(" to start recording.") })
            else -> resend()
        }
    }

    fun cancel() {
        Chat.send(if (Recording.cancel()) text { body("Recording cancelled. Nothing was sent.") } else text { bad("No recording to cancel.") })
    }

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
                tell { bad("The recording could not be prepared, so nothing was sent. Open Contribute in the settings to record it again.") }
            } finally {
                Files.deleteIfExists(raw)
            }
        }
    }

    /** @keep A sent run is told before the delete, so a delete that throws cannot hide that it went out, and one that throws keeps the claim, so this start does not upload the file again. */
    internal fun settle(answer: Upload, gz: Path, tell: (Upload) -> Unit): Boolean {
        if (answer == Upload.SENT) tell(answer)
        val deleted = try {
            Files.deleteIfExists(gz)
            true
        } catch (e: Exception) {
            log.error("Could not delete the recording {} after the backend answered {}", gz, answer, e)
            false
        }
        if (answer != Upload.SENT) tell(answer)
        return deleted
    }

    /** @keep The answer is handled on the backend's thread, as the upload is, so a resend queued behind it sees the file and its claim as this upload left them. */
    private fun upload(requestId: String, gz: Path) {
        val upload = Account.backend.async {
            var release = true
            try {
                val answer = try {
                    submit(requestId, gz)
                } catch (e: Exception) {
                    return@async tell { bad("Not sent. " + Account.why(e) + " Your recording is kept. "); action("/dsmod submit"); bad(" tries again.") }
                }
                release = settle(answer, gz) { told ->
                    when (told) {
                        Upload.SENT -> tell { good("Sent. Thank you!") }
                        Upload.UNKNOWN -> tell { bad("Not sent, and deleted. The backend no longer has that request.") }
                        Upload.CLOSED -> tell { bad("Not sent, and deleted. That request is closed, has enough recordings, or already has yours.") }
                        Upload.TOO_BIG -> tell { bad("Not sent, and deleted. The recording is too big for the backend.") }
                    }
                }
            } catch (e: Exception) {
                log.error("Could not delete the recording {} after its upload", gz, e)
            } finally {
                if (release) claimed.remove(gz)
            }
        }
        uploads.add(upload)
        upload.whenComplete { _, _ -> uploads.remove(upload) }
    }

    private fun resend() {
        Account.backend.async { Kept.waiting(dir()).filter { claimed.add(it) } }.whenComplete { files, error ->
            when {
                error != null -> tell { bad("Could not read the kept recordings.") }
                files.isEmpty() -> tell { body("Nothing is waiting to be sent. Open Contribute in the settings to record a request.") }
                else -> {
                    tell { body("Sending ${files.size} kept recording" + if (files.size == 1) "." else "s.") }
                    files.forEach { file -> upload(checkNotNull(Kept.requestId(file)) { "a kept recording is named for its request" }, file) }
                }
            }
        }
    }

    fun quit() {
        runCatching { CompletableFuture.allOf(*uploads.toTypedArray()).get(QUIT_SECONDS, TimeUnit.SECONDS) }
    }

    fun tick(now: Long) {
        val report = reportGameData.feature.runs
        if ((game.metrics.onUnparsed != null) != report) game.metrics.onUnparsed = if (report) hear else null
        if (report) game.metrics.time("item-sights") { sights.poll(Minecraft.getInstance()) } else sights.reset()
        flush(game, unknowns.batch, report, now, "contribute.unknowns") { unknowns(it) }
        flush(game, items.batch, report, now, "contribute.items") { items(it) }
    }

    private fun <T : Any> flush(game: Game, batch: Batch<T>, report: Boolean, now: Long, name: String, send: Backend.(List<T>) -> Unit) {
        if (!batch.waiting) return
        if (!report) {
            batch.drop()
            return
        }
        if (!batch.due(now)) return
        val taken = batch.take()
        Account.backend.async { send(taken) }.whenComplete { _, error ->
            Minecraft.getInstance().execute {
                batch.finished(taken, error)
                if (error != null) game.metrics.fail(name, RuntimeException(Account.why(error)))
            }
        }
    }
}
