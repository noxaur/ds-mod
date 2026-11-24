package io.github.noxaur.dsmod.edge

import io.github.noxaur.dsmod.core.config.Switch
import io.github.noxaur.dsmod.core.feature.inventory.HotdPlanner
import io.github.noxaur.dsmod.core.heart.HeartBlock
import io.github.noxaur.dsmod.core.heart.MiningArea
import io.github.noxaur.dsmod.core.text.Role
import io.github.noxaur.dsmod.core.text.Text
import io.github.noxaur.dsmod.core.text.grouped
import io.github.noxaur.dsmod.core.text.text
import io.github.noxaur.dsmod.edge.input.InputApproved
import io.github.noxaur.dsmod.edge.input.Refusals
import io.github.noxaur.dsmod.edge.ui.Kit
import io.github.noxaur.dsmod.edge.ui.KitCheckbox
import io.github.noxaur.dsmod.edge.ui.KitDropdown
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.components.Tooltip
import net.minecraft.client.gui.screens.inventory.ContainerScreen
import net.minecraft.network.chat.Component

/** @keep The Heart of the Dragon planner's panel, left of /hotd where Forge Craft puts its own: what the player mines,
 * where and for what, and the powder they have. A dropdown's open menu is drawn over everything and takes the press and
 * the keys first, and a press outside closes it and still does its own job */
object HotdPlans {
    private const val WIDTH = 116
    private const val LABEL_WIDTH = 30
    private const val FIRST_ROW = 20
    private const val ROW = Kit.BUTTON_HEIGHT + Kit.GAP
    private const val CHECK_ROW = Kit.CHECKBOX_SIZE + Kit.GAP
    private const val WELL_HEIGHT = 29

    private lateinit var feature: HotdPlanner

    fun bind(planner: HotdPlanner) {
        feature = planner
    }

    private val taken = TakenButtons()

    @InputApproved(reaches = ["Refusals.allowKeyPress"])
    fun register() {
        ScreenHooks.onEveryLayout<ContainerScreen> { screen, _ ->
            val panel = Panel(screen)
            panel.attach(screen)
            ScreenEvents.afterExtract(screen).register { _, graphics, mouseX, mouseY, partialTick ->
                panel.open()?.menu?.extractRenderState(graphics, mouseX, mouseY, partialTick)
            }
            Refusals.allowKeyPress(screen).register { _, event ->
                val open = panel.open() ?: return@register true
                open.menu?.keyPressed(event)
                false
            }
            taken.register(screen) { event ->
                if (!panel.shown) return@register false
                val open = panel.open()
                val menu = open?.menu
                when {
                    menu != null && menu.isMouseOver(event.x(), event.y()) -> true.also { menu.mouseClicked(event, false) }
                    open != null -> open.isMouseOver(event.x(), event.y()).also { open.close() }
                    else -> panel.background(event.x(), event.y())
                }
            }
        }
    }

    private class Panel(private val screen: ContainerScreen) : SidePanel {
        override var shown = false
            private set
        private var x = 0
        private var y = 0
        private var height = 0
        private var wellY = 0
        private var laidOut: List<KitCheckbox> = emptyList()
        private val names = HashMap<Text, Component>()

        private val ix get() = x + Kit.INSET
        private val inner = WIDTH - Kit.INSET * 2
        private val dropWidth = inner - LABEL_WIDTH

        private fun changed() = feature.feature.redraw()

        private val block = KitDropdown(0, 0, dropWidth, "Block", HeartBlock.entries, feature.block.value, HeartBlock::toString) {
            feature.block.value = it
            changed()
        }.also { it.setTooltip(Tooltip.create(plain(feature.block.description))) }

        private val area = KitDropdown(0, 0, dropWidth, "Area", MiningArea.entries, feature.area.value, MiningArea::toString) {
            feature.area.value = it
            changed()
        }.also { it.setTooltip(Tooltip.create(plain(feature.area.description))) }

        private fun box(switch: Switch, label: String = switch.name) = KitCheckbox(0, 0, label, switch.value) {
            switch.value = it
            changed()
        }.also { it.setTooltip(Tooltip.create(plain(switch.description))) }

