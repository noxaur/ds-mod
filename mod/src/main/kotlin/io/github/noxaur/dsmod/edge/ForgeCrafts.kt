package io.github.noxaur.dsmod.edge

import io.github.noxaur.dsmod.core.config.KeyBind
import io.github.noxaur.dsmod.core.feature.inventory.ForgeCraft
import io.github.noxaur.dsmod.core.game.Item
import io.github.noxaur.dsmod.core.text.Role
import io.github.noxaur.dsmod.core.text.text
import io.github.noxaur.dsmod.edge.mixin.ContainerScreenAccessor
import io.github.noxaur.dsmod.edge.ui.Kit
import io.github.noxaur.dsmod.edge.ui.KitButton
import io.github.noxaur.dsmod.edge.ui.KitKeysButton
import io.github.noxaur.dsmod.edge.ui.KitTextField
import io.github.noxaur.dsmod.edge.ui.fit
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents
import net.fabricmc.fabric.api.client.screen.v1.ScreenKeyboardEvents
import net.fabricmc.fabric.api.client.screen.v1.ScreenMouseEvents
import net.fabricmc.fabric.api.client.screen.v1.Screens
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.components.EditBox
import net.minecraft.client.gui.components.Tooltip
import net.minecraft.client.gui.screens.inventory.ContainerScreen
import net.minecraft.client.input.KeyEvent
import net.minecraft.util.ARGB
import net.minecraft.util.Util
import net.minecraft.world.inventory.AbstractContainerMenu
import net.minecraft.world.inventory.ContainerInput
import net.minecraft.world.item.ItemStack
import org.lwjgl.glfw.GLFW
import java.util.IdentityHashMap

object ForgeCrafts {
    private const val MIN_WIDTH = 108
    private const val MAX_WIDTH = 128
    private const val HEIGHT = 93
    private const val KEY_WIDTH = 28
    private const val FIELD_WIDTH = 20
    private const val ITEM_ROW = 22
    private const val MATERIAL_ROW = 44
    private const val AMOUNT_ROW = 70
    private const val SHIFT_STEP = 10

    private const val WAIT_MILLIS = 1000L

    private val feature get() = DsModClient.feature<ForgeCraft>()
    private val on get() = DsModClient.runs<ForgeCraft>()

    private var clicked: AbstractContainerMenu? = null
    private var clickedAt = 0L
    private val swallowed = HashSet<Int>()

    fun register() {
        ScreenEvents.AFTER_INIT.register { client, screen, _, _ ->
            if (screen !is ContainerScreen) return@register
            val panel = Panel(screen)
            val widgets = Screens.getWidgets(screen)
            widgets += panel.keys
            widgets += panel.minus
            widgets += panel.plus
            widgets += panel.field
            panel.sync()
            ScreenEvents.afterTick(screen).register {
                panel.sync()
                swallowed.removeIf { !Keys.held(client, it) }
            }
            ScreenEvents.beforeExtract(screen).register { _, _, _, _, _ -> panel.place() }
            ScreenEvents.afterBackground(screen).register { _, graphics, mouseX, mouseY, _ -> if (panel.shown) panel.draw(graphics, mouseX, mouseY) }
            ScreenKeyboardEvents.allowKeyPress(screen).register { _, event ->
                !panel.shown || !key(client, screen, panel, event, Keys.action == GLFW.GLFW_PRESS)
            }
            ScreenKeyboardEvents.afterKeyRelease(screen).register { _, event ->
                panel.keys.release(event.key())
            }
            ScreenMouseEvents.allowMouseClick(screen).register { _, event ->
                if (!panel.shown) return@register true
                val button = event.button()
                val background = panel.background(event.x(), event.y())
                val allowed = when {
                    panel.keys.recording -> !(panel.keys.capture(button, mouse = true) || panel.keys.isMouseOver(event.x(), event.y()))
                    panel.field.isFocused && !panel.field.isMouseOver(event.x(), event.y()) -> !background.also { panel.commit() }
                    panel.field.isMouseOver(event.x(), event.y()) -> true.also { panel.opening = !panel.field.isFocused }
                    Keys.isMouse(feature.key.value) && button == feature.key.value -> false.also { press(client, screen) }
                    else -> !background
                }
                if (!allowed) swallowed += button
                allowed
            }
            ScreenMouseEvents.afterMouseClick(screen).register { _, _, consumed ->
                if (panel.opening && panel.field.isFocused) panel.selectAll()
                panel.opening = false
                consumed
            }
            ScreenMouseEvents.allowMouseRelease(screen).register { _, event ->
                panel.keys.release(event.button())
                !swallowed.remove(event.button())
            }
            ScreenMouseEvents.allowMouseScroll(screen).register { _, x, y, _, vertical ->
                if (!panel.shown || vertical == 0.0 || !panel.overAmount(x, y)) return@register true
                panel.step(if (vertical > 0) 1 else -1)
                false
            }
        }
    }

