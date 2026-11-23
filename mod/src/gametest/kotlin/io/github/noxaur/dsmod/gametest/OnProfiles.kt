package io.github.noxaur.dsmod.gametest

import io.github.noxaur.dsmod.edge.profile.ProfileScreen
import io.github.noxaur.dsmod.edge.ui.KitButton
import io.github.noxaur.dsmod.edge.ui.KitTab
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.components.AbstractWidget
import java.util.concurrent.CopyOnWriteArrayList

class OnProfiles(private val context: ClientGameTestContext) : AutoCloseable {
    private fun resource(name: String) = checkNotNull(javaClass.getResource("/profiles/$name")).readText()

    private val profile = resource("profile.json")

    val asked = CopyOnWriteArrayList<String>()

    @Volatile var ownDown = false

    private val backend = FakeBackend("Testwyrm") { call ->
        val path = call.path
        asked += call.exchange.requestURI.toString()
        when {
            path == "/v1/players/${Minecraft.getInstance().user.name}/profile" && ownDown -> 503 to """{"_tag":"UpstreamUnavailable"}"""
            path == "/v1/players/Testwyrm/profile" || path == "/v1/players/${Minecraft.getInstance().user.name}/profile" -> 200 to profile
            path == "/v1/players/Hidden/profile" -> 200 to profile.replace("\"hidden\": false", "\"hidden\": true").replace(PETS_HIDDEN, "$1false")
            path == "/v1/players/Stale/profile" -> 200 to profile.replace("\"stale\": false", "\"stale\": true")
            path == "/v1/players/Down/profile" -> 503 to """{"_tag":"UpstreamUnavailable"}"""
            path == "/v1/players/Slow/profile" -> 200.also { Thread.sleep(LOADING_SCREENSHOT_MILLIS) } to profile
            path.startsWith("/v1/players/") -> 404 to """{"_tag":"PlayerNotFound"}"""
            path.matches(Regex("/v1/snapshots/[^/]+/containers")) -> 200 to ALL.joinToString(",", "[", "]") { resource("containers/$it.json") }
            path.matches(Regex("/v1/snapshots/[^/]+/containers/.+")) -> 200 to resource("containers/${path.substringAfterLast('/').replace(':', '-')}.json")
            else -> 404 to ""
        }
    }

    override fun close() = backend.close()

    fun screen(client: Minecraft) = client.gui.screen() as ProfileScreen

    fun command(line: String) = context.runOnClient<Nothing> { it.connection!!.sendCommand(line) }

    fun open(name: String) {
        context.setScreen { null }
        command("pv $name")
        context.waitForScreen(ProfileScreen::class.java)
        context.waitUntil("the profile of $name loaded") { screen(it).widgets<KitTab>().isNotEmpty() }
    }

    fun move(x: Int, y: Int) = context.point(x, y)

    fun tab(name: String) {
        context.click(context.computeOnClient<AbstractWidget, Nothing> { client -> screen(client).widgets<KitTab>().first { it.message.string == name } as AbstractWidget })
    }

    fun tabs() = context.computeOnClient<List<String>, Nothing> { client -> screen(client).widgets<KitTab>().map { it.message.string } }

    private fun pageButtons(client: Minecraft) = screen(client).widgets<KitButton>().filter { it.message.string.all(Char::isDigit) }

    fun pages() = context.computeOnClient<List<String>, Nothing> { client -> pageButtons(client).map { it.message.string } }

    fun press(page: String) {
        context.click(context.computeOnClient<KitButton, Nothing> { client -> pageButtons(client).first { it.message.string == page } })
    }

    fun hoverItem(contentX: Int, viewY: Int, tooltips: List<String>): String? {
        for (row in 0 until 4) {
            for (x in contentX until contentX + 296 step 9) {
                move(x, viewY + 9 + row * 18)
                tooltips.lastOrNull()?.let { return it }
            }
        }
        return null
    }

    private companion object {
        const val LOADING_SCREENSHOT_MILLIS = 1500L
        val ALL = listOf("inventory", "ender_chest", "storage-0", "storage-1", "storage-2", "wardrobe-0", "accessories", "pets")
        val PETS_HIDDEN = Regex("(?s)(\"id\": \"pets\".*?\"hidden\": )true")
    }
}
