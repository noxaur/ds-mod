package io.github.noxaur.dsmod.edge.contribute

import io.github.noxaur.dsmod.core.backend.Backend
import io.github.noxaur.dsmod.core.backend.BackendException
import io.github.noxaur.dsmod.core.backend.Identity
import io.github.noxaur.dsmod.core.backend.Request
import io.github.noxaur.dsmod.core.capture.CaptureIncompleteException
import io.github.noxaur.dsmod.core.capture.sealCapture
import io.github.noxaur.dsmod.core.contribute.Kept
import io.github.noxaur.dsmod.core.contribute.Requirements
import io.github.noxaur.dsmod.core.contribute.Unknowns
import io.github.noxaur.dsmod.core.feature.Game
import io.github.noxaur.dsmod.core.text.clock
import io.github.noxaur.dsmod.core.text.text
import io.github.noxaur.dsmod.edge.Background
import io.github.noxaur.dsmod.edge.Browser
import io.github.noxaur.dsmod.edge.Build
import io.github.noxaur.dsmod.edge.Chat
import io.github.noxaur.dsmod.edge.DsModClient
import io.github.noxaur.dsmod.edge.capture.CaptureSession
import io.github.noxaur.dsmod.edge.capture.Recorder
import io.github.noxaur.dsmod.edge.capture.Recording
import net.fabricmc.loader.api.FabricLoader
import net.minecraft.client.Minecraft
import java.net.URLEncoder
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

/**
 * Everything a player does with ds-mod's backend: offers to record, `/dsmod login`, and the lines the mod could not read.
 * Nothing here contacts the backend until the player uses one of them or has switched the report on, and every call
 * runs on [Backend]'s own thread and answers on the client thread.
 */
object Contribute {
    private const val SETTLE_SECONDS = 4L

    private val identity = object : Identity {
        override val name: String get() = Minecraft.getInstance().user.name

        override fun join(serverId: String) {
            val client = Minecraft.getInstance()
            val user = client.user
            // The same call the game makes when it joins an online server. The access token goes to Mojang and nowhere else.
            client.services().sessionService().joinServer(user.profileId, user.accessToken, serverId)
        }
    }
    private val backend by lazy { Backend(Build.backend, identity, Build.version) }
    private val unknowns = Unknowns { DsModClient.game.tabPlayers + identity.name }
    private val hear = { feature: String, sample: String -> unknowns.see(feature, sample) }
    private val inFlight = ConcurrentHashMap.newKeySet<CompletableFuture<*>>()

    private val rejected = setOf(404, 409)

    private fun tell(build: io.github.noxaur.dsmod.core.text.TextBuilder.() -> Unit) = Minecraft.getInstance().execute { Chat.send(text(build)) }

    private fun why(error: Throwable): String {
        val e = (error as? CompletionException)?.cause ?: error
        if (e is CaptureIncompleteException) return "The recording was not fully written, so it was thrown away. Accept the request again to record it."
        if (e !is BackendException) return "Something went wrong, and nothing was sent."
        return when (e.status) {
            null -> e.message.orEmpty()
            400 -> "The backend refused it as empty or too big."
            401 -> "The backend would not sign you in."
            404 -> "The backend does not know that request."
            409 -> "You have already sent this one, or it is no longer open."
            else -> "The backend answered ${e.status}."
        }
    }

    /** Opens the offers, and fills them in when the backend answers. */
    fun open() {
        val client = Minecraft.getInstance()
        val screen = ContributeScreen()
        client.setScreenAndShow(screen)
        val areas = DsModClient.game.areas.value.names
        backend.async { requests() }.whenComplete { requests, error ->
            client.execute {
                if (error != null) screen.fail(why(error)) else screen.offer(requests.filter { Requirements.qualifies(it, areas) })
            }
        }
    }

    /** Tells the backend this player does not want [request], so that it is not offered again. */
    fun skip(request: Request) {
        backend.async { skip(request.id) }.whenComplete { _, error -> if (error != null) tell { bad("The backend could not hide it: " + why(error)) } }
    }

    /** Starts the brief of [request], which the player works through with the HUD. */
    fun accept(request: Request) {
        if (Recording.busy || Recorder.running) {
            tell { bad("Finish or cancel the recording you have first.") }
            return
        }
        Recording.start(request.brief, request.id, request.timeoutSeconds)
        tell {
            body("Press "); value(Recording.key()); body(" to start recording \"${request.brief.name}\", and again after each step. ")
            body("You have "); value(clock(request.timeoutSeconds * 1000L)); body(". "); action("/dsmod cancel"); body(" stops without sending.")
        }
    }