    private fun key(client: Minecraft, screen: ContainerScreen, panel: Panel, event: KeyEvent, fresh: Boolean): Boolean {
        val key = event.key()
        return when {
            panel.keys.recording -> true.also { panel.keys.capture(key) }
            panel.field.isFocused -> true.also { panel.type(screen, event) }
            screen.focused is EditBox -> false
            Keys.isMouse(feature.key.value) || key != feature.key.value -> false
            !fresh -> true
            else -> true.also { press(client, screen) }
        }
    }

    private fun press(client: Minecraft, screen: ContainerScreen) {
        if (!on) return
        val step = feature.next() ?: return
        val menu = screen.menu
        if (step.blocked || step.menu != menu.containerId || step.slot !in menu.slots.indices) return
        val now = Util.getMillis()
        if (menu === clicked && now - clickedAt < WAIT_MILLIS) return
        val mode = client.gameMode ?: return
        val player = client.player ?: return
        clicked = menu
        clickedAt = now
        mode.handleContainerInput(menu.containerId, step.slot, 0, ContainerInput.PICKUP, player)
    }

    private fun ink(rgb: Int, light: Boolean, role: Role? = null): Int {
        if (!light) return ARGB.opaque(rgb)
        val t = Kit.theme
        return when (role) {
            Role.GOOD -> t.inkGood
            Role.BAD -> t.inkBad
            Role.VALUE, Role.COMMON -> t.ink
            else -> ARGB.opaque(darker(rgb))
        }
    }

    private fun darker(rgb: Int): Int {
        fun part(shift: Int) = ((rgb shr shift and 0xFF) * 6 / 10) shl shift
        return part(16) or part(8) or part(0)
    }

    private class Panel(private val screen: ContainerScreen) {
        private val accessor = screen as ContainerScreenAccessor
        var shown = false
            private set
        private var x = 0
        private var y = 0
        private var width = MIN_WIDTH
        private var name = ""
        private var shownName = ""
        private var amountTip = ""

        private val stacks = IdentityHashMap<Item, ItemStack>()

        val keys = KitKeysButton(0, 0, KEY_WIDTH, "Craft Key", listOf(feature.key.value).filter { it >= 0 }, 1) { keys ->
            feature.key.value = keys.firstOrNull() ?: KeyBind.NONE
        }.also {
            it.prompt = "?"
            it.glass = ::glass
            it.setTooltip(tip("Craft Key", "\nEach press clicks the outlined button.", "\nClick, then press a key."))
        }
        val minus = stepper("-", -1)
        val plus = stepper("+", 1)
        val field: KitTextField = KitTextField(0, 0, FIELD_WIDTH, "Amount", "${feature.amount}", 2) { text ->
            if (!text.all(Char::isDigit)) field.value = text.filter(Char::isDigit)
        }
        var opening = false

