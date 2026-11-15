package io.github.noxaur.dsmod.edge

import io.github.noxaur.dsmod.core.text.Role
import io.github.noxaur.dsmod.core.text.text
import io.github.noxaur.dsmod.core.ui.CommandList
import io.github.noxaur.dsmod.core.ui.ItemSearch
import io.github.noxaur.dsmod.edge.mixin.ContainerScreenAccessor
import io.github.noxaur.dsmod.edge.ui.Kit
import io.github.noxaur.dsmod.edge.ui.KitButton
import io.github.noxaur.dsmod.edge.ui.KitTextField
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents
import net.fabricmc.fabric.api.client.screen.v1.ScreenKeyboardEvents
import net.fabricmc.fabric.api.client.screen.v1.ScreenMouseEvents
import net.fabricmc.fabric.api.client.screen.v1.Screens
import net.minecraft.client.gui.components.Tooltip
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen
import net.minecraft.core.component.DataComponents
import net.minecraft.world.item.ItemStack
import org.lwjgl.glfw.GLFW
import java.util.BitSet

/**
 * Menu Search and Command Buttons, under Better UI. They stand in a column right of the menu, from the top of the
 * player's inventory down, so they keep their place from menu to menu as the inventory does under Stable Menus: the
 * search field, then a button for each command. A menu too close to the screen's right edge gets neither.
 */
object MenuTools {
    private const val WIDTH = 90
    private const val MIN_WIDTH = 50

    // The first inventory row's frame sits this far above a menu's bottom edge, in a chest and in the inventory alike.
    private const val INVENTORY_TOP = 84

    private val settings get() = DsModClient.settings.betterUi

    /** Whether Better UI is on, which every button it holds asks first. */
    val on: Boolean get() = DsModClient.active && settings.enabled

    // The query stays from menu to menu until the player clears it, so it can follow them through storage pages.
    private var query = ""
    private var words = emptyList<String>()
    private var searched: String? = null
    private var stacks = arrayOfNulls<ItemStack>(0)
    private val dimmed = BitSet()

    fun register() {
        // Fabric drops a screen's listeners on every init, so they are added again here.
        ScreenEvents.AFTER_INIT.register { _, screen, width, height ->
            if (screen !is AbstractContainerScreen<*> || screen is CreativeModeInventoryScreen) return@register
            screen as ContainerScreenAccessor
            val x = screen.leftPos + screen.imageWidth + Kit.GAP
            val columnWidth = minOf(WIDTH, width - Kit.GAP - x)
            if (columnWidth < MIN_WIDTH) return@register
            var y = screen.topPos + screen.imageHeight - INVENTORY_TOP
            val widgets = Screens.getWidgets(screen)
            stacks = arrayOfNulls(0)
            searched = null

            val field = KitTextField(x, y, columnWidth, "Search", "Search...")
            field.value = query
            field.setResponder {
                query = it
                words = ItemSearch.words(it)
            }
            widgets.add(field)
            y += Kit.BUTTON_HEIGHT + Kit.GAP

            val buttons = ArrayList<KitButton>()
            for (command in CommandList.parse(settings.commands)) {
                if (y + Kit.BUTTON_HEIGHT > height - Kit.GAP) break
                val label = "/$command"
                val shown = if (Kit.font.width(label) <= columnWidth - 8) label else Kit.font.plainSubstrByWidth(label, columnWidth - 8 - Kit.font.width("...")) + "..."
                val button = KitButton(x, y, shown, { MenuBack.send(command) }, columnWidth)
                button.setTooltip(Tooltip.create(text { muted("Sends $label."); action("\nClick to send.") }.toComponent()))
                buttons += button
                widgets.add(button)
                y += Kit.BUTTON_HEIGHT + 2
            }

            fun show() {
                field.visible = on && settings.menuSearch
                for (button in buttons) button.visible = on && settings.commandButtons
            }
            show()
            ScreenEvents.afterTick(screen).register {
                show()
                search(screen, field)
            }
            ScreenEvents.afterForeground(screen).register { _, graphics, _, _, _ ->
                if (!field.visible) return@register
                for (slot in screen.menu.slots) {
                    if (dims(slot.index) && slot.isActive) Kit.slotDim(graphics, screen.leftPos + slot.x, screen.topPos + slot.y)
                }
            }
            ScreenKeyboardEvents.allowKeyPress(screen).register { _, event ->
                when {
                    !field.visible -> true
                    // A focused field takes every key, so E, Q and the number keys type instead of closing or moving items.
                    field.isFocused -> {
                        if (event.isEscape || event.isConfirmation) screen.focused = null else field.keyPressed(event)
                        false
                    }
                    event.hasControlDownWithQuirk() && event.key() == GLFW.GLFW_KEY_F -> {
                        screen.focused = field
                        false
                    }
                    else -> true
                }
            }
            ScreenMouseEvents.allowMouseClick(screen).register { _, event ->
                val over = field.visible && field.isMouseOver(event.x(), event.y())
                if (over && event.button() == GLFW.GLFW_MOUSE_BUTTON_RIGHT) {
                    field.value = ""
                    screen.focused = field
                    return@register false
                }
                if (!over && field.isFocused) screen.focused = null
                true
            }
        }
    }

    /** Whether Menu Search dims the open menu's slot at [index], because its item does not match. */
    fun dims(index: Int) = words.isNotEmpty() && dimmed[index]

    /** Works out which slots to dim, looking again only at a slot whose stack changed since the last tick. */
    private fun search(screen: AbstractContainerScreen<*>, field: KitTextField) {
        if (!field.visible || words.isEmpty()) {
            field.setTextColor(Role.BODY.argb())
            return
        }
        val slots = screen.menu.slots
        val again = searched != query || stacks.size != slots.size
        if (stacks.size != slots.size) stacks = arrayOfNulls(slots.size)
        for (i in slots.indices) {
            val stack = slots[i].item
            if (!again && stack === stacks[i]) continue
            stacks[i] = stack
            dimmed[i] = !stack.isEmpty && !Slots.hides(i) && !matches(stack)
        }
        searched = query
        val any = slots.indices.any { !stacks[it]!!.isEmpty && !dimmed[it] && !Slots.hides(it) }
        field.setTextColor(if (any) Role.BODY.argb() else Role.BAD.argb())
    }

    private fun matches(stack: ItemStack) =
        ItemSearch.matches(words, stack.hoverName.string, stack.get(DataComponents.LORE)?.lines()?.map { it.string }.orEmpty())
}