        private val nodes = box(feature.nodes)
        private val items = box(feature.items)
        private val goals = listOf(items, box(feature.powder), box(feature.xp), box(feature.pets))
        private val pristine = box(feature.pristine)
        private val fragments = box(feature.fragments, "Fragments")
        private val boxes = listOf(nodes) + goals + listOf(pristine, fragments)
        private val switches = mapOf(nodes to feature.nodes, goals[0] to feature.items, goals[1] to feature.powder, goals[2] to feature.xp,
            goals[3] to feature.pets, pristine to feature.pristine, fragments to feature.fragments)

        override val widgets = listOf(block, area) + boxes

        private fun plain(line: String) = text { body(line) }.toComponent()

        fun open() = listOf(block, area).firstOrNull { it.menu != null }

        override fun sync() {
            shown = feature.feature.runs && feature.shown(screen.title.string)
            if (!shown) {
                block.close()
                area.close()
            }
            block.value = feature.block.value
            area.value = feature.area.value
            for ((box, switch) in switches) box.checked = switch.value
            val showing = buildList {
                if (feature.hasNodes()) add(nodes)
                addAll(goals)
                if (feature.amethyst()) addAll(listOf(pristine, fragments))
            }
            for (widget in widgets) widget.visible = shown && (widget !is KitCheckbox || widget in showing)
            if (showing != laidOut) {
                laidOut = showing
                val drop = feature.block.value
                items.setTooltip(Tooltip.create(plain("${drop.drop} x${drop.count} a block, raised by Fortune and Spread")))
            }
            place()
        }

        // @keep Checkboxes go two to a row, and one whose label is too long for half the row takes all of it
        override fun place() {
            x = screen.containerLeft - WIDTH - 2
            y = screen.containerTop
            block.x = ix + LABEL_WIDTH
            block.y = y + FIRST_ROW
            area.x = ix + LABEL_WIDTH
            area.y = y + FIRST_ROW + ROW
            val half = (inner - Kit.GAP) / 2
            var row = 0
            var column = 0
            for (box in laidOut) {
                val full = box.width > half
                if (full && column == 1) {
                    row++
                    column = 0
                }
                box.x = ix + column * (half + Kit.GAP)
                box.y = y + FIRST_ROW + ROW * 2 + row * CHECK_ROW
                if (full || column == 1) {
                    row++
                    column = 0
                } else {
                    column = 1
                }
            }
            if (column == 1) row++
            wellY = y + FIRST_ROW + ROW * 2 + row * CHECK_ROW
            height = wellY - y + WELL_HEIGHT + Kit.INSET
        }

        fun background(mouseX: Double, mouseY: Double) =
            mouseX >= x && mouseX < x + WIDTH && mouseY >= y && mouseY < y + height && widgets.none { it.visible && it.isMouseOver(mouseX, mouseY) }

        override fun draw(g: GuiGraphicsExtractor, mouseX: Int, mouseY: Int) {
            val glass = MenuSkin.glass(screen)
            if (glass < 0) Kit.panel(g, x, y, WIDTH, height) else Kit.glass(g, x, y, WIDTH, height, glass)
            Kit.label(g, "Upgrades", ix, y + 7, Kit.Ink.NORMAL, glass >= 0)
            Kit.label(g, "Block", ix, block.y + 4, Kit.Ink.MUTED, glass >= 0)
            Kit.label(g, "Area", ix, area.y + 4, Kit.Ink.MUTED, glass >= 0)
            if (glass < 0) Kit.well(g, ix, wellY, inner, WELL_HEIGHT) else Kit.glassWell(g, ix, wellY, inner, WELL_HEIGHT)
            val purse = feature.purse ?: return
            val font = Kit.font
            fun line(name: Text, have: Long, short: Boolean, top: Int) {
                g.text(font, names.getOrPut(name) { name.toComponent() }, ix + 3, top, -1, true)
                val number = grouped(have)
                g.text(font, number, ix + inner - 3 - font.width(number), top, (if (short) Role.BAD else Role.VALUE).argb(), true)
            }
            line(purse.enderName, purse.ender, purse.enderShort, wellY + 4)
            line(purse.voidName, purse.void, purse.voidShort, wellY + 17)
        }
    }
}