        private fun stepper(label: String, by: Int) = KitButton(0, 0, label, { step(by) }, Kit.BUTTON_HEIGHT).also { it.glass = ::glass }

        private fun tip(name: String, detail: String, hint: String) = Tooltip.create(text { body(name); muted(detail); action(hint) }.toComponent())

        fun selectAll() {
            field.moveCursorToEnd(false)
            field.setHighlightPos(0)
        }

        fun commit() {
            field.value.toIntOrNull()?.let { feature.amount = it }
            field.value = "${feature.amount}"
            screen.focused = null
        }

        fun type(screen: ContainerScreen, event: KeyEvent) {
            when {
                event.isConfirmation -> commit()
                event.isEscape -> {
                    field.value = "${feature.amount}"
                    screen.focused = null
                }
                else -> field.keyPressed(event)
            }
        }

        private fun glass() = MenuSkin.glass(screen) >= 0

        fun step(by: Int) {
            feature.amount += if (Minecraft.getInstance().hasShiftDown()) by * SHIFT_STEP else by
        }

        fun sync() {
            val recipe = feature.recipe
            shown = on && recipe != null && feature.forge(screen.title.string)
            for (widget in listOf(keys, minus, plus, field)) widget.visible = shown
            if (!shown) {
                if (field.isFocused) screen.focused = null
                return
            }
            if (name != recipe!!.name) {
                name = recipe.name
                width = (Kit.INSET + ITEM_ROW + Kit.font.width(name) + Kit.INSET).coerceIn(MIN_WIDTH, MAX_WIDTH)
                shownName = fit(name, width - Kit.INSET * 2 - ITEM_ROW)
            }
            minus.active = feature.amount > ForgeCraft.AMOUNT_MIN
            plus.active = feature.amount < ForgeCraft.AMOUNT_MAX
            val made = "\n${feature.crafted} of ${feature.amount} crafted."
            if (made != amountTip) {
                amountTip = made
                field.setTooltip(tip("Amount", made, "\nClick to type, or scroll."))
            }
            if (!field.isFocused && field.value != "${feature.amount}") field.value = "${feature.amount}"
            field.setTextColor((if (feature.done) Role.GOOD else Role.VALUE).argb())
            place()
        }

        fun place() {
            x = accessor.leftPos - width - 2
            y = accessor.topPos
            val right = x + width - Kit.INSET
            keys.x = right - KEY_WIDTH
            keys.y = y + 3
            plus.x = right - Kit.BUTTON_HEIGHT
            minus.x = plus.x - 2 - FIELD_WIDTH - 2 - Kit.BUTTON_HEIGHT
            minus.y = y + AMOUNT_ROW
            plus.y = minus.y
            field.place(plus.x - 2 - FIELD_WIDTH, minus.y)
        }

        fun background(mouseX: Double, mouseY: Double) =
            mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + HEIGHT &&
                listOf(keys, minus, plus, field).none { it.isMouseOver(mouseX, mouseY) }

        fun overAmount(mouseX: Double, mouseY: Double) =
            mouseX >= x + Kit.INSET && mouseX < x + width - Kit.INSET && mouseY >= y + AMOUNT_ROW && mouseY < y + AMOUNT_ROW + Kit.BUTTON_HEIGHT

        private fun stack(item: Item) = stacks.getOrPut(item) { item.toStack().also { it.count = 1 } }

