package io.github.noxaur.dsmod.core.backend

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import io.github.noxaur.dsmod.core.brief.Brief
import java.net.InetSocketAddress
import java.nio.file.Files
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
    private val backend = Backend(url, identity, "1.2.3")

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

    private val requestJson = """{"id":"r1","brief":{"name":"Golem","steps":[{"do":"Warp","note":null,"seconds":null}]},
        "requirements":[{"kind":"area","value":"The End"}],"timeoutSeconds":600}"""

    @Test
    fun `nothing connects until a call is made`() {
        assertEquals(emptyList(), seen)
    }

    @Test
    fun `signs in with Mojang's proof, then calls with the session`() {
        answers["/v1/contrib/requests"] = { 200 to "[$requestJson]" }

        val requests = backend.requests()

        assertEquals(listOf(Request("r1", Brief("Golem", listOf(Brief.Step("Warp"))), listOf(Requirement("area", "The End")), 600)), requests)
        assertEquals(listOf("%040x".format(1)), joined)
        val verify = seen.single { it.path == "/api/auth/minecraft/verify" }
        assertEquals("""{"name":"Steve","serverId":"${"%040x".format(1)}"}""", String(verify.body))
        val call = seen.last()
        assertEquals("Bearer session-1", call.headers["authorization"])
        assertEquals(url, call.headers["origin"])
        assertTrue(seen.all { it.headers["origin"] == url }, "sign-in sends the origin too")
    }

    @Test
    fun `a request the mod cannot read is left out of the list`() {
        answers["/v1/contrib/requests"] = { 200 to """[$requestJson, {"id":"r2","brief":{"name":"","steps":[]},"requirements":[],"timeoutSeconds":1}]""" }

        assertEquals(listOf("r1"), backend.requests().map { it.id })
    }

    @Test
    fun `an offer whose brief has a key the mod does not know is dropped, and a new field around it is not`() {
        val misspelled = """{"id":"r2","brief":{"name":"Golem","steps":[{"do":"Wait","secs":90}]},"requirements":[],"timeoutSeconds":600}"""
        val newer = """{"id":"r3","brief":{"name":"Golem","steps":[{"do":"Wait","seconds":90}]},"requirements":[],"timeoutSeconds":600,"priority":2}"""
        answers["/v1/contrib/requests"] = { 200 to "[$misspelled, $newer]" }

        val requests = backend.requests()

        assertEquals(listOf("r3"), requests.map { it.id })
        assertEquals(90, requests.single().brief.steps.single().seconds)
    }

    @Test
    fun `a session that ended signs in again once and repeats the call`() {
        var calls = 0
        answers["/v1/contrib/requests/r1/skip"] = { if (++calls == 1) 401 to """{"message":"ended"}""" else 204 to "" }

        backend.skip("r1")

        assertEquals(2, challenges)
        assertEquals(2, joined.size)
        assertEquals(listOf("Bearer session-1", "Bearer session-2"), seen.filter { it.path.endsWith("/skip") }.map { it.headers["authorization"] })
    }

    @Test
    fun `a second 401 is an error, not a loop`() {
        answers["/v1/contrib/requests/r1/skip"] = { 401 to "{}" }

        val failure = assertFailsWith<BackendException> { backend.skip("r1") }

        assertEquals(401, failure.status)
        assertEquals(2, challenges)
        assertEquals(2, seen.count { it.path.endsWith("/skip") })
    }

    @Test
    fun `uploads the file as bytes with the mod's version`() {
        val file = Files.createTempFile("capture", ".jsonl.gz").also { Files.write(it, byteArrayOf(31, -117, 8, 0, 1, 2, 3)) }
        answers["/v1/contrib/requests/r1/submissions"] = { 200 to """{"id":"s1"}""" }

        backend.submit("r1", file)

        val upload = seen.last()
        assertEquals("/v1/contrib/requests/r1/submissions?modVersion=1.2.3", upload.path)
        assertEquals("application/octet-stream", upload.headers["content-type"])
        assertEquals(listOf<Byte>(31, -117, 8, 0, 1, 2, 3), upload.body.toList())
    }

    @Test
    fun `a conflict names its status`() {
        val file = Files.createTempFile("capture", ".gz")
        answers["/v1/contrib/requests/r1/submissions"] = { 409 to """{"message":"already"}""" }

        assertEquals(409, assertFailsWith<BackendException> { backend.submit("r1", file) }.status)
    }

    @Test
    fun `sends sightings as the contract says`() {
        backend.unknowns(listOf(Sighting("golem-status", "Protector: # left", "Protector: 5 left")))

        assertEquals(
            """{"modVersion":"1.2.3","sightings":[{"kind":"golem-status","signature":"Protector: # left","sample":"Protector: 5 left"}]}""",
            String(seen.last().body),
        )
    }

    @Test
    fun `gives the one-time token`() {
        answers["/api/auth/one-time-token/generate"] = { 200 to """{"token":"once"}""" }

        assertEquals("once", backend.oneTimeToken())
        assertEquals("GET", seen.last().method)
    }

    @Test
    fun `an unreachable backend and a refused proof are errors with no status to blame on the player`() {
        server.stop(0)
        assertEquals(null, assertFailsWith<BackendException> { backend.skip("r1") }.status)

        val refusing = Backend(url, object : Identity {
            override val name = "Steve"
            override fun join(serverId: String) = error("Mojang said no")
        }, "1")
        assertEquals(null, assertFailsWith<BackendException> { refusing.skip("r1") }.status)
    }

    @Test
    fun `the session token appears in no error`() {
        answers["/v1/contrib/requests"] = { 500 to "session-1" }

        val failure = assertFailsWith<BackendException> { backend.requests() }

        assertFalse("session-1" in failure.message.orEmpty() || "session-1" in failure.stackTraceToString())
    }
}
