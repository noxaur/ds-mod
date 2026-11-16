package io.github.noxaur.dsmod.debug

import io.github.noxaur.dsmod.core.config.Bind
import io.github.noxaur.dsmod.core.config.Canvas
import io.github.noxaur.dsmod.core.config.Grouped
import io.github.noxaur.dsmod.core.config.Kind
import io.github.noxaur.dsmod.core.config.Pick
import io.github.noxaur.dsmod.core.config.Rows
import io.github.noxaur.dsmod.core.config.Setting
import io.github.noxaur.dsmod.core.config.SettingsTree
import io.github.noxaur.dsmod.core.config.Switch
import io.github.noxaur.dsmod.core.container.Border
import io.github.noxaur.dsmod.core.container.RarityPreset
import io.github.noxaur.dsmod.core.container.SlotStyle
import io.github.noxaur.dsmod.core.feature.general.EquipmentPiece
import io.github.noxaur.dsmod.core.input.CommandBind
import io.github.noxaur.dsmod.core.input.CommandBinds
import io.github.noxaur.dsmod.core.input.Where
import io.github.noxaur.dsmod.core.text.text
import io.github.noxaur.dsmod.core.ui.Look
import io.github.noxaur.dsmod.core.ui.Section
import io.github.noxaur.dsmod.core.ui.TabLayout
import io.github.noxaur.dsmod.core.ui.SettingsSearch
import io.github.noxaur.dsmod.core.ui.Theme
import io.github.noxaur.dsmod.edge.DsModClient
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
private const val HEIGHT = 352
private const val COLUMN = 198
private const val HEADING = 12

/**
 * The kit gallery's pages of settings rows: every [Kind] of row, idle, changed and open, and each again dimmed under
 * an off switch, with every [Look] a pick shows. `/dskit rows` opens the rows, `/dskit lists` a list the player
 * builds, full, empty and dimmed, and `/dskit panels` each panel. The rows work, so a press opens a menu. [kinds]
 * and [looks] say what a page shows, and `KitTest` fails on a kind or a look that no page shows.
 */
class SettingsGalleryScreen(private val page: Page = Page.ROWS) : Screen(text { body(page.title) }.toComponent()) {
    enum class Page(val title: String) {
        ROWS("Settings Rows"),
        LISTS("Settings Lists"),
        PANELS("Settings Panels"),
    }

    private class Column(val width: Int, val parts: List<Pair<String, List<Setting>>>)

    private class Values {
        var on = true
        var off = false
        var feature = true
        var open = true
        var heading = true
        var distance = 4f
        var tilt = -12f
        var tier = Tier.EPIC
        var views = Tier.RARE
        var preset = RarityPreset.GILDED
        var theme = Theme.VANILLA
        var key = 342
        var none = 75
        var spans = listOf(Section(150, 160), Section(175, 200))
        var border = Border.GILDED
        var gate = false
        var dimNumber = 4f
        var dimTier = Tier.EPIC
        var dimKey = 76
        var dimSpans = listOf(Section(175, 200))
        var dimPreset = RarityPreset.SHOWCASE
        var foundPreset = RarityPreset.GILDED
        var foundGem = false
        var rows = mutableListOf(Row.TOP, Row.HOTBAR)
        var dimPieces = mutableListOf(EquipmentPiece.HELMET, EquipmentPiece.PET)
        var binds = Grouped(
            listOf(
                CommandBind("Warp End", "/warp end", listOf(GLFW.GLFW_KEY_LEFT_SHIFT, GLFW.GLFW_KEY_F6)),
                CommandBind("Stash", "/viewstash", listOf(GLFW.GLFW_KEY_F7), where = Where.BOTH),
                CommandBind("", "/sbmenu", listOf(GLFW.GLFW_KEY_F8, GLFW.GLFW_KEY_F9), inOrder = true),
                CommandBind("Pets", "/pets", listOf(GLFW.GLFW_KEY_E)),
                CommandBind("Hub", "/warp hub", listOf(GLFW.GLFW_KEY_LEFT_SHIFT, GLFW.GLFW_KEY_F6), group = "Warps"),
                CommandBind("Dragon", "/warp drag", emptyList(), enabled = false, group = "Warps"),
                CommandBind("Shared", "/warp end", listOf(GLFW.GLFW_KEY_KP_1), group = "From a Friend", enabled = false),
            ),
            listOf("Warps", "Dungeons", "From a Friend"),
            folded = listOf("From a Friend"),
            off = listOf("Dungeons"),
        )
        var noBinds = Grouped<CommandBind>()
        var dimBinds = Grouped(listOf(CommandBind("End", "/warp end", listOf(GLFW.GLFW_KEY_F6))))
        var tabs = TabLayout.DEFAULT
        var dimTabs = TabLayout.DEFAULT
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
    private val gate = Switch("gate", "Feature", "Turn it on.", Bind.of(v::gate))
    private val heading = Switch("heading", "Heading", "Turn the group on.", Bind.of(v::heading))

