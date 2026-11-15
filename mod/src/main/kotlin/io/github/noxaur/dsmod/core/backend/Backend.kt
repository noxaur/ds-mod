package io.github.noxaur.dsmod.core.backend

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpRequest.BodyPublishers
import java.net.http.HttpResponse
import java.net.http.HttpResponse.BodyHandlers
import java.time.Duration
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Executors

/** What a call to the backend failed on. [status] is the HTTP status, or null when the backend could not be reached. */
class BackendException(val status: Int?, message: String, cause: Throwable? = null) : Exception(message, cause)

/** The Minecraft session the game already holds, which is the only proof of who the player is. */
interface Identity {
    /** The account's username. */
    val name: String

    /** Tells Mojang this account is joining the server [serverId], as the vanilla client does when it connects. */
    fun join(serverId: String)
}

private val json = Json { ignoreUnknownKeys = true }

/**
 * Talks to the backend as the signed-in player. Every call blocks, so a caller runs it with [async], which has one
 * thread of its own. Nothing connects until the first call. The session token lives in [token] and nowhere else: the
 * mod is open source and the backend believes nothing the mod says, so the token is never written, logged or shown.
 * A session ends after 24 hours, so a 401 signs in again once and repeats the call.
 */
class Backend(private val url: String, private val identity: Identity) {
    @Volatile
    private var token: String? = null

    // Built on the first call, on the worker, because building one may read the trust store from disk.
    // HTTP/1.1, because over plain http Java asks to upgrade to HTTP/2 in a header, and a server that does not know
    // the upgrade (Vite's, in development) drops the connection, which reads as an unreachable backend.
    private val http by lazy { HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).connectTimeout(Duration.ofSeconds(10)).build() }

    private val worker = Executors.newSingleThreadExecutor { Thread(it, "dsmod-backend").apply { isDaemon = true } }
    private val origin = URI.create(url).let { "${it.scheme}://${it.authority}" }

    /** Runs [call] on the backend's own thread, never the client thread. */
    fun <T> async(call: Backend.() -> T): CompletableFuture<T> = CompletableFuture.supplyAsync({ call() }, worker)

    /** A code that the website trades for a session once, within a minute. */
    fun oneTimeToken(): String =
        json.parseToJsonElement(send { get("/api/auth/one-time-token/generate", it) }.body()).jsonObject.getValue("token").jsonPrimitive.content

    private fun get(path: String, token: String) = http.send(builder(path, token).GET().build(), BodyHandlers.ofString())

    private fun builder(path: String, token: String) = HttpRequest.newBuilder(URI.create(url + path))
        .timeout(TIMEOUT)
        .header("Origin", origin)
        .header("Authorization", "Bearer $token")

    /** Sends with the session, signs in first when there is none, and signs in again once when the backend says it ended. */
    private fun send(call: (token: String) -> HttpResponse<String>): HttpResponse<String> {
        var response = reach { call(token ?: signIn()) }
        if (response.statusCode() == 401) {
            token = null
            response = reach { call(signIn()) }
        }
        if (response.statusCode() !in 200..299) throw BackendException(response.statusCode(), "The backend answered ${response.statusCode()}.")
        return response
    }

    private fun signIn(): String {
        val challenge = reach { http.send(auth("/api/auth/minecraft/challenge", "{}"), BodyHandlers.ofString()) }
        if (challenge.statusCode() != 200) throw BackendException(challenge.statusCode(), "The backend would not start a sign-in.")
        val serverId = json.parseToJsonElement(challenge.body()).jsonObject.getValue("serverId").jsonPrimitive.content
        try {
            identity.join(serverId)
        } catch (e: Exception) {
            throw BackendException(null, "Mojang would not confirm this account.", e)
        }
        val verify = reach {
            http.send(auth("/api/auth/minecraft/verify", buildJsonObject { put("name", identity.name); put("serverId", serverId) }.toString()), BodyHandlers.ofString())
        }
        if (verify.statusCode() != 200) throw BackendException(verify.statusCode(), "The backend did not accept the sign-in.")
        return json.parseToJsonElement(verify.body()).jsonObject.getValue("token").jsonPrimitive.content.also { token = it }
    }

    private fun auth(path: String, body: String) = HttpRequest.newBuilder(URI.create(url + path))
        .timeout(TIMEOUT)
        .header("Origin", origin)
        .header("Content-Type", "application/json")
        .POST(BodyPublishers.ofString(body))
        .build()

    private fun <T> reach(call: () -> T): T = try {
        call()
    } catch (e: java.io.IOException) {
        throw BackendException(null, "The backend could not be reached.", e)
    }

    private companion object {
        val TIMEOUT: Duration = Duration.ofSeconds(20)
    }
}
