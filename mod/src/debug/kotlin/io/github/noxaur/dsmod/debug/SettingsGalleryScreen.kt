package io.github.noxaur.dsmod.debug

import io.github.noxaur.dsmod.core.config.DsModConfig
import io.github.noxaur.dsmod.core.config.Bind
import io.github.noxaur.dsmod.core.config.Canvas
import io.github.noxaur.dsmod.core.config.Grouped
import io.github.noxaur.dsmod.core.config.Kind
import io.github.noxaur.dsmod.core.config.Pick
import io.github.noxaur.dsmod.core.config.Rows
import io.github.noxaur.dsmod.core.config.Setting
import io.github.noxaur.dsmod.core.config.Store
import io.github.noxaur.dsmod.core.config.Stored
import io.github.noxaur.dsmod.core.config.SettingsTree
import io.github.noxaur.dsmod.core.config.Switch
import io.github.noxaur.dsmod.core.container.MarkStyle
import io.github.noxaur.dsmod.core.container.SlotStyle
import io.github.noxaur.dsmod.core.feature.general.EquipmentPiece
import io.github.noxaur.dsmod.core.input.CommandBind
import io.github.noxaur.dsmod.core.input.CommandBinds
import io.github.noxaur.dsmod.core.input.Where
import io.github.noxaur.dsmod.core.text.text
import io.github.noxaur.dsmod.core.ui.Look
import io.github.noxaur.dsmod.core.ui.RecentColors
import io.github.noxaur.dsmod.core.ui.Section
import io.github.noxaur.dsmod.core.ui.TabLayout
import io.github.noxaur.dsmod.core.ui.SettingsSearch
import io.github.noxaur.dsmod.core.ui.Theme
import io.github.noxaur.dsmod.edge.toComponent
import io.github.noxaur.dsmod.edge.ui.Kit
import io.github.noxaur.dsmod.edge.ui.SettingRows
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.input.CharacterEvent
import net.minecraft.client.input.KeyEvent
import net.minecraft.client.input.MouseButtonEvent
import org.lwjgl.glfw.GLFW
import kotlin.reflect.KClass

private const val WIDTH = 620
private const val HEIGHT = 420
private const val COLUMN = 198
private const val HEADING = 12

class SettingsGalleryScreen(private val hud: DsModConfig.Hud, private val page: Page = Page.ROWS) : Screen(text { body(page.title) }.toComponent()) {
    enum class Page(val title: String) {
        ROWS("Settings Rows"),
        LISTS("Settings Lists"),
        PANELS("Settings Panels"),
    }

    private class Column(val width: Int, val parts: List<Pair<String, List<Setting>>>)

    private class Values {
        private val s = Stored(Store.GENERAL)

        val on = s.slot("on", true)
        val off = s.slot("off", false)
        val feature = s.slot("feature", true)
        val open = s.slot("open", true)
        val heading = s.slot("heading", true)
        val distance = s.slot("distance", 4f)
        val tilt = s.slot("tilt", -12f)
        val color = s.slot("color", 0xFF55FFFF.toInt())
        val tint = s.slot("tint", 0xFFFFAA00.toInt())
        val dimColor = s.slot("dimColor", 0xFF55FF55.toInt())
        val recent = s.slot("recent", emptyList<Int>())
        val tier = s.slot("tier", Tier.EPIC)
        val views = s.slot("views", Tier.RARE)
        val preset = s.slot("preset", MarkStyle.FRAME)
        val theme = s.slot("theme", Theme.VANILLA)
        val key = s.slot("key", 342)
        val none = s.slot("none", 75)
        val spans = s.slot("spans", listOf(Section(150, 160), Section(175, 200)))
        val sparkled = s.slot("sparkled", MarkStyle.AURA)
        val gate = s.slot("gate", false)
        val dimNumber = s.slot("dimNumber", 4f)
        val dimTier = s.slot("dimTier", Tier.EPIC)
        val dimKey = s.slot("dimKey", 76)
        val dimSpans = s.slot("dimSpans", listOf(Section(175, 200)))
        val dimPreset = s.slot("dimPreset", MarkStyle.AURA)
        val foundPreset = s.slot("foundPreset", MarkStyle.FRAME)
        val foundSparkles = s.slot("foundSparkles", false)
        val rows = s.slot("rows", listOf(Row.TOP, Row.HOTBAR))
        val dimPieces = s.slot("dimPieces", listOf(EquipmentPiece.HELMET, EquipmentPiece.PET))
        var binds = Grouped(
            sampleBinds("/sbmenu") + listOf(
                CommandBind("Pets", "/pets", listOf(GLFW.GLFW_KEY_E)),
                CommandBind("Hub", "/warp hub", listOf(GLFW.GLFW_KEY_LEFT_SHIFT, GLFW.GLFW_KEY_F6), group = "Warps"),
                CommandBind("Dragon", "/warp drag", emptyList(), enabled = false, group = "Warps"),
                friendBind(),
            ),
            listOf("Warps", "Dungeons", "From a Friend"),
            folded = listOf("From a Friend"),
            off = listOf("Dungeons"),
        )
        var noBinds = Grouped<CommandBind>()
        var dimBinds = Grouped(listOf(CommandBind("End", "/warp end", listOf(GLFW.GLFW_KEY_F6))))
        val tabs = s.slot("tabs", TabLayout.DEFAULT)
        val dimTabs = s.slot("dimTabs", TabLayout.DEFAULT)
    }