    private val rowsPage: List<List<Pair<String, List<Setting>>>> = listOf(
        listOf(
            "Switch: on, changed, with settings" to rows {
                switch(v::on, "Dragon Timer", "Show when the egg returns.")
                switch(v::off, "Fullbright", "Light the whole world.")
                switch(v::feature, "Slot Lock", "Lock slots and items.") { keys(v::key, "Slot Key", "Lock the slot.") }
                switch(v::open, "Boss Beacon", "Show a beam.", id = "openSwitch") { keys(v::none, "Item Key", "Lock the item.") }
            },
            "Number: from the minimum, signed" to rows {
                number(v::distance, "Distance", "Set the distance.", 1f..50f, step = 0.5f)
                number(v::tilt, "Tilt", "Angle the camera.", -45f..45f, step = 1f, unit = "°")
            },
        ),
        listOf(
            "Choice and pick: idle, changed" to rows {
                choice(v::tier, "Alert Tier", "Pick the lowest tier.")
                choice(v::views, "F5 Views", "Pick the views.")
                pick(v::preset, "Style", "Pick a look.") { Look.Slots(it.style ?: SlotStyle()) }
                pick(v::theme, "Theme", "Pick the colors.") { Look.Swatch(it) }
            },
            "Keys: set, cleared" to rows {
                keys(v::key, "Key", "Pick the key.", id = "galleryKey")
                keys(v::none, "None", "Pick the key.", id = "galleryNone")
            },
            "Dimmed too" to rows {
                span(v::dimSpans, "Timing", "Pick it.", max = 200, dependsOn = gate)
                pick(Bind("Values.dimPreset", v::dimPreset::get, v::dimPreset::set), "Style", "Pick it.", RarityPreset.entries, dependsOn = gate) {
                    Look.Slots(it.style ?: SlotStyle())
                }
                action(::noop, "Edit Binds", "Edit them.", button = "Edit", dependsOn = gate)
            },
        ),
        listOf(
            "Span, fold, action, order" to rows {
                span(v::spans, "Beam Timing", "Pick when it shows.", max = 200, ticks = listOf(50, 100, 150))
                fold("openFold", "Open Fold", "Build your own.") {
                    pick(Bind("Values.border", v::border::get, v::border::set), "Border", "Pick the ring.", Border.entries, label = { it.label }) {
                        Look.Slots(SlotStyle(border = it), 1)
                    }
                }
                action(::noop, "Move HUD", "Drag a block.", button = "Move")
                order(v::rows, "Rows", "Order them.", id = "openOrder")
            },
            "Dimmed under an off switch" to rows {
                number(v::dimNumber, "Distance", "Set it.", 1f..50f, step = 0.5f, dependsOn = gate)
                choice(v::dimTier, "Tier", "Pick it.", dependsOn = gate)
                keys(v::dimKey, "Key", "Pick it.", dependsOn = gate)
                order(v::dimPieces, "Pieces", "Pick them.", id = "dimOrder", dependsOn = gate)
            },
        ),
    )

    private val noop = Runnable {}