    /** The folder of redacted recordings that are waiting to be sent, and the one a run records into. */
    fun dir(): Path = FabricLoader.getInstance().gameDir.resolve("dsmod/contributions")

    private fun track(work: CompletableFuture<*>) {
        inFlight += work
        work.whenComplete { _, _ -> inFlight -= work }
    }

    /**
     * Says how an upload of [gz] went. A recording the backend no longer wants is deleted, and any other failure
     * keeps it for `/dsmod submit`. Runs off the client thread.
     */
    private fun uploaded(gz: Path, error: Throwable?) {
        val failure = (error as? CompletionException)?.cause ?: error
        when {
            error == null -> tell { good("Sent. Thank you!") }
            failure is BackendException && failure.status in rejected -> {
                Files.deleteIfExists(gz)
                tell { bad("Not sent. That request is no longer open, so the recording was deleted.") }
            }
            else -> {
                val kept = Files.exists(gz)
                tell {
                    bad("Not sent. " + why(error))
                    if (kept) { body(" Your recording is kept. "); action("/dsmod submit"); body(" tries again.") }
                }
            }
        }
    }

    private fun upload(requestId: String, gz: Path): CompletableFuture<Boolean> =
        backend.async { submit(requestId, gz); Files.deleteIfExists(gz) }.whenComplete { _, error -> uploaded(gz, error) }

    /**
     * Redacts the finished capture into [Kept]'s folder, uploads it, and says how it went. [session] is closed first,
     * off the client thread, and a capture its writer did not finish is not sent.
     */
    fun send(requestId: String, session: CaptureSession, raw: Path) {
        val gz = raw.resolveSibling(Kept.name(requestId, raw.fileName.toString()))
        val own = DsModClient.game.tabPlayers + identity.name
        tell { body("Sending your recording.") }
        val sealed = CompletableFuture.supplyAsync({ sealCapture(session, raw, gz, own) }, Background.io)
        sealed.whenComplete { _, error -> if (error != null) tell { bad("Not sent. " + why(error)) } }
        track(sealed.thenCompose { upload(requestId, gz) })
    }

    /** Sends the recordings a failed upload kept, for `/dsmod submit` when no run is on. */
    fun resend() {
        track(
            CompletableFuture.supplyAsync({ Kept.waiting(dir()) }, Background.io).thenAccept { files ->
                if (files.isEmpty()) {
                    tell { body("Nothing is waiting to be sent. Accept a request in Contribute to record one.") }
                    return@thenAccept
                }
                tell { body("Sending ${files.size} kept recording" + if (files.size == 1) "." else "s.") }
                files.forEach { file -> Kept.requestId(file)?.let { track(upload(it, file)) } }
            },
        )
    }

    /** Deletes what a run that was cut short left, which holds names and chat. Call it off the client thread, at start. */
    fun sweep() = Kept.sweep(dir())

    /** Gives a redaction or an upload that is already going a few seconds to finish, as the game closes. */
    fun settle() {
        val end = System.nanoTime() + TimeUnit.SECONDS.toNanos(SETTLE_SECONDS)
        try {
            // The io thread is one queue, so a task behind the redactions and closes in it is done when they are.
            CompletableFuture.supplyAsync({}, Background.io).get(SETTLE_SECONDS, TimeUnit.SECONDS)
            CompletableFuture.allOf(*inFlight.toTypedArray()).get(maxOf(end - System.nanoTime(), 0), TimeUnit.NANOSECONDS)
        } catch (_: Exception) {
            // Out of time or failed. A recording that did not go stays for `/dsmod submit`, and a raw one is swept at the next start.
        }
    }

    /** Trades the session for a link that signs the website in once, and opens it. The token never reaches chat or a log. */
    fun login() {
        tell { body("Signing in to the website.") }
        backend.async { oneTimeToken() }.thenApplyAsync({ token ->
            Browser.open(Build.website + "/login#" + URLEncoder.encode(token, Charsets.UTF_8))
        }, Background.io).whenComplete { opened, error ->
            when {
                error != null -> tell { bad("Could not sign in. " + why(error)) }
                opened == false -> tell { bad("Could not open your browser. Open ${Build.website} yourself.") }
                else -> tell { good("Your browser is open on your ds-mod account.") }
            }
        }
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
        backend.async { unknowns(sightings) }.whenComplete { _, error ->
            Minecraft.getInstance().execute {
                unknowns.finished(sightings, delivered = error == null)
                if (error != null) game.metrics.fail("contribute.unknowns", RuntimeException(why(error)))
            }
        }
    }
}
