package io.github.noxaur.dsmod.gametest

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import io.github.noxaur.dsmod.core.backend.Backend
import io.github.noxaur.dsmod.core.backend.Identity
import io.github.noxaur.dsmod.core.ui.Theme
import io.github.noxaur.dsmod.edge.Account
import io.github.noxaur.dsmod.edge.DsModClient
import io.github.noxaur.dsmod.edge.profile.ProfileScreen
import io.github.noxaur.dsmod.edge.profile.Profiles
import io.github.noxaur.dsmod.edge.ui.KitButton
import io.github.noxaur.dsmod.edge.ui.KitTab
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest
import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext
import net.minecraft.client.gui.components.AbstractWidget
import net.minecraft.client.gui.screens.ChatScreen
import net.minecraft.core.component.DataComponents
import net.minecraft.network.chat.ClickEvent
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.Style
import java.net.InetSocketAddress
import java.util.Optional
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Looks players up through `/pv` against a stub of the backend's profile routes, which answers with the profiles the
 * unit tests read. Opens the overview, turns the model with the real mouse, hovers a skill, opens the inventory and a
 * storage page and hovers an item, and checks that each tab asks for its container once. Checks that `/pv 12` is left
 * to the server's vault command, and that a missing player and a hidden inventory each say so. Read the screenshots
 * in both themes.
 */
class ProfileViewerTest : FabricClientGameTest {
    private fun resource(name: String) = checkNotNull(javaClass.getResource("/profiles/$name")).readText()

    private val profile = resource("profile.json")

    private companion object {
        /** Long enough for the screenshot of the loading line. */
        const val SLOW_MILLIS = 1500L
        val PETS_HIDDEN = Regex("(?s)(\"id\": \"pets\".*?\"hidden\": )true")
    }
    private val asked = CopyOnWriteArrayList<String>()

    /** The `item_id` of each stack whose tooltip another mod's hook saw, as DragSim Utils' hooks read it. */
    private val hooked = CopyOnWriteArrayList<String>()

