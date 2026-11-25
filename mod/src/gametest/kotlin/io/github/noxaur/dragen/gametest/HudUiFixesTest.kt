package io.github.noxaur.dragen.gametest

import io.github.noxaur.dragen.core.config.DragenConfig
import io.github.noxaur.dragen.core.feature.hud.layout.hudbackground.HudBackground
import io.github.noxaur.dragen.core.game.Item
import io.github.noxaur.dragen.core.game.ServerSpan
import io.github.noxaur.dragen.core.game.ServerText
import io.github.noxaur.dragen.core.hud.HudGroup
import io.github.noxaur.dragen.core.hud.hud
import io.github.noxaur.dragen.core.profile.Container
import io.github.noxaur.dragen.core.profile.ContainerSummary
import io.github.noxaur.dragen.core.profile.DrawnItem
import io.github.noxaur.dragen.core.profile.Profile
import io.github.noxaur.dragen.core.profile.Load
import io.github.noxaur.dragen.core.profile.ProfileLookup
import io.github.noxaur.dragen.core.profile.ProfileSource
import io.github.noxaur.dragen.core.ui.NavTree
import io.github.noxaur.dragen.core.ui.Section
import io.github.noxaur.dragen.edge.CopyChats
import io.github.noxaur.dragen.edge.CopyChats.Format
import io.github.noxaur.dragen.edge.DragenClient
import io.github.noxaur.dragen.edge.Hud
import io.github.noxaur.dragen.edge.profile.ProfileScreen
import io.github.noxaur.dragen.edge.ui.Kit
import io.github.noxaur.dragen.edge.ui.KitMenu
import io.github.noxaur.dragen.edge.ui.KitNavTree
import io.github.noxaur.dragen.edge.ui.KitRangeSlider
import io.github.noxaur.dragen.edge.ui.KitTab
import io.github.noxaur.dragen.gametest.SettingsSteps.guiScale
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.components.AbstractWidget
import net.minecraft.client.gui.screens.ChatScreen
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.input.MouseButtonEvent
import net.minecraft.client.input.MouseButtonInfo
import net.minecraft.network.chat.Component
import java.util.concurrent.CompletableFuture
import kotlin.math.abs

class HudUiFixesTest : FabricClientGameTest {
    override fun runTest(context: ClientGameTestContext) {
        val window = SettingsSteps.windowSize(context)
        val copies = ArrayList<String>()
        val clipboard = CopyChats.clipboard
        CopyChats.clipboard = { copies += it }
        try {
            context.worldBuilder().create().use { world ->
                SettingsSteps.fit(context, 1)
                freeBlockStaysOnScreen(context, world)
                topIconIsNamed(context)
                menuPaddingDoesNotRunTheFirstItem(context)
                subsectionLiftsUnderTheMouse(context)
                rightClickDeleteSurvivesTheDrag(context)
                profileShowsThePetAndScrollsStorage(context)
                trimmedChatCopiesTheRowsStillShown(context, copies)
            }
        } finally {
            CopyChats.clipboard = clipboard
            context.runOnClient<Nothing> {
                Hud.hide("under")
                Hud.hide("over")
                DragenClient.settings.hud.positions.value = emptyMap()
            }
            SettingsSteps.restore(context, window)
        }
    }
}

private fun freeBlockStaysOnScreen(context: ClientGameTestContext, world: net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext) {
    context.showDragonTimer(world)
    context.waitTicks(5)
    val hud = DragenClient.settings.hud
    val placed = context.computeOnClient<Hud.Placed, Nothing> { Hud.find(DRAGON_TIMER) ?: error("The dragon timer is not on the HUD") }
    val size = context.guiSize()
    val margin = 8f
    val fraction = DragenConfig.Position(
        (size.first - placed.width * placed.scale - margin) / size.first,
        (size.second - placed.height * placed.scale - margin) / size.second,
        1f,
    )
    context.runOnClient<Nothing> {
        hud.positions.value += DRAGON_TIMER to fraction
        DragenClient.feature<HudBackground>().feature.enabled = false
    }
    context.waitTicks(2)
    context.runOnClient<Nothing> { DragenClient.feature<HudBackground>().feature.enabled = true }
    context.waitTicks(2)
    val wide = context.computeOnClient<Hud.Placed, Nothing> { Hud.find(DRAGON_TIMER)!! }
    check(inside(wide, size.first, size.second)) { "On the wide GUI the timer sits at ${wide.x},${wide.y} ${wide.box()}" }

    guiScale(context, 4)
    val narrow = context.computeOnClient<Triple<Hud.Placed, Int, Int>, Nothing> {
        Triple(Hud.find(DRAGON_TIMER)!!, it.window.guiScaledWidth, it.window.guiScaledHeight)
    }
    check(inside(narrow.first, narrow.second, narrow.third)) {
        "After the GUI shrinks the timer runs off it: ${narrow.first.x},${narrow.first.y} size ${narrow.first.width * narrow.first.scale}x${narrow.first.height * narrow.first.scale} on ${narrow.second}x${narrow.third}"
    }
    val saved = context.computeOnClient<DragenConfig.Position, Nothing> { hud.positions.value.getValue(DRAGON_TIMER) }
    check(saved.x == fraction.x && saved.y == fraction.y) { "Clamping rewrote the saved fraction $saved" }

    guiScale(context, 1)
    val back = context.computeOnClient<Hud.Placed, Nothing> { Hud.find(DRAGON_TIMER)!! }
    check(abs(back.x - fraction.x * size.first) <= 1.5f && abs(back.y - fraction.y * size.second) <= 1.5f) {
        "Back on the wide GUI the timer stayed at the clamped corner ${back.x},${back.y} instead of the saved fraction"
    }
    check(inside(wide, size.first, size.second))
}

