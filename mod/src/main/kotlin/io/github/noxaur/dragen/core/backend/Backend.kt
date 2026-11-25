package io.github.noxaur.dragen.core.backend

import io.github.noxaur.dragen.core.brief.Brief
import io.github.noxaur.dragen.core.game.ServerText
import io.github.noxaur.dragen.core.profile.Container
import io.github.noxaur.dragen.core.profile.Profile
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

class BackendException(val status: Int?, message: String, cause: Throwable? = null, val tag: String? = null) : Exception(message, cause)

fun Throwable.unwrapped(): Throwable = (this as? CompletionException)?.cause ?: this

interface Identity {
    val name: String

    fun join(serverId: String)
}

@Serializable
data class Requirement(val kind: String, val value: String)

@Serializable
data class Request(
    val id: String,
    @Serializable(with = StrictBrief::class) val brief: Brief,
    val requirements: List<Requirement>,
    val timeoutSeconds: Int,
) {
    /** @keep The id names a file on the player's disk and a path on the backend, so it may hold nothing that leaves either. */
    init {
        require(id.matches(Regex("[A-Za-z0-9-]{1,64}"))) { "A request's id is up to 64 letters, digits and dashes: $id" }
    }
}

private object StrictBrief : KSerializer<Brief> by Brief.serializer() {
    override fun deserialize(decoder: Decoder): Brief =
        Json.decodeFromJsonElement(Brief.serializer(), (decoder as JsonDecoder).decodeJsonElement())
}

enum class Upload { SENT, UNKNOWN, CLOSED, TOO_BIG }

@Serializable
data class Sighting(val kind: String, val signature: String, val sample: String)

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

fun ItemSighting.toJson() = buildJsonObject {
    put("entry", entry)
    put("material", material)
    itemModel?.let { put("itemModel", it) }
    skinId?.let { put("skinId", it) }
    put("name", name.toJson())
    put("lore", buildJsonArray { lore.forEach { add(it.toJson()) } })
    put("glint", glint)
    skullTexture?.let { put("skullTexture", it) }
    color?.let { put("color", it) }
    put("data", data)
}

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
                span.font?.let { put("font", it) }
            },
        )
    }
}

/** @keep The mod is open source and the backend believes nothing the mod says, so the session token lives in `token` and is never written, logged or shown. */
class Backend(private val url: String, private val identity: Identity, private val modVersion: String) {
    @Volatile
    private var token: String? = null

    val signedIn get() = token != null

    /** @keep Built on the first call, on the worker, because building one may read the trust store from disk. HTTP/1.1, because over plain http Java sends an HTTP/2 upgrade header, and Vite's dev server drops the connection on it. */
    private val http by lazy { HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).connectTimeout(Duration.ofSeconds(10)).build() }

    private val worker = Executors.newSingleThreadExecutor { Thread(it, "dragen-backend").apply { isDaemon = true } }
    private val origin = URI.create(url).let { "${it.scheme}://${it.authority}" }

    fun <T> async(call: Backend.() -> T): CompletableFuture<T> = CompletableFuture.supplyAsync({ call() }, worker)

    fun requests(): List<Request> {
        val body = send { get("/v1/contrib/requests", it) }
        return json.parseToJsonElement(body.body()).let { it as JsonArray }.mapNotNull { element ->
            runCatching { json.decodeFromJsonElement(Request.serializer(), element) }.getOrNull()
        }
    }

    fun profile(name: String, profile: String?): Profile {
        require(Profile.validName(name)) { "Not a Minecraft name: $name" }
        val query = profile?.let { "?profile=" + URLEncoder.encode(it, Charsets.UTF_8) }.orEmpty()
        return Profile.parse(send { get("/v1/players/$name/profile$query", it) }.body())
    }

    fun container(snapshot: String, id: String): Container =
        Profile.parseContainer(send { get("/v1/snapshots/${segment(snapshot)}/containers/${segment(id)}", it) }.body())

    fun containers(snapshot: String): List<Container> =
        Profile.parseContainers(send { get("/v1/snapshots/${segment(snapshot)}/containers", it) }.body())

    fun skip(id: String) {
        send { post("/v1/contrib/requests/$id/skip", BodyPublishers.noBody(), null, it) }
    }

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

    fun items(items: List<ItemSighting>) = report("/v1/contrib/items", "items", items.map { it.toJson() })

    private fun report(path: String, key: String, entries: List<JsonObject>) {
        val body = buildJsonObject {
            put("modVersion", modVersion)
            putJsonArray(key) { entries.forEach { add(it) } }
        }
        send { post(path, BodyPublishers.ofString(body.toString()), "application/json", it) }
    }

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

    private fun segment(value: String) = value.also { require(it.matches(Regex("[A-Za-z0-9_:-]{1,64}"))) { "Not an id: $it" } }

    private fun signIn(): String {
        val challenge = reach { exchange(auth("/api/auth/minecraft/challenge", "{}")) }
        if (challenge.statusCode() == 404) throw BackendException(null, "Nothing at $origin answers as the Dragen backend.")
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
        if (verify.statusCode() == 503) throw BackendException(503, MOJANG_DOWN)
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
        const val MAX_UPLOAD = 50_000_000L

        const val MOJANG_DOWN = "The backend could not ask Mojang."

        private val REFUSED = setOf(404, 409, 413)
        private val TIMEOUT: Duration = Duration.ofSeconds(20)
        private val UPLOAD_TIMEOUT: Duration = Duration.ofSeconds(120)
    }
}