    private enum class Tier(private val label: String) {
        RARE("Rare"),
        EPIC("Epic"),
        RNGESUS("RNGesus"),
        ;

        override fun toString() = label
    }

    private enum class Row(private val label: String) {
        TOP("Top Row"),
        HOTBAR("Hotbar"),
        ;

        override fun toString() = label
    }

    private class Well(val x: Int, val y: Int, val width: Int, val height: Int, val caption: String)

    private val v = Values()
    private val gate = Switch("gate", "Feature", "Turn it on.", v.gate.bind)
    private val heading = Switch("heading", "Heading", "Turn the group on.", v.heading.bind)

    private val rowsPage: List<List<Pair<String, List<Setting>>>> = listOf(
        listOf(
            "Switch: on, changed, with settings" to rows {
                switch(v.on.bind, "Dragon Timer", "Show when the egg returns.")
                switch(v.off.bind, "Fullbright", "Light the whole world.")
                switch(v.feature.bind, "Slot Lock", "Lock slots and items.") { keys(v.key.bind, "Slot Key", "Lock the slot.") }
                switch(v.open.bind, "Protector Spawn Beam", "Show a beam.", id = "openSwitch") { keys(v.none.bind, "Item Key", "Lock the item.") }
            },
            "Number: from the minimum, signed" to rows {
                number(v.distance.bind, "Distance", "Set the distance.", 1f..50f, step = 0.5f)
                number(v.tilt.bind, "Tilt", "Angle the camera.", -45f..45f, step = 1f, unit = "°")
            },
            "Color: idle, changed and see-through" to rows {
                color(v.color.bind, "Beam Color", "Pick a color.")
                color(v.tint.bind, "Tint", "Pick the tint.")
            },
        ),
        listOf(
            "Choice and pick: idle, changed" to rows {
                choice(v.tier.bind, "Alert Tier", "Pick the lowest tier.", Tier.entries)
                choice(v.views.bind, "F5 Views", "Pick the views.", Tier.entries)
                pick(v.preset.bind, "Style", "Pick a look.", MarkStyle.entries) { slotLook(it) }
                pick(v.theme.bind, "Theme", "Pick the colors.", Theme.entries) { Look.Swatch(it) }
            },
            "Keys: set, cleared" to rows {
                keys(v.key.bind, "Key", "Pick the key.", id = "galleryKey")
                keys(v.none.bind, "None", "Pick the key.", id = "galleryNone")
            },
            "Dimmed too" to rows {
                span(v.dimSpans.bind, "Timing", "Pick it.", max = 200, dependsOn = gate)
                pick(v.dimPreset.bind, "Style", "Pick it.", MarkStyle.entries, dependsOn = gate) { slotLook(it) }
                color(v.dimColor.bind, "Color", "Pick it.", dependsOn = gate)
                action("noop", "Edit Binds", "Edit them.", button = "Edit", dependsOn = gate) {}
            },
        ),
        listOf(
            "Span, fold, action, order" to rows {
                span(v.spans.bind, "Beam Timing", "Pick when it shows.", max = 200, ticks = listOf(50, 100, 150))
                fold("openFold", "Open Fold", "Build your own.") {
                    pick(v.sparkled.bind, "Sparkled", "Pick the shape.", MarkStyle.entries) {
                        Look.Slots(SlotStyle(it, opacity = 60, sparkles = true), 1)
                    }
                }
                action("noop", "Move HUD", "Drag a block.", button = "Move") {}
                order(v.rows.bind, "Rows", "Order them.", Row.entries, id = "openOrder")
            },
            "Dimmed under an off switch" to rows {
                number(v.dimNumber.bind, "Distance", "Set it.", 1f..50f, step = 0.5f, dependsOn = gate)
                choice(v.dimTier.bind, "Tier", "Pick it.", Tier.entries, dependsOn = gate)
                keys(v.dimKey.bind, "Key", "Pick it.", dependsOn = gate)
                order(v.dimPieces.bind, "Pieces", "Pick them.", EquipmentPiece.entries, id = "dimOrder", dependsOn = gate)
            },
        ),
    )