    private fun binds(property: kotlin.reflect.KMutableProperty0<Grouped<CommandBind>>) = Bind("Values.${property.name}", property::get, property::set)

    private val listsPage = listOf(
        Column(
            360,
            listOf(
                "Items: groups, a folded one, an off one, clashes" to rows {
                    items(binds(v::binds), "Binds", "Name each bind, pick its keys and choose where it works.", CommandBinds, id = "galleryBinds")
                },
            ),
        ),
        Column(
            244,
            listOf(
                "Items: empty" to rows { items(binds(v::noBinds), "Binds", "Add one.", CommandBinds, id = "noBinds") },
                "Items: dimmed" to rows { items(binds(v::dimBinds), "Binds", "Add one.", CommandBinds, id = "dimBinds", dependsOn = gate) },
            ),
        ),
    )

    private val panelsPage = listOf(
        Column(
            COLUMN,
            listOf(
                "Panel: menu tabs, changed" to rows {
                    panel(Bind("Values.tabs", v::tabs::get, v::tabs::set), "Tabs", "Press a place to add a tab.", Canvas.MENU_TABS, id = "galleryTabs") { "${it.size} tabs" }
                },
            ),
        ),
        Column(
            COLUMN,
            listOf(
                "Panel: the HUD's layout" to rows {
                    val hud = DsModClient.settings.hud
                    panel(Bind("Hud.positions", { HashMap(hud.positions) }, { hud.positions.clear(); hud.positions.putAll(it) }), "Layout", "Drag a block.", Canvas.HUD_LAYOUT, id = "galleryLayout") {
                        "${it.size} moved"
                    }
                    action(::noop, "Move HUD", "Move it over the game.", button = "Move", id = "galleryMove")
                },
            ),
        ),
        Column(
            COLUMN,
            listOf(
                "Panel: dimmed" to rows {
                    panel(Bind("Values.dimTabs", v::dimTabs::get, v::dimTabs::set), "Tabs", "Press a place.", Canvas.MENU_TABS, id = "dimTabs", dependsOn = gate) { "${it.size} tabs" }
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

    /** The kinds of row the page shows. */
    val kinds: Set<Kind> = all.map { it.kind }.toSet()

    /** The kinds of look the page's picks show. */
    val looks: Set<KClass<out Look>> = all.filterIsInstance<Pick<*>>().flatMap { pick -> pick.values.indices.map { pick.lookAt(it)::class } }.toSet()

    private val opened = hashSetOf("openSwitch", "openFold", "openOrder")
    private val settingRows = SettingRows(opened, ::layout, { _, _ -> })

    /** Rows as a search for "gilded look" shows them, with the words it matched painted, drawn apart from the rows above. */
    private val found = "Found by a search: gilded look" to rows {
        pick(Bind("Values.foundPreset", v::foundPreset::get, v::foundPreset::set), "Style", "Pick a ready-made look.", RarityPreset.entries, id = "foundStyle") {
            Look.Slots(it.style ?: SlotStyle())
        }
        fold("foundFold", "Custom Look", "Build your own look.") { switch(v::foundGem, "Gem", "Put a gem.", id = "foundGem") }
    }
    private val foundRows = SettingRows(HashSet(), ::layout, { _, _ -> })
    private val wells = ArrayList<Well>()
    private var ox = 0
    private var oy = 0

    /** Where the row [id] is, so a test can press it. */
    fun spot(id: String) = settingRows.spot(id)

    private fun rows(block: Rows.() -> Unit) = Rows().apply(block).rows.also { SettingsTree.link(it, null) }

    init {
        // The changed variants, against the defaults the rows took when they were made.
        v.off = true
        v.views = Tier.RNGESUS
        v.theme = Theme.CHARCOAL
        v.tilt = 12f
        v.none = -1
        v.foundGem = true
        v.rows = mutableListOf(Row.TOP)
        v.tabs = TabLayout.DEFAULT.drop(1)
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
                foundRows.words = SettingsSearch.words("gilded look")
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
