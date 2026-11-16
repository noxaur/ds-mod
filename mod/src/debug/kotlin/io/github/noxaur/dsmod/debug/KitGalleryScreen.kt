package io.github.noxaur.dsmod.debug

import io.github.noxaur.dsmod.core.text.Role
import io.github.noxaur.dsmod.core.text.TextBuilder
import io.github.noxaur.dsmod.core.text.text
import io.github.noxaur.dsmod.core.ui.Section
import io.github.noxaur.dsmod.core.ui.TabEdge
import io.github.noxaur.dsmod.edge.DsModClient
import io.github.noxaur.dsmod.edge.SlotArts
import io.github.noxaur.dsmod.edge.Surface
import io.github.noxaur.dsmod.edge.toComponent
import io.github.noxaur.dsmod.edge.ui.Icon
import io.github.noxaur.dsmod.edge.ui.Kit
import io.github.noxaur.dsmod.edge.ui.KitButton
import io.github.noxaur.dsmod.edge.ui.KitCheckbox
import io.github.noxaur.dsmod.edge.ui.KitCycleButton
import io.github.noxaur.dsmod.edge.ui.KitKeysButton
import io.github.noxaur.dsmod.edge.ui.KitMenu
import io.github.noxaur.dsmod.edge.ui.KitRangeSlider
import io.github.noxaur.dsmod.edge.ui.KitStepper
import io.github.noxaur.dsmod.edge.ui.KitTextField
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.input.KeyEvent
import net.minecraft.network.chat.Component
import net.minecraft.world.item.Item
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import org.lwjgl.glfw.GLFW

private const val WIDTH = 575
private const val HEIGHT = 255
private const val MIDDLE = 271
private const val RIGHT = 396

/** Every shape, state, widget and icon of the kit on one screen, in the player's theme. `/dskit` opens it. */
class KitGalleryScreen : Screen(text { body("Kit") }.toComponent()) {
    private class Line(val icon: Icon, val color: Int, val depth: Int, val text: Component)
    private class Mark(val item: ItemStack, val role: Role, val color: Int, val style: Char)

    private var ox = 0
    private var oy = 0
    private val states = Kit.State.entries
    private val stateWidths = states.map { font.width(name(it)) }
    private val lines = listOf(
        line(Icon.CROSS, Role.BAD, 0) { bad("0/1 "); value("Dragon Blade") },
        line(Icon.CROSS, Role.BAD, 1) { bad("0/8 "); body("Refined Scale") },
        line(Icon.CHECK, Role.GOOD, 2) { good("256/256 "); body("Lapis Block") },
        line(Icon.CROSS, Role.BAD, 2) { bad("115/640 "); body("Scale") },
        line(Icon.CROSS, Role.BAD, 3) { bad("22k/51k "); body("Shard") },
        line(Icon.CROSS, Role.BAD, 1) { bad("0/3 "); value("Null Blade") },
        line(Icon.WARN, Role.WARNING, 2) { warning("158/192 "); body("Quartz") },
        line(Icon.CHECK, Role.GOOD, 2) { good("1,728/1,728 "); body("Mithril") },
        line(Icon.CHECK, Role.GOOD, 1) { good("128/128 "); muted("Ender Silk") },
        line(Icon.CROSS, Role.BAD, 1) { bad("0/1 "); action("Click to craft.") },
    )
    private val marks = listOf(
        mark(Items.GOLDEN_SWORD, 1, Role.LEGENDARY, 'f'), mark(Items.ENDER_PEARL, 12, Role.EPIC, 'f'),
        mark(Items.DIAMOND, 1, Role.RARE, 'f'), mark(Items.EMERALD, 3, Role.UNCOMMON, 'f'), mark(Items.IRON_INGOT, 1, Role.COMMON, 'f'),
        mark(Items.NETHER_STAR, 1, Role.MYTHIC, ' '), mark(Items.LAPIS_LAZULI, 38, Role.RARE, ' '),
        mark(Items.BLAZE_ROD, 1, Role.LEGENDARY, 'b'), mark(Items.SLIME_BALL, 7, Role.UNCOMMON, ' '), mark(Items.PRISMARINE_SHARD, 1, Role.DIVINE, 'b'),
        mark(Items.PAPER, 20, Role.COMMON, 'd'), mark(Items.ENDER_EYE, 1, Role.EPIC, 'd'), mark(Items.QUARTZ, 9, Role.RARE, ' '), mark(Items.FEATHER, 4, Role.COMMON, 'h'),
        mark(Items.AIR, 1, Role.COMMON, ' '), mark(Items.DIAMOND_SWORD, 1, Role.LEGENDARY, 'c'),
    )
    private val tabs = listOf(Items.GOLDEN_SWORD, Items.DIAMOND, Items.ENDER_PEARL, Items.EMERALD).map(::ItemStack)
    private val hud = listOf(
        text { brand("Zealot Tracker") },
        text { muted("Kills: "); value(1204) },
        text { muted("Eyes: "); value(3); muted(" (1 in 310)") },
        text { muted("Storage: "); warning("80%") },
    ).map { it.toComponent() }
    private val tooltip = listOf(
        text { body("Ender Pearl") },
        text { muted("Stack: "); value(12) },
    ).map { it.toComponent() }

