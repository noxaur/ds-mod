package io.github.noxaur.dsmod.core.backend

import io.github.noxaur.dsmod.core.brief.Brief
import io.github.noxaur.dsmod.core.game.ServerText
import io.github.noxaur.dsmod.core.profile.Container
import io.github.noxaur.dsmod.core.profile.Profile
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import java.net.URI
import java.net.URLEncoder
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpRequest.BodyPublisher
import java.net.http.HttpRequest.BodyPublishers
import java.net.http.HttpResponse
import java.net.http.HttpResponse.BodyHandlers
import java.nio.file.Path
import java.time.Duration
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionException
import java.util.concurrent.Executors

/**
 * What a call to the backend failed on. [status] is the HTTP status, or null when the backend could not be reached or
 * something else answers at its address.
 * [tag] is the `_tag` of the error's body, which tells apart failures that share a status, and is null when the body
 * had none, as when a proxy answered.
 */
class BackendException(val status: Int?, message: String, cause: Throwable? = null, val tag: String? = null) : Exception(message, cause)

/** The error a future's stage failed with, without the [CompletionException] that wraps it. */
fun Throwable.unwrapped(): Throwable = (this as? CompletionException)?.cause ?: this

/** The Minecraft session the game already holds, which is the only proof of who the player is. */
interface Identity {
    /** The account's username. */
    val name: String

    /** Tells Mojang this account is joining the server [serverId], as the vanilla client does when it connects. */
    fun join(serverId: String)
}

@Serializable
data class Requirement(val kind: String, val value: String)

/**
 * Something an agent asked players to record. The backend may add a field here and an older mod ignores it, but the
 * [brief] is read as strictly as [Brief.read] reads one, so a step can't silently lose its timer to a key the mod
 * does not know.
 */
@Serializable
data class Request(
    val id: String,
    @Serializable(with = StrictBrief::class) val brief: Brief,
    val requirements: List<Requirement>,
    val timeoutSeconds: Int,
) {
    // The id names a file on the player's disk and a path on the backend, so it may hold nothing that leaves either.
    init {
        require(id.matches(Regex("[A-Za-z0-9-]{1,64}"))) { "A request's id is up to 64 letters, digits and dashes: $id" }
    }
}

private object StrictBrief : KSerializer<Brief> by Brief.serializer() {
    override fun deserialize(decoder: Decoder): Brief =
        Json.decodeFromJsonElement(Brief.serializer(), (decoder as JsonDecoder).decodeJsonElement())
}

/** How an upload ended, when the backend answered it. Every answer but [SENT] means sending it again would fail again. */
enum class Upload { SENT, UNKNOWN, CLOSED, TOO_BIG }

/** A line, or an item, that reached a feature and failed to parse. */
@Serializable
data class Sighting(val kind: String, val signature: String, val sample: String)

/**
 * An item from the player's own inventory, as the backend's `ItemSighting` has it. [entry] is the server's id for the
 * item, and [data] is the part of its `custom_data` that says how it is built.
 */
data class ItemSighting(
    val entry: String,
    val material: String,
    val itemModel: String?,
    val skinId: String?,
    val name: ServerText,
    val lore: List<ServerText>,
    val glint: Boolean,
    val skullTexture: String?,
    val color: Int?,
    val data: JsonObject,
)

private val json = Json { ignoreUnknownKeys = true }

/** A line of text as the contract's spans: `#RRGGBB` colors, and a flag only when it is set. */
private fun ServerText.toJson() = buildJsonArray {
    for (span in spans) {
        add(
            buildJsonObject {
                put("text", span.text)
                span.color?.let { put("color", "#%06X".format(it)) }
                if (span.bold) put("bold", true)
                if (span.italic) put("italic", true)
                if (span.underlined) put("underlined", true)
                if (span.strikethrough) put("strikethrough", true)
                if (span.obfuscated) put("obfuscated", true)
            },
        )
    }
}

/**
 * Talks to the backend as the signed-in player. Every call blocks, so a caller runs it with [async], which has one
 * thread of its own. Nothing connects until the first call. The session token lives in [token] and nowhere else: the
 * mod is open source and the backend believes nothing the mod says, so the token is never written, logged or shown.
 * A session ends after 24 hours, so a 401 signs in again once and repeats the call. [modVersion] goes with every
 * upload and report, so an agent can tell which mod recorded what.
 */
