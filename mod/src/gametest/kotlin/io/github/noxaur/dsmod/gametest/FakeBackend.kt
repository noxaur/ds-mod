package io.github.noxaur.dsmod.gametest

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import io.github.noxaur.dsmod.edge.Account
import io.github.noxaur.dsmod.core.backend.Backend
import io.github.noxaur.dsmod.core.backend.Identity
import java.net.InetSocketAddress

class FakeBackend(player: String, private val signIn: () -> Unit = {}, private val answer: (Request) -> Pair<Int, String>) : AutoCloseable {
    class Request(val exchange: HttpExchange, val body: ByteArray) {
        val path: String = exchange.requestURI.path
        val signedIn get() = exchange.requestHeaders.getFirst("Authorization") == "Bearer s1"
    }

    private val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0).apply {
        createContext("/") { exchange -> respond(exchange) }
        start()
    }

    init {
        Account.backend = Backend("http://127.0.0.1:${server.address.port}", object : Identity {
            override val name = player
            override fun join(serverId: String) {}
        }, "test")
    }

    override fun close() = server.stop(0)

    private fun respond(exchange: HttpExchange) {
        val request = Request(exchange, exchange.requestBody.readBytes())
        val (status, answer) = when (request.path) {
            "/api/auth/minecraft/challenge" -> 200.also { signIn() } to """{"serverId":"${"0".repeat(40)}"}"""
            "/api/auth/minecraft/verify" -> 200 to """{"token":"s1"}"""
            else -> answer(request)
        }
        val bytes = answer.toByteArray()
        exchange.sendResponseHeaders(status, if (bytes.isEmpty()) -1 else bytes.size.toLong())
        if (bytes.isNotEmpty()) exchange.responseBody.use { it.write(bytes) }
        exchange.close()
    }
}