    override fun init() {
        ox = (width - WIDTH) / 2
        oy = (height - HEIGHT) / 2
        val y = oy + 51
        val done = addRenderableWidget(KitButton(ox + Kit.INSET, y, "Done", ::onClose))
        val stepper = addRenderableWidget(KitStepper(done.right + Kit.GAP, y, "Scale", 30) {})
        stepper.value = "1.5x"
        val icon = addRenderableWidget(KitButton(stepper.right + Kit.GAP, y, Icon.SEARCH, "Search") {})
        val rate = addRenderableWidget(KitCheckbox(icon.right + Kit.GAP, y + 3, "Rate", true) {})
        addRenderableWidget(KitCheckbox(rate.right + Kit.GAP * 2, y + 3, "Bar", false) {})
        // The storage window's search field, below its slot grid.
        addRenderableWidget(KitTextField(ox + MIDDLE + 7, oy + 133, 99, "Search", "", 64) {}.hint("Search..."))
        addRenderableWidget(KitRangeSlider(ox + Kit.INSET, oy + 192, 252, "Range", 200, listOf(50, 100, 150), listOf(Section(20, 70), Section(175, 200))) {})
        val field = addRenderableWidget(KitTextField(ox + Kit.INSET, oy + 226, 96, "Command", "/warp end", 256) {})
        var keyX = field.x + field.width + 4 + Kit.GAP
        val sets = listOf(listOf(GLFW.GLFW_KEY_LEFT_SHIFT, GLFW.GLFW_KEY_F6), listOf(GLFW.GLFW_KEY_F6), listOf(GLFW.GLFW_KEY_Q), listOf(GLFW.GLFW_KEY_F6, GLFW.GLFW_KEY_F7))
        for ((i, clash) in listOf(null, Role.BAD, Role.WARNING, null).withIndex()) {
            val keys = addRenderableWidget(KitKeysButton(keyX, oy + 226, 70, "Keys", sets[i], 4) {})
            keys.clash = clash
            keys.inOrder = i == 3
            keyX += 70 + Kit.GAP
        }
        // The last one records, as after a press.
        children().filterIsInstance<KitKeysButton>().last().onPress(KeyEvent(GLFW.GLFW_KEY_ENTER, 0, 0))
        addRenderableWidget(KitCycleButton(keyX, oy + 226, 70, "Where", listOf("In the World", "In Menus"), "In the World", { it }) {})
        // A context menu, as a right press beside the cycle button would open it.
        addRenderableWidget(KitMenu(keyX + 70 + Kit.GAP - 2, oy + 214, width, height, listOf("Duplicate" to {}, "Delete" to {}), {}))
    }