    private fun slotLook(mark: MarkStyle) = Look.Slots(SlotStyle(mark))

    private val listsPage = listOf(
        Column(
            360,
            listOf(
                "Items: groups, a folded one, an off one, clashes" to rows {
                    items(Bind("General.binds", { v.binds }, { v.binds = it }), "Binds", "Name each bind, pick its keys and choose where it works.", CommandBinds, id = "galleryBinds")
                },
            ),
        ),
        Column(
            244,
            listOf(
                "Items: empty" to rows { items(Bind("General.noBinds", { v.noBinds }, { v.noBinds = it }), "Binds", "Add one.", CommandBinds, id = "noBinds") },
                "Items: dimmed" to rows { items(Bind("General.dimBinds", { v.dimBinds }, { v.dimBinds = it }), "Binds", "Add one.", CommandBinds, id = "dimBinds", dependsOn = gate) },
            ),
        ),
    )

    private val panelsPage = listOf(
        Column(
            COLUMN,
            listOf(
                "Panel: menu tabs, changed" to rows {
                    panel(v.tabs.bind, "Tabs", "Press a place to add a tab.", Canvas.MENU_TABS, id = "galleryTabs") { "${it.size} tabs" }
                },
            ),
        ),
        Column(
            COLUMN,
            listOf(
                "Panel: the HUD's layout" to rows {
                    panel(hud.positions.bind, "Layout", "Drag a block.", Canvas.HUD_LAYOUT, id = "galleryLayout") {
                        "${it.size} moved"
                    }
                    action("galleryMove", "Move HUD", "Move it over the game.", button = "Move") {}
                },
            ),
        ),
        Column(
            COLUMN,
            listOf(
                "Panel: dimmed" to rows {
                    panel(v.dimTabs.bind, "Tabs", "Press a place.", Canvas.MENU_TABS, id = "dimTabs", dependsOn = gate) { "${it.size} tabs" }
                },
                "Preview: the player's model" to rows {
                    preview("galleryPreview", "Preview", "Drag the model to turn it.", Canvas.PLAYER_MODEL)
                },
            ),
        ),
    )

    private val columns: List<Column> = when (page) {
        Page.ROWS -> rowsPage.map { Column(COLUMN, it) }
        Page.LISTS -> listsPage
        Page.PANELS -> panelsPage
    }

    private val all = columns.flatMap { it.parts }.flatMap { (_, settings) -> settings.flatMap { it.walk().toList() } } + heading + gate

    val kinds: Set<Kind> = all.map { it.kind }.toSet()

    val looks: Set<KClass<out Look>> = all.filterIsInstance<Pick<*>>().flatMap { pick -> pick.values.indices.map { pick.lookAt(it)::class } }.toSet()

    private val opened = hashSetOf("openSwitch", "openFold", "openOrder")
    val settingRows = SettingRows(opened, ::layout, { _, _ -> }, recent = RecentColors(v.recent.bind))

    private val found = "Found by a search: frame look" to rows {
        pick(v.foundPreset.bind, "Style", "Pick a look for the marks.", MarkStyle.entries, id = "foundStyle") { slotLook(it) }
        fold("foundFold", "Hotbar Look", "Give the hotbar its own look.") { switch(v.foundSparkles.bind, "Sparkles", "Add glints.", id = "foundSparkles") }
    }
    private val foundRows = SettingRows(HashSet(), ::layout, { _, _ -> }, recent = RecentColors(v.recent.bind))
    private val wells = ArrayList<Well>()
    private var ox = 0
    private var oy = 0


    private fun rows(block: Rows.() -> Unit) = Rows().apply(block).rows.also { SettingsTree.link(it, null) }