    private val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0).apply {
        createContext("/") { exchange -> respond(exchange) }
        start()
    }

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

    override fun runTest(context: ClientGameTestContext) {
        // Another mod adds to a real item's tooltip through Fabric's hook and reads the server's data from the stack.
        ItemTooltipCallback.EVENT.register { stack, _, _, lines ->
            val id = stack.get(DataComponents.CUSTOM_DATA)?.copyTag()?.getCompoundOrEmpty("item_data")?.getStringOr("item_id", "")
            if (!id.isNullOrEmpty()) {
                hooked += id
                lines += Component.literal("Added by another mod")
            }
        }
        Account.backend = Backend("http://127.0.0.1:${server.address.port}", object : Identity {
            override val name = "Testwyrm"
            override fun join(serverId: String) {}
        }, "test")
        try {
            context.worldBuilder().create().use { world ->
                context.waitTicks(20)
                // The server's /pv <vault> is not ours, so nothing opens and the line goes on to the server.
                command(context, "pv 12")
                context.waitTicks(5)
                check(context.computeOnClient<Boolean, Nothing> { it.gui.screen() !is ProfileScreen }) { "/pv 12 opened the profile viewer" }

                command(context, "pv Testwyrm")
                context.waitForScreen(ProfileScreen::class.java)
                waitUntil(context, "the profile loaded") { screen(it).children().any { w -> w is KitTab } }
                context.waitTicks(10)
                context.takeScreenshot("profile-overview")
                check(context.computeOnClient<Boolean, Nothing> { !screen(it).overflows }) { "The overview needs a scrollbar" }
                context.runOnClient<Nothing> { DsModClient.settings.hud.theme = Theme.VANILLA }
                context.waitTicks(2)
                context.takeScreenshot("profile-overview-vanilla")
                context.runOnClient<Nothing> { DsModClient.settings.hud.theme = Theme.CHARCOAL }

                val (contentX, viewY) = context.computeOnClient<Pair<Int, Int>, Nothing> { screen(it).contentX to screen(it).viewY }
                // The model sits left of the content. A drag turns it, and the skill under the mouse shows its XP.
                move(context, contentX - 57, viewY + 60)
                context.input.holdMouse(0)
                move(context, contentX - 20, viewY + 50)
                context.takeScreenshot("profile-model-turned")
                context.input.releaseMouse(0)
                move(context, contentX + 40, viewY + 18)
                context.takeScreenshot("profile-skill-tooltip")

                // Kills past the first five are in the well's tooltip.
                move(context, contentX + 200, viewY + 150)
                context.takeScreenshot("profile-kills-tooltip")
                tab(context, "Dungeons")
                context.waitTicks(2)
                context.takeScreenshot("profile-dungeons")
                check(context.computeOnClient<Boolean, Nothing> { !screen(it).overflows }) { "The dungeons tab needs a scrollbar" }

                // Each stat's hover breaks it down by the server's groups. Health is the second row.
                tab(context, "Player Stats")
                move(context, contentX + 20, viewY + 28)
                context.takeScreenshot("profile-stat-tooltip")

                tab(context, "Inventory")
                waitUntil(context, "the inventory loaded") { asked.any { "/containers/inventory" in it } }
                context.waitTicks(5)
                // The other tabs have a title row, so their content starts lower than the overview's.
                val gridY = context.computeOnClient<Int, Nothing> { screen(it).viewY }
                val item = hoverItem(context, contentX, gridY)
                context.takeScreenshot("profile-inventory-tooltip")
                check(item == "reinforced_plating") { "The first item hovered is $item" }
                check("reinforced_plating" in hooked) { "Another mod's tooltip hook saw only $hooked" }

                tab(context, "Storage")
                context.waitTicks(5)
                press(context, "2")
                context.waitTicks(5)
                context.takeScreenshot("profile-storage-2")
                tab(context, "Inventory")
                context.waitTicks(2)
                val inventories = asked.count { "/containers/inventory" in it }
                check(inventories == 1) { "The inventory was asked for $inventories times" }
                check(asked.count { "/containers/storage:1" in it } == 1) { "Storage 2 was asked for ${asked.filter { "storage" in it }}" }

                context.setScreen { null }
                command(context, "pv Nobody")
                context.waitForScreen(ProfileScreen::class.java)
                context.waitTicks(10)
                context.takeScreenshot("profile-not-found")

                context.setScreen { null }
                val containers = asked.count { "/containers/" in it }
                command(context, "dsmod pv Hidden")
                context.waitForScreen(ProfileScreen::class.java)
                waitUntil(context, "the hidden profile loaded") { screen(it).children().any { w -> w is KitTab } }
                tab(context, "Inventory")
                context.waitTicks(5)
                context.takeScreenshot("profile-hidden")
                check(asked.count { "/containers/" in it } == containers) { "A hidden container was fetched" }
                check(context.computeOnClient<Boolean, Nothing> { client -> screen(client).children().any { (it as? KitTab)?.message?.string == "Pets" } }) { "A hidden inventory hid the pets" }

                names(context, world)
                tour(context)
                // The pets come with the profile, so their tab fetches nothing.
                check(asked.none { "/containers/pets" in it }) { "The pets were fetched" }
                states(context)

                context.setScreen { null }
                context.runOnClient<Nothing> { DsModClient.settings.general.profileViewer = false }
                command(context, "pv Testwyrm")
                context.waitTicks(5)
                check(context.computeOnClient<Boolean, Nothing> { it.gui.screen() !is ProfileScreen }) { "/pv opened with Profile Viewer off" }
                context.runOnClient<Nothing> { DsModClient.settings.general.profileViewer = true }
            }
        } finally {
            server.stop(0)
        }
    }

    /** Every tab and every page, in both themes, with the mouse off the panel, for reading the whole screen. */
    private fun tour(context: ClientGameTestContext) {
        context.setScreen { null }
        command(context, "pv Testwyrm")
        context.waitForScreen(ProfileScreen::class.java)
        waitUntil(context, "the profile loaded for the tour") { screen(it).children().any { w -> w is KitTab } }
        move(context, 0, 0)
        for (theme in listOf(Theme.CHARCOAL, Theme.VANILLA)) {
            context.runOnClient<Nothing> { DsModClient.settings.hud.theme = theme }
            val tabs = context.computeOnClient<List<String>, Nothing> { client -> screen(client).children().filterIsInstance<KitTab>().map { it.message.string } }
            for (name in tabs) {
                tab(context, name)
                move(context, 0, 0)
                val slug = name.lowercase().replace(' ', '-')
                val pages = context.computeOnClient<List<String>, Nothing> { client -> pageButtons(client).map { it.message.string } }
                for (page in pages.ifEmpty { listOf("1") }) {
                    if (pages.isNotEmpty()) press(context, page)
                    move(context, 0, 0)
                    context.waitTicks(5)
                    context.takeScreenshot("tour-${theme.name.lowercase()}-$slug${if (page != "1") "-$page" else ""}")
                }
            }
        }
        context.runOnClient<Nothing> { DsModClient.settings.hud.theme = Theme.CHARCOAL }
    }

    /**
     * A player's name in a chat line from the server links to their profile: its hover says so, and its click runs
     * the mod's own command, which opens the viewer. The rank and the message around the name keep their styles.
     */
    private fun names(context: ClientGameTestContext, world: TestSingleplayerContext) {
        context.setScreen { null }
        context.runOnClient<Nothing> { it.gui.hud.chat.clearMessages(false) }
        world.server.runCommand("""tellraw @a [{"text":"[MVP+] ","color":"aqua"},{"text":"Testwyrm","color":"aqua"},{"text":": anyone for F7?","color":"white"}]""")
        context.waitTicks(5)
        val linked = context.computeOnClient<Map<String, String?>, Nothing> {
            val line = Profiles.names(Component.empty().append("[MVP+] Testwyrm: anyone for F7?"))
            val pieces = LinkedHashMap<String, String?>()
            line.visit({ style, piece -> pieces[piece] = (style.clickEvent as? ClickEvent.RunCommand)?.command(); Optional.empty<Unit>() }, Style.EMPTY)
            pieces
        }
        check(linked == mapOf("[MVP+] " to null, "Testwyrm" to "/dsmod pv Testwyrm", ": anyone for F7?" to null)) { "The name linked as $linked" }

        context.setScreen { ChatScreen("", false) }
        val height = context.computeOnClient<Int, Nothing> { it.window.guiScaledHeight }
        move(context, 52, height - 45)
        context.takeScreenshot("profile-chat-name")
        // A click on the name runs its command as the chat screen runs it, and Fabric keeps it from the server.
        context.runOnClient<Nothing> { it.connection!!.sendUnattendedCommand("dsmod pv Testwyrm", null) }
        context.waitForScreen(ProfileScreen::class.java)
        context.setScreen { null }
    }

    /** What the screen says while it loads, when dragsim.co is down, and when the copy is an old one. */
    private fun states(context: ClientGameTestContext) {
        context.setScreen { null }
        command(context, "pv Slow")
        context.waitForScreen(ProfileScreen::class.java)
        context.waitTicks(10)
        context.takeScreenshot("state-loading")
        waitUntil(context, "the slow profile loaded") { screen(it).children().any { w -> w is KitTab } }

        context.setScreen { null }
        command(context, "pv Down")
        context.waitForScreen(ProfileScreen::class.java)
        waitUntil(context, "the retry button showed") { screen(it).children().any { w -> (w as? AbstractWidget)?.message?.string == "Retry" } }
        context.takeScreenshot("state-upstream-down")

        context.setScreen { null }
        command(context, "pv Stale")
        context.waitForScreen(ProfileScreen::class.java)
        waitUntil(context, "the stale profile loaded") { screen(it).children().any { w -> w is KitTab } }
        val corner = context.computeOnClient<Pair<Int, Int>, Nothing> { screen(it).contentX - 55 to screen(it).viewY + 30 }
        move(context, corner.first, corner.second)
        context.takeScreenshot("state-stale")
    }

    private fun screen(client: net.minecraft.client.Minecraft) = client.gui.screen() as ProfileScreen

    /** The buttons that show a page of the tab, each named by its number. */
    private fun pageButtons(client: net.minecraft.client.Minecraft) =
        screen(client).children().filterIsInstance<KitButton>().filter { it.message.string.all(Char::isDigit) }

    private fun press(context: ClientGameTestContext, page: String) {
        val at = context.computeOnClient<Pair<Int, Int>, Nothing> { client ->
            pageButtons(client).first { it.message.string == page }.let { it.x + it.width / 2 to it.y + it.height / 2 }
        }
        move(context, at.first, at.second)
        context.input.pressMouse(0)
        context.waitTick()
    }

    /** Types [line] as a command, which is how Fabric decides whether it is the mod's or the server's. */
    private fun command(context: ClientGameTestContext, line: String) = context.runOnClient<Nothing> { it.connection!!.sendCommand(line) }

    private fun move(context: ClientGameTestContext, x: Int, y: Int) {
        val scale = context.computeOnClient<Int, Nothing> { it.window.guiScale }
        context.input.setCursorPos(x * scale.toDouble(), y * scale.toDouble())
        context.waitTick()
    }

    private fun tab(context: ClientGameTestContext, name: String) {
        val at = context.computeOnClient<Pair<Int, Int>, Nothing> { client ->
            val tab = screen(client).children().filterIsInstance<KitTab>().first { it.message.string == name } as AbstractWidget
            tab.x + tab.width / 2 to tab.y + tab.height / 2
        }
        move(context, at.first, at.second)
        context.input.pressMouse(0)
        context.waitTick()
    }

    /** Sweeps the mouse along the grid's rows until an item is under it, and says which. */
    private fun hoverItem(context: ClientGameTestContext, contentX: Int, viewY: Int): String? {
        for (row in 0 until 4) {
            for (x in contentX until contentX + 296 step 9) {
                move(context, x, viewY + 9 + row * 18)
                val entry = context.computeOnClient<String?, Nothing> { screen(it).hovered?.entry }
                if (entry != null) return entry
            }
        }
        return null
    }

    private fun waitUntil(context: ClientGameTestContext, what: String, done: (net.minecraft.client.Minecraft) -> Boolean) {
        repeat(200) {
            if (context.computeOnClient<Boolean, Nothing> { done(it) }) return
            context.waitTicks(1)
        }
        error("Timed out waiting until $what")
    }
}