        fun draw(g: GuiGraphicsExtractor, mouseX: Int, mouseY: Int) {
            val recipe = feature.recipe ?: return
            val glass = MenuSkin.glass(screen)
            val light = glass < 0 && !Kit.theme.inkShadow
            val font = Kit.font
            if (glass < 0) Kit.panel(g, x, y, width, HEIGHT) else Kit.glass(g, x, y, width, HEIGHT, glass)
            val ix = x + Kit.INSET
            val inner = width - Kit.INSET * 2
            Kit.label(g, "Craft", ix, y + 7, Kit.Ink.NORMAL, glass >= 0)
            fun slot(sx: Int, sy: Int) = if (glass < 0) Kit.slot(g, sx, sy) else Kit.glassSlot(g, sx, sy)
            fun line(text: String, tx: Int, ty: Int, rgb: Int, role: Role? = null) = g.text(font, text, tx, ty, ink(rgb, light, role), !light)

            val top = y + ITEM_ROW
            slot(ix, top)
            g.item(stack(recipe.item), ix + 1, top + 1)
            val nameX = ix + ITEM_ROW
            val nameRgb = recipe.item.name?.spans?.firstOrNull { it.color != null }?.color ?: Role.COMMON.rgb
            line(shownName, nameX, top, nameRgb, Role.rarity(nameRgb).takeIf { it == Role.COMMON })
            val can = feature.canCraft()
            if (feature.done) {
                line("Done", nameX, top + 10, Role.GOOD.rgb, Role.GOOD)
            } else {
                val label = "Can craft "
                Kit.label(g, label, nameX, top + 10, Kit.Ink.MUTED, glass >= 0)
                val role = if (can > 0) Role.VALUE else Role.BAD
                line("$can", nameX + font.width(label), top + 10, role.rgb, role)
            }

            val row = y + MATERIAL_ROW
            val fits = inner / Kit.SLOT_SIZE
            val materials = recipe.materials
            val drawn = if (materials.size > fits) materials.take(fits - 1) else materials
            for ((i, material) in drawn.withIndex()) {
                val sx = ix + i * Kit.SLOT_SIZE
                slot(sx, row)
                feature.icon(material)?.let { g.item(stack(it), sx + 1, row + 1) }
                val count = "${material.count}"
                val short = feature.has(material.name) < material.count
                g.text(font, count, sx + 18 - font.width(count), row + 10, (if (short) Role.BAD else Role.COMMON).argb(), true)
            }
            if (drawn.size < materials.size) {
                val more = "+${materials.size - drawn.size}"
                val sx = ix + drawn.size * Kit.SLOT_SIZE
                Kit.label(g, more, sx + (Kit.SLOT_SIZE - font.width(more)) / 2, row + 5, Kit.Ink.MUTED, glass >= 0)
            }

            val amountY = y + AMOUNT_ROW
            Kit.label(g, "Amount", ix, amountY + 4, Kit.Ink.NORMAL, glass >= 0)

            val tip = when {
                over(mouseX, mouseY, ix, top, 18, 18) -> null.also { g.setTooltipForNextFrame(font, stack(recipe.item), mouseX, mouseY) }
                shownName != name && over(mouseX, mouseY, nameX, top, inner - ITEM_ROW, 9) -> text { server(name, recipe.item.name!!) }
                !feature.done && over(mouseX, mouseY, nameX, top + 9, inner - ITEM_ROW, 10) -> text {
                    body("Can craft")
                    val covered = feature.covered()
                    if (covered > 0) muted("\nMaterials cover $covered.") else bad("\nMaterials cover none.")
                    if (feature.free > 0) muted("\nForge queue has ${feature.free} free.") else bad("\nForge queue is full.")
                }
                over(mouseX, mouseY, ix, row, inner, 18) -> text {
                    body("Each craft takes")
                    for (material in materials) {
                        val have = feature.has(material.name)
                        val line = "\n${material.count} ${material.name}, have $have"
                        if (have < material.count) bad(line) else muted(line)
                    }
                }
                else -> null
            }
            tip?.let { g.setTooltipForNextFrame(font, font.split(it.toComponent(), 240), mouseX, mouseY) }
        }

        private fun over(mouseX: Int, mouseY: Int, ax: Int, ay: Int, w: Int, h: Int) = mouseX >= ax && mouseX < ax + w && mouseY >= ay && mouseY < ay + h
    }

}