private fun inside(block: Hud.Placed, width: Int, height: Int): Boolean {
    val box = block.box()
    return box.x >= -0.5f && box.y >= -0.5f && box.x + box.width <= width + 0.5f && box.y + box.height <= height + 0.5f
}

private fun topIconIsNamed(context: ClientGameTestContext) {
    val dirt = Item("minecraft:dirt", 1, ServerText(ServerSpan("Dirt")))
    val stone = Item("minecraft:stone", 1, ServerText(ServerSpan("Stone")))
    context.runOnClient<Nothing> {
        Hud.show("under", HudGroup("under", 30, 30), hud("") { items(listOf(dirt)) })
        Hud.show("over", HudGroup("over", 30, 30), hud("") { items(listOf(stone)) })
    }
    val names = context.computeOnClient<Set<String>, Nothing> {
        val found = LinkedHashSet<String>()
        for (x in 20 until 90) for (y in 20 until 90) {
            Hud.itemAt(x.toDouble(), y.toDouble())?.hoverName?.string?.let(found::add)
        }
        found
    }
    check(names == setOf("Stone")) { "The icon under the mouse is $names, and the one drawn on top is Stone" }
}

private fun menuPaddingDoesNotRunTheFirstItem(context: ClientGameTestContext) {
    context.runOnClient<Nothing> {
        var ran = -1
        val menu = KitMenu(20, 40, 400, 300, listOf("First" to { ran = 0 }, "Second" to { ran = 1 }), {})
        val press = MouseButtonEvent(menu.x + 6.0, menu.y + 1.0, MouseButtonInfo(0, 0))
        menu.mouseClicked(press, false)
        check(ran == -1) { "A click in the menu's top padding ran item $ran" }
        val onFirst = MouseButtonEvent(menu.x + 6.0, menu.y + 4.0, MouseButtonInfo(0, 0))
        menu.mouseClicked(onFirst, false)
        check(ran == 0) { "A click on the first item ran $ran" }
    }
}

private fun subsectionLiftsUnderTheMouse(context: ClientGameTestContext) {
    context.setScreen {
        object : Screen(Component.literal("nav")) {
            val tree = KitNavTree(
                8, 8, 140, 80,
                NavTree(listOf(1)).also { it.select(0) },
                listOf("Section"),
                listOf(0x55FF55),
                listOf(listOf("Subsection")),
                {}, { _, _ -> }, {}, { _, _ -> },
            )

            override fun init() {
                addRenderableWidget(tree)
            }

            override fun extractRenderState(graphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, partialTick: Float) {
                Kit.side(graphics, 0, 0, width, height)
                super.extractRenderState(graphics, mouseX, mouseY, partialTick)
            }
        }
    }
    context.waitTick()
    val at = context.computeOnClient<Pair<Int, Int>, Nothing> {
        val tree = checkNotNull(it.gui.screen()).widgets<KitNavTree>().single() as AbstractWidget
        tree.x + 4 to tree.y + 16 + 6
    }
    context.point(at.first, at.second)
    val pixel = context.guiPixel(context.takeScreenshot("nav-subsection-hover"), at.first, at.second)
    val side = context.computeOnClient<Int, Nothing> { Kit.theme.side }
    check(distance(pixel, side) > 12) { "Hover left the subsection on the side color ${hex(side)}, pixel ${hex(pixel)}" }
    context.setScreen { null }
}

private fun hex(rgb: Int) = "#${(rgb and 0xFFFFFF).toString(16).padStart(6, '0')}"

private fun rightClickDeleteSurvivesTheDrag(context: ClientGameTestContext) {
    context.runOnClient<Nothing> {
        val slider = KitRangeSlider(10, 10, 200, "Hours", 100, emptyList(), listOf(Section(10, 40))) {}
        val x = 10.0 + 1 + 20.0 * 198 / 100
        slider.mouseClicked(MouseButtonEvent(x, 16.0, MouseButtonInfo(0, 0)), false)
        slider.mouseClicked(MouseButtonEvent(x, 16.0, MouseButtonInfo(1, 0)), false)
        slider.mouseDragged(MouseButtonEvent(x + 25, 16.0, MouseButtonInfo(0, 0)), 25.0, 0.0)
        check(slider.sections.isEmpty()) { "The drag put the deleted section back: ${slider.sections}" }
    }
}

