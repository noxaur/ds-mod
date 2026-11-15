package io.github.noxaur.dsmod.core.backend

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Runs the real [Backend] against a stub that answers like the backend's routes, over a real socket. */
class BackendTest {
    private class Seen(val method: String, val path: String, val headers: Map<String, String>, val body: ByteArray)

    private val seen = CopyOnWriteArrayList<Seen>()
    private val joined = CopyOnWriteArrayList<String>()
    private var challenges = 0
    private var sessions = 0

    /** What each path answers, as a status and a body. The default is 204. */
    private val answers = HashMap<String, (Seen) -> Pair<Int, String>>()

    private val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0).apply {
        createContext("/") { exchange -> respond(exchange) }
        start()
    }
    private val url = "http://127.0.0.1:${server.address.port}"
    private val identity = object : Identity {
        override val name = "Steve"
        override fun join(serverId: String) {
            joined += serverId
        }
    }
    private val backend = Backend(url, identity)

    @AfterTest
    fun stop() = server.stop(0)

    private fun respond(exchange: HttpExchange) {
        val request = Seen(
            exchange.requestMethod,
            exchange.requestURI.toString(),
            exchange.requestHeaders.entries.associate { it.key.lowercase() to it.value.first() },
            exchange.requestBody.readBytes(),
        )
        seen += request
        val (status, body) = when (exchange.requestURI.path) {
            "/api/auth/minecraft/challenge" -> 200 to """{"serverId":"${"%040x".format(++challenges)}"}"""
            "/api/auth/minecraft/verify" -> 200 to """{"token":"session-${++sessions}","uuid":"u","name":"Steve"}"""
            else -> answers[exchange.requestURI.path]?.invoke(request) ?: (204 to "")
        }
        val bytes = body.toByteArray()
        exchange.sendResponseHeaders(status, if (status == 204) -1 else bytes.size.toLong())
        if (status != 204) exchange.responseBody.use { it.write(bytes) }
        exchange.close()
    }

    private val generate = "/api/auth/one-time-token/generate"

    @Test
    fun `nothing connects until a call is made`() {
        assertEquals(emptyList(), seen)
    }

    @Test
    fun `signs in with Mojang's proof, then calls with the session`() {
        answers[generate] = { 200 to """{"token":"once"}""" }

        assertEquals("once", backend.oneTimeToken())
        assertEquals(listOf("%040x".format(1)), joined)
        val verify = seen.single { it.path == "/api/auth/minecraft/verify" }
        assertEquals("""{"name":"Steve","serverId":"${"%040x".format(1)}"}""", String(verify.body))
        val call = seen.last()
        assertEquals("GET $generate", "${call.method} ${call.path}")
        assertEquals("Bearer session-1", call.headers["authorization"])
        assertEquals(url, call.headers["origin"])
        assertTrue(seen.all { it.headers["origin"] == url }, "sign-in sends the origin too")
    }

    @Test
    fun `a session that ended signs in again once and repeats the call`() {
        var calls = 0
        answers[generate] = { if (++calls == 1) 401 to """{"message":"ended"}""" else 200 to """{"token":"once"}""" }

        backend.oneTimeToken()

        assertEquals(2, challenges)
        assertEquals(2, joined.size)
        assertEquals(listOf("Bearer session-1", "Bearer session-2"), seen.filter { it.path == generate }.map { it.headers["authorization"] })
    }

    @Test
    fun `a second 401 is an error, not a loop`() {
        answers[generate] = { 401 to "{}" }

        val failure = assertFailsWith<BackendException> { backend.oneTimeToken() }

        assertEquals(401, failure.status)
        assertEquals(2, challenges)
        assertEquals(2, seen.count { it.path == generate })
    }

    @Test
    fun `an unreachable backend and a refused proof are errors with no status to blame on the player`() {
        server.stop(0)
        assertEquals(null, assertFailsWith<BackendException> { backend.oneTimeToken() }.status)

        val refusing = Backend(url, object : Identity {
            override val name = "Steve"
            override fun join(serverId: String) = error("Mojang said no")
        })
        assertEquals(null, assertFailsWith<BackendException> { refusing.oneTimeToken() }.status)
    }

    @Test
    fun `the session token appears in no error`() {
        answers[generate] = { 500 to "session-1" }

        val failure = assertFailsWith<BackendException> { backend.oneTimeToken() }

        assertFalse("session-1" in failure.message.orEmpty() || "session-1" in failure.stackTraceToString())
    }
}