    init {
        v.off.value = true
        v.views.value = Tier.RNGESUS
        v.theme.value = Theme.CHARCOAL
        v.tilt.value = 12f
        v.tint.value = 0x80FF5555.toInt()
        v.none.value = -1
        v.foundSparkles.value = true
        v.rows.value = listOf(Row.TOP)
        v.tabs.value = TabLayout.DEFAULT.drop(1)
    }

    override fun init() {
        ox = (width - WIDTH) / 2
        oy = (height - HEIGHT) / 2
        layout()
    }

    private fun layout() {
        wells.clear()
        settingRows.begin()
        var x = ox + Kit.INSET
        for ((c, column) in columns.withIndex()) {
            var y = oy + Kit.INSET
            for ((caption, settings) in column.parts) {
                if (c == 0 && y == oy + Kit.INSET && page == Page.ROWS) settingRows.heading(heading, x + font.width(caption) + Kit.GAP, y, x)
                y += HEADING
                val bottom = settingRows.add(settings, x + 1, y + 1, column.width - 2)
                wells += Well(x, y, column.width, bottom - y + 1, caption)
                y = bottom + 1 + Kit.GAP * 2
            }
            if (c == 0 && page == Page.ROWS) {
                foundRows.begin()
                foundRows.words = SettingsSearch.words("frame look")
                y += HEADING
                val bottom = foundRows.add(found.second, x + 1, y + 1, COLUMN - 2)
                wells += Well(x, y, COLUMN, bottom - y + 1, found.first)
                foundRows.end()
            }
            x += column.width + Kit.GAP * 2
        }
        settingRows.end()
    }

    override fun mouseClicked(event: MouseButtonEvent, doubleClick: Boolean) = settingRows.mouseClicked(event, doubleClick) || super.mouseClicked(event, doubleClick)

    override fun mouseDragged(event: MouseButtonEvent, dx: Double, dy: Double) = settingRows.mouseDragged(event, dx, dy) || super.mouseDragged(event, dx, dy)

    override fun mouseReleased(event: MouseButtonEvent) = settingRows.mouseReleased(event) || super.mouseReleased(event)

    override fun mouseScrolled(mouseX: Double, mouseY: Double, scrollX: Double, scrollY: Double) =
        settingRows.mouseScrolled(mouseX, mouseY, scrollY, minecraft.hasShiftDown()) || super.mouseScrolled(mouseX, mouseY, scrollX, scrollY)

    override fun keyPressed(event: KeyEvent) = settingRows.keyPressed(event) || super.keyPressed(event)

    override fun keyReleased(event: KeyEvent) = settingRows.keyReleased(event) || super.keyReleased(event)

    override fun charTyped(event: CharacterEvent) = settingRows.charTyped(event) || super.charTyped(event)

    override fun removed() = settingRows.dispose()

    override fun extractRenderState(graphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, partialTick: Float) {
        settingRows.view(0, 0, height, width, height)
        Kit.panel(graphics, ox, oy, WIDTH, HEIGHT)
        for (well in wells) {
            Kit.label(graphics, well.caption, well.x, well.y - HEADING + 2, Kit.Ink.MUTED)
            Kit.well(graphics, well.x, well.y, well.width, well.height)
        }
        val tip = settingRows.draw(graphics, mouseX, mouseY, partialTick)
        if (page == Page.ROWS) {
            foundRows.view(0, 0, height, width, height)
            foundRows.draw(graphics, mouseX, mouseY, partialTick)
        }
        super.extractRenderState(graphics, mouseX, mouseY, partialTick)
        settingRows.drawMenu(graphics, mouseX, mouseY, partialTick)
        tip?.let { graphics.setTooltipForNextFrame(font, it.lines, mouseX, mouseY) }
    }
}

fun sampleBinds(menuCommand: String) = listOf(
    CommandBind("Warp End", "/warp end", listOf(GLFW.GLFW_KEY_LEFT_SHIFT, GLFW.GLFW_KEY_F6)),
    CommandBind("Stash", "/viewstash", listOf(GLFW.GLFW_KEY_F7), where = Where.BOTH),
    CommandBind("", menuCommand, listOf(GLFW.GLFW_KEY_F8, GLFW.GLFW_KEY_F9), inOrder = true),
)

fun friendBind() = CommandBind("Shared", "/warp end", listOf(GLFW.GLFW_KEY_KP_1), group = "From a Friend", enabled = false)
