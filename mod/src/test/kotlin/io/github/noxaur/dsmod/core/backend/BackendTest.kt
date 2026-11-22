package io.github.noxaur.dsmod.core.backend

import io.github.noxaur.dsmod.harness.resource
import com.sun.net.httpserver.HttpExchange
import io.github.noxaur.dsmod.harness.text
import com.sun.net.httpserver.HttpServer
import io.github.noxaur.dsmod.core.brief.Brief
import io.github.noxaur.dsmod.core.game.ServerSpan
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.net.InetSocketAddress
import java.nio.file.Files
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class BackendTest {
    private class Seen(val method: String, val path: String, val headers: Map<String, String>, val body: ByteArray)

    private val seen = CopyOnWriteArrayList<Seen>()
    private val joined = CopyOnWriteArrayList<String>()
    private var challenges = 0
    private var sessions = 0

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
        val (status, body) = answers[exchange.requestURI.path]?.invoke(request) ?: when (exchange.requestURI.path) {
            "/api/auth/minecraft/challenge" -> 200 to """{"serverId":"${"%040x".format(++challenges)}"}"""
            "/api/auth/minecraft/verify" -> 200 to """{"token":"session-${++sessions}","uuid":"u","name":"Steve"}"""
            else -> 204 to ""
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
    fun `an unreachable backend is an error with no status to blame on the player`() {
        server.stop(0)
        assertEquals(null, assertFailsWith<BackendException> { backend.oneTimeToken() }.status)
    }

    @Test
    fun `a server with no sign-in at the address is not mistaken for a backend that refused`() {
        answers["/api/auth/minecraft/challenge"] = { 404 to """{"error":"Unknown route"}""" }

        val failure = assertFailsWith<BackendException> { backend.oneTimeToken() }

        assertEquals(null, failure.status)
        assertEquals("Nothing at $url answers as the ds-mod backend.", failure.message)
    }

    @Test
    fun `a proof Mojang refuses stops the sign-in before the backend is asked to verify it`() {
        val refusing = Backend(url, object : Identity {
            override val name = "Steve"
            override fun join(serverId: String) = error("Mojang said no to $serverId")
        }, "1.2.3")

        val failure = assertFailsWith<BackendException> { refusing.oneTimeToken() }

        assertEquals(null, failure.status)
        assertEquals("Mojang would not confirm this account.", failure.message)
        assertEquals(listOf("/api/auth/minecraft/challenge"), seen.map { it.path })
    }

    @Test
    fun `a backend that could not ask Mojang is told apart from one that is down`() {
        answers["/api/auth/minecraft/verify"] = { 503 to """{"message":"Mojang answered 403."}""" }

        val failure = assertFailsWith<BackendException> { backend.oneTimeToken() }

        assertEquals(503, failure.status)
        assertEquals(Backend.MOJANG_DOWN, failure.message)
    }

    @Test
    fun `a profile is asked for by name, and another of the player's by its id`() {
        val body = resource("profiles/profile.json")
        answers["/v1/players/Testwyrm/profile"] = { 200 to body }

        assertEquals("Testwyrm", backend.profile("Testwyrm", null).player.name)
        backend.profile("Testwyrm", "aaaaaaaa-0000-4000-8000-000000000001")

        assertEquals(
            listOf("/v1/players/Testwyrm/profile", "/v1/players/Testwyrm/profile?profile=aaaaaaaa-0000-4000-8000-000000000001"),
            seen.map { it.path }.filter { it.startsWith("/v1/") },
        )
    }

    @Test
    fun `a container is asked for from its snapshot, colon and all`() {
        val body = resource("profiles/containers/inventory.json")
        answers["/v1/snapshots/s1/containers/storage:2"] = { 200 to body }

        assertEquals(36, backend.container("s1", "storage:2").items.size)
    }

    @Test
    fun `an error's tag tells apart failures that share a status`() {
        answers["/v1/players/Nobody/profile"] = { 404 to """{"_tag":"PlayerNotFound"}""" }
        answers["/v1/players/Proxy/profile"] = { 502 to "<html>Bad gateway</html>" }

        val missing = assertFailsWith<BackendException> { backend.profile("Nobody", null) }
        val proxied = assertFailsWith<BackendException> { backend.profile("Proxy", null) }

        assertEquals(404 to "PlayerNotFound", missing.status to missing.tag)
        assertEquals(502 to null, proxied.status to proxied.tag)
        assertFailsWith<IllegalArgumentException> { backend.profile("not a name", null) }
    }

    @Test
    fun `the session token appears in no error`() {
        answers[generate] = { 500 to "session-1" }

        val failure = assertFailsWith<BackendException> { backend.oneTimeToken() }

        assertFalse("session-1" in failure.message.orEmpty() || "session-1" in failure.stackTraceToString())
    }

    private val offer = """{"id":"r1","brief":{"name":"Golem","steps":[{"do":"Warp"}]},"requirements":[{"kind":"area","value":"The End"}],"timeoutSeconds":600}"""

    @Test
    fun `reads the offers as the contract sends them`() {
        answers["/v1/contrib/requests"] = { 200 to "[$offer]" }

        assertEquals(listOf(Request("r1", Brief("Golem", listOf(Brief.Step("Warp"))), listOf(Requirement("area", "The End")), 600)), backend.requests())
        assertEquals("Bearer session-1", seen.last().headers["authorization"])
    }

    @Test
    fun `an offer whose brief the mod cannot read is left out, and a new field around a brief is not`() {
        val empty = """{"id":"r2","brief":{"name":"","steps":[]},"requirements":[],"timeoutSeconds":60}"""
        val misspelled = """{"id":"r3","brief":{"name":"Golem","steps":[{"do":"Wait","secs":90}]},"requirements":[],"timeoutSeconds":600}"""
        val newer = """{"id":"r4","brief":{"name":"Golem","steps":[{"do":"Wait","seconds":90}]},"requirements":[],"timeoutSeconds":600,"priority":2}"""
        answers["/v1/contrib/requests"] = { 200 to "[$empty, $misspelled, $newer]" }

        val requests = backend.requests()

        assertEquals(listOf("r4"), requests.map { it.id })
        assertEquals(90, requests.single().brief.steps.single().seconds)
    }

    @Test
    fun `skips with an empty post`() {
        backend.skip("r1")

        val skip = seen.last()
        assertEquals("POST" to "/v1/contrib/requests/r1/skip", skip.method to skip.path)
        assertEquals(0, skip.body.size)
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
    fun `an upload the backend refuses for good is an answer, and any other failure throws`() {
        val file = Files.createTempFile("capture", ".gz")
        val path = "/v1/contrib/requests/r1/submissions"
        for ((status, answer) in listOf(404 to Upload.UNKNOWN, 409 to Upload.CLOSED, 413 to Upload.TOO_BIG, 200 to Upload.SENT)) {
            answers[path] = { status to "" }
            assertEquals(answer, backend.submit("r1", file))
        }
        for (status in listOf(400, 503)) {
            answers[path] = { status to "" }
            assertEquals(status, assertFailsWith<BackendException> { backend.submit("r1", file) }.status)
        }
    }

    @Test
    fun `a sign-in the backend refuses is never read as a refused upload`() {
        val file = Files.createTempFile("capture", ".gz")
        val refusing = Backend(url, object : Identity {
            override val name = "Steve"
            override fun join(serverId: String) = error("no")
        }, "1.2.3")

        assertFailsWith<BackendException> { refusing.submit("r1", file) }
    }

    @Test
    fun `an offer whose id is not a short name is left out`() {
        answers["/v1/contrib/requests"] = { 200 to """[${offer.replace("\"r1\"", "\"../r1\"")}, ${offer.replace("\"r1\"", "\"${"a".repeat(65)}\"")}, $offer]""" }

        assertEquals(listOf("r1"), backend.requests().map { it.id })
    }

    @Test
    fun `reports sightings with exactly the keys the contract allows`() {
        backend.unknowns(listOf(Sighting("protector-status", "Protector: # left", "Protector: 5 left")))

        val report = seen.last()
        assertEquals("application/json", report.headers["content-type"])
        assertEquals(
            """{"modVersion":"1.2.3","sightings":[{"kind":"protector-status","signature":"Protector: # left","sample":"Protector: 5 left"}]}""",
            String(report.body),
        )
    }

    @Test
    fun `reports items with exactly the keys the contract allows`() {
        val span = ServerSpan("Scylla", color = 0xFFAA00, bold = true)
        backend.items(
            listOf(
                ItemSighting(
                    "scylla", "minecraft:iron_sword", null, "gold", text(span), listOf(text(), text("Damage")),
                    true, null, 0x112233, buildJsonObject { put("item_data", buildJsonObject { put("item_id", "scylla") }) },
                ),
            ),
        )

        val report = seen.last()
        assertEquals("POST /v1/contrib/items", "${report.method} ${report.path}")
        assertEquals(
            """{"modVersion":"1.2.3","items":[{"entry":"scylla","material":"minecraft:iron_sword","skinId":"gold","name":[{"text":"Scylla","color":"#FFAA00","bold":true}],"lore":[[],[{"text":"Damage"}]],"glint":true,"color":1122867,"data":{"item_data":{"item_id":"scylla"}}}]}""",
            String(report.body),
        )
    }

    @Test
    fun `is signed in only once a call has signed it in`() {
        assertFalse(backend.signedIn)
        backend.skip("r1")
        assertTrue(backend.signedIn)
    }
}
