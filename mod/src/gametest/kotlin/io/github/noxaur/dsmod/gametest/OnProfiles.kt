package io.github.noxaur.dsmod.gametest

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import io.github.noxaur.dsmod.core.backend.Backend
import io.github.noxaur.dsmod.core.backend.Identity
import io.github.noxaur.dsmod.edge.Account
import io.github.noxaur.dsmod.edge.profile.ProfileScreen
import io.github.noxaur.dsmod.edge.ui.KitButton
import io.github.noxaur.dsmod.edge.ui.KitTab
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.components.AbstractWidget
import java.net.InetSocketAddress
import java.util.concurrent.CopyOnWriteArrayList

/**
 * A stub of the backend's profile routes, which answers with the profiles the unit tests read, and the real mouse on
 * the profile viewer, in GUI pixels, for the tests of the profile viewer. Close it to stop the stub.
 */
class OnProfiles(private val context: ClientGameTestContext) : AutoCloseable {
    private fun resource(name: String) = checkNotNull(javaClass.getResource("/profiles/$name")).readText()

    private val profile = resource("profile.json")

    /** Every path the viewer asked the stub for, in order. */
    val asked = CopyOnWriteArrayList<String>()

    private val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0).apply {
        createContext("/") { exchange -> respond(exchange) }
        start()
    }

    init {
        Account.backend = Backend("http://127.0.0.1:${server.address.port}", object : Identity {
            override val name = "Testwyrm"
            override fun join(serverId: String) {}
        }, "test")
    }

    override fun close() = server.stop(0)

    private fun respond(exchange: HttpExchange) {
        val path = exchange.requestURI.path
        asked += exchange.requestURI.toString()
        val (status, answer) = when {
            path == "/api/auth/minecraft/challenge" -> 200 to """{"serverId":"${"0".repeat(40)}"}"""
            path == "/api/auth/minecraft/verify" -> 200 to """{"token":"s1"}"""
            path == "/v1/players/Testwyrm/profile" -> 200 to profile
            // A hidden inventory hides every container but the pets, as the backend hides them.
            path == "/v1/players/Hidden/profile" -> 200 to profile.replace("\"hidden\": false", "\"hidden\": true").replace(PETS_HIDDEN, "$1false")
            path == "/v1/players/Stale/profile" -> 200 to profile.replace("\"stale\": false", "\"stale\": true")
            path == "/v1/players/Down/profile" -> 503 to """{"_tag":"UpstreamUnavailable"}"""
            path == "/v1/players/Slow/profile" -> 200.also { Thread.sleep(SLOW_MILLIS) } to profile
            path.startsWith("/v1/players/") -> 404 to """{"_tag":"PlayerNotFound"}"""
            // Each container as the backend answered it for the same player, by its id with the colon a dash.
            path.matches(Regex("/v1/snapshots/[^/]+/containers/.+")) -> 200 to resource("containers/${path.substringAfterLast('/').replace(':', '-')}.json")
            else -> 404 to ""
        }
        val bytes = answer.toByteArray()
        exchange.sendResponseHeaders(status, if (bytes.isEmpty()) -1 else bytes.size.toLong())
        if (bytes.isNotEmpty()) exchange.responseBody.use { it.write(bytes) }
        exchange.close()
    }

    fun screen(client: Minecraft) = client.gui.screen() as ProfileScreen

    /** Types [line] as a command, which is how Fabric decides whether it is the mod's or the server's. */
    fun command(line: String) = context.runOnClient<Nothing> { it.connection!!.sendCommand(line) }

    /** Opens the viewer on [name] with `/pv` and waits until its profile shows. */
    fun open(name: String) {
        context.setScreen { null }
        command("pv $name")
        context.waitForScreen(ProfileScreen::class.java)
        context.waitUntil("the profile of $name loaded") { screen(it).children().any { w -> w is KitTab } }
    }

    fun move(x: Int, y: Int) {
        val scale = context.computeOnClient<Int, Nothing> { it.window.guiScale }
        context.input.setCursorPos(x * scale.toDouble(), y * scale.toDouble())
        context.waitTick()
    }

    fun tab(name: String) {
        val at = context.computeOnClient<Pair<Int, Int>, Nothing> { client ->
            val tab = screen(client).children().filterIsInstance<KitTab>().first { it.message.string == name } as AbstractWidget
            tab.x + tab.width / 2 to tab.y + tab.height / 2
        }
        move(at.first, at.second)
        context.input.pressMouse(0)
        context.waitTick()
    }

    fun tabs() = context.computeOnClient<List<String>, Nothing> { client -> screen(client).children().filterIsInstance<KitTab>().map { it.message.string } }

    /** The buttons that show a page of the tab, each named by its number. */
    private fun pageButtons(client: Minecraft) = screen(client).children().filterIsInstance<KitButton>().filter { it.message.string.all(Char::isDigit) }

    fun pages() = context.computeOnClient<List<String>, Nothing> { client -> pageButtons(client).map { it.message.string } }

    fun press(page: String) {
        val at = context.computeOnClient<Pair<Int, Int>, Nothing> { client ->
            pageButtons(client).first { it.message.string == page }.let { it.x + it.width / 2 to it.y + it.height / 2 }
        }
        move(at.first, at.second)
        context.input.pressMouse(0)
        context.waitTick()
    }

    /** Sweeps the mouse along the grid's rows until an item is under it, and says which. */
    fun hoverItem(contentX: Int, viewY: Int): String? {
        for (row in 0 until 4) {
            for (x in contentX until contentX + 296 step 9) {
                move(x, viewY + 9 + row * 18)
                val entry = context.computeOnClient<String?, Nothing> { screen(it).hovered?.entry }
                if (entry != null) return entry
            }
        }
        return null
    }

    private companion object {
        /** Long enough for the screenshot of the loading line. */
        const val SLOW_MILLIS = 1500L
        val PETS_HIDDEN = Regex("(?s)(\"id\": \"pets\".*?\"hidden\": )true")
    }
}