    override fun extractRenderState(graphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, partialTick: Float) {
        val g = graphics
        val t = Kit.theme
        Kit.panel(g, ox, oy, WIDTH, HEIGHT)
        val x = ox + Kit.INSET

        caption(g, "Buttons: idle, hover, down, off", x, oy + 7)
        for (i in states.indices) {
            Kit.button(g, x + i * 44, oy + 19, 40, Kit.BUTTON_HEIGHT, states[i], name(states[i]), stateWidths[i])
            Kit.button(g, x + 180 + i * 20, oy + 19, Kit.BUTTON_HEIGHT, Kit.BUTTON_HEIGHT, states[i], Icon.UP)
        }

        caption(g, "Widgets", x, oy + 39)

        caption(g, "Checkbox: idle, hover, down, off", x, oy + 71)
        for (i in states.indices) {
            Kit.checkbox(g, x + 1 + i * 32, oy + 84, true, states[i])
            Kit.checkbox(g, x + 15 + i * 32, oy + 84, false, states[i])
        }
        Kit.icon(g, Icon.ARROW, x + 214, oy + 81, t.ink, t.inkShadow)
        Kit.slot(g, x + 226, oy + 72, Kit.RESULT_SLOT_SIZE)
        g.item(RESULT, x + 231, oy + 77)

        caption(g, "Bars", x, oy + 99)
        Kit.bar(g, x, oy + 111, 60, 0f, Role.GOOD.argb())
        Kit.bar(g, x + 64, oy + 111, 60, 0.25f, Role.BAD.argb())
        Kit.bar(g, x + 128, oy + 111, 60, 0.62f, Role.WARNING.argb())
        Kit.bar(g, x + 192, oy + 111, 60, 1f, Role.VALUE.argb())

        caption(g, "Icons: ink, and roles on a well", x, oy + 121)
        val icons = Icon.entries
        val well = x + icons.size * 8 + 4
        Kit.well(g, well, oy + 132, icons.size * 8 + 4, 14)
        for (i in icons.indices) {
            Kit.icon(g, icons[i], x + i * 8, oy + 136, t.ink, t.inkShadow)
            Kit.icon(g, icons[i], well + 3 + i * 8, oy + 136, ROLES[i % ROLES.size].argb())
        }

        caption(g, "Divider, rings and a line", x, oy + 150)
        Kit.divider(g, x, oy + 166, 120)
        Kit.ring(g, x + 128, oy + 162, 40, 10, t.accent)
        Kit.ring(g, x + 172, oy + 162, 40, 10, Role.GOOD.argb())
        Kit.ring(g, x + 216, oy + 162, 14, 10, Role.BAD.argb())
        Kit.line(g, x + 234, oy + 171, x + 251, oy + 162, Role.ACTION.argb())

        caption(g, "Range slider, and spans: idle, hover, down, off", x, oy + 180)
        caption(g, "Text field, keys: held together, clashing, game's key, recording, a cycle button and a menu", x, oy + 214)
        for (i in states.indices) Kit.span(g, ox + MIDDLE + i * 30, oy + 193, 26, 14, states[i])

        window(g, ox + MIDDLE, oy + 40)
        list(g, ox + RIGHT, oy + 7)
        plate(g, ox + RIGHT, oy + 159)
        skeleton(g, ox + RIGHT + 128, oy + 159)
        tooltip(g, ox + MIDDLE + 30, oy + 84)
        glass(g, ox, oy + HEIGHT + 2)

        super.extractRenderState(graphics, mouseX, mouseY, partialTick)
    }

    /** Tabs, a slot grid with every mark and a scrollbar, as a storage screen would have them. Its search field is a widget. */
    private fun window(g: GuiGraphicsExtractor, x: Int, y: Int) {
        caption(g, "Tabs on both edges, slots, marks", x, y - 33)
        for (i in 1 until tabs.size) g.item(tabs[i], x + 4 + i * 23 + 3, Kit.tab(g, x + 4 + i * 23, y, false))
        // Tabs on the bottom edge too: one under the mouse, then the selected one after the panel.
        g.item(tabs[2], x + 31, Kit.tab(g, x + 28, y + 118, false, TabEdge.BOTTOM, hovered = true))
        Kit.panel(g, x, y, 113, 118)
        g.item(tabs[0], x + 7, Kit.tab(g, x + 4, y, true))
        g.item(tabs[1], x + 7, Kit.tab(g, x + 4, y + 118, true, TabEdge.BOTTOM))
        Kit.label(g, "Storage", x + 7, y + 6, Kit.Ink.MUTED)
        Kit.icon(g, Icon.CROSS, x + 99, y + 7, Kit.theme.ink, Kit.theme.inkShadow)
        val gx = x + 7
        val gy = y + 18
        for (i in 0 until 20) {
            val sx = gx + i % 5 * 18
            val sy = gy + i / 5 * 18
            Kit.slot(g, sx, sy)
            val mark = marks.getOrNull(i) ?: continue
            if (mark.style == 'f') Kit.slotFill(g, sx + 1, sy + 1, mark.color)
            if (mark.style == 'b') Kit.slotArt(g, sx + 1, sy + 1, SlotArts.atlas(DsModClient.settings.inventory, Surface.CONTAINER), mark.role.ordinal)
            g.item(mark.item, sx + 1, sy + 1)
            g.itemDecorations(font, mark.item, sx + 1, sy + 1)
            if (mark.style == 'd') Kit.slotDim(g, sx + 1, sy + 1)
            if (mark.style == 'h') Kit.slotHover(g, sx + 1, sy + 1)
            if (mark.style == 'c') Kit.cooldown(g, sx + 1, sy + 1, 0.6f)
            if (mark.style == 'b' && DsModClient.settings.inventory.inventoryStyle.gem) {
                Kit.slotGem(g, sx + 1, sy + 1, SlotArts.atlas(DsModClient.settings.inventory, Surface.CONTAINER), mark.role.ordinal)
            }
        }
        Kit.scrollbar(g, gx + 93, gy, 72, 6, 30)
    }