class Backend(private val url: String, private val identity: Identity, private val modVersion: String) {
    @Volatile
    private var token: String? = null

    /** Whether the next call can skip signing in, which is the slow part: Mojang must confirm the account first. */
    val signedIn get() = token != null

    // Built on the first call, on the worker, because building one may read the trust store from disk.
    // HTTP/1.1, because over plain http Java asks to upgrade to HTTP/2 in a header, and a server that does not know
    // the upgrade (Vite's, in development) drops the connection, which reads as an unreachable backend.
    private val http by lazy { HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).connectTimeout(Duration.ofSeconds(10)).build() }

    private val worker = Executors.newSingleThreadExecutor { Thread(it, "dsmod-backend").apply { isDaemon = true } }
    private val origin = URI.create(url).let { "${it.scheme}://${it.authority}" }

    /** Runs [call] on the backend's own thread, never the client thread. */
    fun <T> async(call: Backend.() -> T): CompletableFuture<T> = CompletableFuture.supplyAsync({ call() }, worker)

    /** The open requests this player has neither recorded nor skipped. One the mod cannot read is left out. */
    fun requests(): List<Request> {
        val body = send { get("/v1/contrib/requests", it) }
        return json.parseToJsonElement(body.body()).let { it as JsonArray }.mapNotNull { element ->
            runCatching { json.decodeFromJsonElement(Request.serializer(), element) }.getOrNull()
        }
    }

    /** [name]'s profile, the one they play now unless [profile] names another of theirs. */
    fun profile(name: String, profile: String?): Profile {
        require(Profile.validName(name)) { "Not a Minecraft name: $name" }
        val query = profile?.let { "?profile=" + URLEncoder.encode(it, Charsets.UTF_8) }.orEmpty()
        return Profile.parse(send { get("/v1/players/$name/profile$query", it) }.body())
    }

    /** One of a profile's containers, as it was when [snapshot] was taken. */
    fun container(snapshot: String, id: String): Container =
        Profile.parseContainer(send { get("/v1/snapshots/${segment(snapshot)}/containers/${segment(id)}", it) }.body())

    fun skip(id: String) {
        send { post("/v1/contrib/requests/$id/skip", BodyPublishers.noBody(), null, it) }
    }

    /**
     * Uploads the gzipped capture at [file] for request [id], and says whether the backend kept it or refused it for
     * good. Any other failure, sign-in's included, throws, and the recording may be sent again. The backend takes at
     * most [MAX_UPLOAD] bytes.
     */
    fun submit(id: String, file: Path): Upload {
        val query = "?modVersion=" + URLEncoder.encode(modVersion, Charsets.UTF_8)
        val response = send(REFUSED) { post("/v1/contrib/requests/$id/submissions$query", BodyPublishers.ofFile(file), "application/octet-stream", it, UPLOAD_TIMEOUT) }
        return when (response.statusCode()) {
            404 -> Upload.UNKNOWN
            409 -> Upload.CLOSED
            413 -> Upload.TOO_BIG
            else -> Upload.SENT
        }
    }

    fun unknowns(sightings: List<Sighting>) = report(
        "/v1/contrib/unknowns",
        "sightings",
        sightings.map { buildJsonObject { put("kind", it.kind); put("signature", it.signature); put("sample", it.sample) } },
    )

    fun items(items: List<ItemSighting>) = report(
        "/v1/contrib/items",
        "items",
        items.map { item ->
            buildJsonObject {
                put("entry", item.entry)
                put("material", item.material)
                item.itemModel?.let { put("itemModel", it) }
                item.skinId?.let { put("skinId", it) }
                put("name", item.name.toJson())
                put("lore", buildJsonArray { item.lore.forEach { add(it.toJson()) } })
                put("glint", item.glint)
                item.skullTexture?.let { put("skullTexture", it) }
                item.color?.let { put("color", it) }
                put("data", item.data)
            }
        },
    )

    /** Posts [entries] under [key] with the mod's version, the shape of every report the backend takes. */
    private fun report(path: String, key: String, entries: List<JsonObject>) {
        val body = buildJsonObject {
            put("modVersion", modVersion)
            putJsonArray(key) { entries.forEach { add(it) } }
        }
        send { post(path, BodyPublishers.ofString(body.toString()), "application/json", it) }
    }

    /** A code that the website trades for a session once, within a minute. */
    fun oneTimeToken(): String = field(send { get("/api/auth/one-time-token/generate", it) }.body(), "token")

    private fun get(path: String, token: String) = exchange(builder(path, token).GET().build())

    private fun post(path: String, body: BodyPublisher, type: String?, token: String, timeout: Duration = TIMEOUT) =
        exchange(builder(path, token, timeout).apply { if (type != null) header("Content-Type", type) }.POST(body).build())

    private fun exchange(request: HttpRequest): HttpResponse<String> = http.send(request, BodyHandlers.ofString())

    private fun field(body: String, key: String) = json.parseToJsonElement(body).jsonObject.getValue(key).jsonPrimitive.content

    private fun request(path: String, timeout: Duration = TIMEOUT) = HttpRequest.newBuilder(URI.create(url + path))
        .timeout(timeout)
        .header("Origin", origin)

    private fun builder(path: String, token: String, timeout: Duration = TIMEOUT) = request(path, timeout).header("Authorization", "Bearer $token")

    /**
     * Sends with the session, signs in first when there is none, and signs in again once when the backend says it
     * ended. A status in [answers] is returned as an answer the caller reads, and any other failure throws.
     */
    private fun send(answers: Set<Int> = emptySet(), call: (token: String) -> HttpResponse<String>): HttpResponse<String> {
        var response = reach { call(token ?: signIn()) }
        if (response.statusCode() == 401) {
            token = null
            response = reach { call(signIn()) }
        }
        val status = response.statusCode()
        if (status !in 200..299 && status !in answers) throw BackendException(status, "The backend answered $status.", tag = tag(response.body()))
        return response
    }

    private fun tag(body: String?): String? = runCatching {
        (json.parseToJsonElement(body.orEmpty()) as? JsonObject)?.get("_tag")?.jsonPrimitive?.content
    }.getOrNull()

    // A path segment the backend reads back whole: its ids are letters, digits, dashes, underscores and colons.
    private fun segment(value: String) = value.also { require(it.matches(Regex("[A-Za-z0-9_:-]{1,64}"))) { "Not an id: $it" } }

    private fun signIn(): String {
        val challenge = reach { exchange(auth("/api/auth/minecraft/challenge", "{}")) }
        // Every ds-mod backend has this route, so a 404 means something else holds the address, such as another
        // project's dev server on the port. Read as a status, it would blame a request the player never made.
        if (challenge.statusCode() == 404) throw BackendException(null, "Nothing at $origin answers as the ds-mod backend.")
        challenge.requireOk("a sign-in")
        val serverId = field(challenge.body(), "serverId")
        try {
            identity.join(serverId)
        } catch (e: Exception) {
            throw BackendException(null, "Mojang would not confirm this account.", e)
        }
        val verify = reach {
            exchange(auth("/api/auth/minecraft/verify", buildJsonObject { put("name", identity.name); put("serverId", serverId) }.toString()))
        }
        verify.requireOk("the account's proof")
        return field(verify.body(), "token").also { token = it }
    }

    private fun HttpResponse<String>.requireOk(of: String) {
        if (statusCode() != 200) throw BackendException(statusCode(), "The backend answered ${statusCode()} to $of.")
    }

    private fun auth(path: String, body: String) = request(path)
        .header("Content-Type", "application/json")
        .POST(BodyPublishers.ofString(body))
        .build()

    private fun <T> reach(call: () -> T): T = try {
        call()
    } catch (e: java.io.IOException) {
        throw BackendException(null, "The backend could not be reached.", e)
    }

    companion object {
        /** The largest capture the backend keeps, which answers 413 above it. */
        const val MAX_UPLOAD = 50_000_000L

        /** What the submissions route answers when it will never take this recording. */
        private val REFUSED = setOf(404, 409, 413)
        private val TIMEOUT: Duration = Duration.ofSeconds(20)
        private val UPLOAD_TIMEOUT: Duration = Duration.ofSeconds(120)
    }
}