private fun profileShowsThePetAndScrollsStorage(context: ClientGameTestContext) {
    val body = checkNotNull(HudUiFixesTest::class.java.getResource("/profiles/profile.json")).readText()
    val parsed = Profile.parse(body)
    val pages = (0 until 30).map { i ->
        ContainerSummary("storage:$i", ServerText(ServerSpan("Storage ${i + 1}")), 9, 9, false)
    }
    val profile = parsed.copy(containers = parsed.containers.filter { it.kind != "storage" } + pages)
    val gem = DrawnItem(material = "minecraft:diamond", name = ServerText(ServerSpan("Gem")), rarity = "EPIC")
    val page = Container("storage:0", ServerText(ServerSpan("Storage 1")), 9, 9, false, listOf(gem))
    context.setScreen {
        val lookup = ProfileLookup("Testwyrm", object : ProfileSource {
            override fun profile(name: String, profileId: String?) = CompletableFuture.completedFuture(profile)
            override fun container(snapshot: String, id: String) = CompletableFuture.supplyAsync { page.copy(id = id) }
            override fun containers(snapshot: String) = CompletableFuture.supplyAsync { listOf(page) }
        }) { Minecraft.getInstance().execute(it) }
        ProfileScreen(lookup, null)
    }
    context.waitTicks(3)
    context.runOnClient<Nothing> { client ->
        val screen = client.gui.screen() as ProfileScreen
        val pet = profile.pets.first { it.active }
        val method = ProfileScreen::class.java.declaredMethods.first { it.name == "petRows" }
        method.isAccessible = true
        val rows = method.invoke(screen, pet) as List<*>
        val text = rows.joinToString("\n") { row ->
            val name = row!!.javaClass.getDeclaredMethod("getName").apply { isAccessible = true }.invoke(row) as (Int) -> Component
            name(160).string
        }
        check("Gold's Power" in text && "Combat Pet" in text) { "The active pet table shows:\n$text" }
    }

    context.runOnClient<Nothing> { client ->
        val screen = client.gui.screen() as ProfileScreen
        val lookup = ProfileScreen::class.java.getDeclaredField("lookup").apply { isAccessible = true }.get(screen) as ProfileLookup
        @Suppress("UNCHECKED_CAST")
        val containers = ProfileLookup::class.java.getDeclaredField("containers").apply { isAccessible = true }.get(lookup) as MutableMap<String, Load<Container>>
        containers["storage:0"] = Load.Loaded(page)
    }
    context.click(context.computeOnClient<AbstractWidget, Nothing> { client ->
        (client.gui.screen() as ProfileScreen).widgets<KitTab>().first { it.message.string == "Storage" } as AbstractWidget
    })
    context.waitTicks(2)
    val report = context.computeOnClient<String, Nothing> { client ->
        val screen = client.gui.screen() as ProfileScreen
        val pages = screen.widgets<io.github.noxaur.dragen.edge.ui.KitButton>().map { it.message.string }
        val height = ProfileScreen::class.java.getDeclaredField("contentHeight").apply { isAccessible = true }.getInt(screen)
        "overflows=${screen.overflows} pages=${pages.size} contentHeight=$height"
    }
    check(report.startsWith("overflows=true")) { "Thirty storage pages draw their contents below the panel, and the tab does not scroll ($report)" }
    context.setScreen { null }
}

private fun trimmedChatCopiesTheRowsStillShown(context: ClientGameTestContext, copies: ArrayList<String>) {
    context.runOnClient<Nothing> { client ->
        val chat = client.gui.hud.chat
        chat.clearMessages(false)
        chat.addClientSystemMessage(Component.literal("DROPPED HEADER\nKept middle\nKept footer"))
        repeat(98) { chat.addClientSystemMessage(Component.literal("pad $it")) }
        client.gui.setScreen(ChatScreen("", false))
        repeat(120) { chat.scrollChat(1) }
    }
    context.waitForScreen(ChatScreen::class.java)
    context.waitTick()
    val row = context.computeOnClient<Int, Nothing> { it.window.guiScaledHeight - 40 - (it.gui.hud.chat.linesPerPage - 1) * 9 - 4 }
    context.click(40, row, button = 1)
    check(CopyChats.menu?.header == "Copy Line") { "The remaining row opened ${CopyChats.menu?.header}" }
    val menu = checkNotNull(CopyChats.menu)
    context.click(menu.buttonX(Format.TEXT) + 4, menu.buttonY + 4)
    check(copies.singleOrNull()?.contains("DROPPED HEADER") != true) { "The copy included the trimmed row: $copies" }
    check(copies.singleOrNull() == "Kept middle") { "The copy was $copies" }
}