    /** A strip of glass below the gallery, over the world as a glass menu is: slots, a well, buttons and tabs in each state. */
    private fun glass(g: GuiGraphicsExtractor, x: Int, y: Int) {
        val alpha = 153
        Kit.glass(g, x, y, 240, 26, alpha)
        g.text(font, "Glass", x + 7, y + 9, Kit.GLASS_INK, true)
        for (i in 0 until 3) Kit.glassSlot(g, x + 40 + i * 18, y + 4)
        g.item(tabs[3], x + 41, y + 5)
        Kit.glassWell(g, x + 98, y + 4, 54, 18)
        for (i in states.indices) Kit.button(g, x + 156 + i * 20, y + 5, Kit.BUTTON_HEIGHT, Kit.BUTTON_HEIGHT, states[i], Icon.BACK, glass = true)
        g.item(tabs[0], x + 4, Kit.glassTab(g, x + 1, y + 26, true, alpha, TabEdge.BOTTOM))
        g.item(tabs[1], x + 27, Kit.glassTab(g, x + 24, y + 26, false, alpha, TabEdge.BOTTOM))
        g.item(tabs[2], x + 50, Kit.glassTab(g, x + 47, y + 26, false, alpha, TabEdge.BOTTOM, hovered = true))
    }

    /** A plain tooltip over the slot grid, drawn last as the game draws one. */
    private fun tooltip(g: GuiGraphicsExtractor, x: Int, y: Int) {
        Kit.tooltip(g, x, y, tooltip.maxOf(font::width), tooltip.size * 10 - 2)
        for (i in tooltip.indices) g.text(font, tooltip[i], x, y + i * 10, -1, true)
    }

    /** Role text belongs on a dark well, as in a crafting tree. */
    private fun list(g: GuiGraphicsExtractor, x: Int, y: Int) {
        caption(g, "Well with role text, one line flashed", x, y)
        Kit.well(g, x, y + 12, 172, 122)
        for (i in lines.indices) {
            val line = lines[i]
            val lx = x + 5 + line.depth * 8
            val ly = y + 17 + i * 11
            Kit.icon(g, line.icon, lx, ly + if (line.icon == Icon.WARN) 0 else 1, line.color)
            g.text(font, line.text, lx + 10, ly, -1, true)
        }
        Kit.flash(g, x + 1, y + 27, 163, 11, 0.3f)
        Kit.scrollbar(g, x + 164, y + 14, 118, 0, 60)
    }

    private fun plate(g: GuiGraphicsExtractor, x: Int, y: Int) {
        caption(g, "HUD plate", x, y - 12)
        Kit.plate(g, x, y, 120, 54)
        for (i in hud.indices) g.text(font, hud[i], x + 4, y + 4 + i * 10, -1, true)
        Kit.bar(g, x + 4, y + 44, 80, 0.8f, Role.WARNING.argb())
    }

    /** Bones where a loading screen's words will be, and the veil a notice sits on, over the lower half. */
    private fun skeleton(g: GuiGraphicsExtractor, x: Int, y: Int) {
        caption(g, "Veil", x, y - 12)
        Kit.bone(g, x, y, 16, 16)
        Kit.bone(g, x + 20, y + 2, 24, 7)
        Kit.bar(g, x, y + 20, 44, 0f, Role.VALUE.argb())
        Kit.bone(g, x, y + 32, 16, 16)
        Kit.bone(g, x + 20, y + 34, 18, 7)
        Kit.veil(g, x - 2, y + 28, 48, 26)
    }

    private fun caption(g: GuiGraphicsExtractor, text: String, x: Int, y: Int) = Kit.label(g, text, x, y, Kit.Ink.MUTED)

    private companion object {
        val ROLES = listOf(Role.GOOD, Role.BAD, Role.WARNING, Role.VALUE, Role.ACTION, Role.MUTED, Role.LEGENDARY)
        val RESULT = ItemStack(Items.CRAFTING_TABLE)

        fun name(state: Kit.State) = state.name.lowercase().replaceFirstChar(Char::uppercase)

        fun line(icon: Icon, role: Role, depth: Int, build: TextBuilder.() -> Unit) = Line(icon, role.argb(), depth, text(build).toComponent())

        fun mark(item: Item, count: Int, role: Role, style: Char) = Mark(ItemStack(item, count), role, role.argb(110), style)
    }
}
