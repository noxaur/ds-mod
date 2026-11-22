package io.github.noxaur.dsmod.edge

import io.github.noxaur.dsmod.core.feature.inventory.BetterUi
import io.github.noxaur.dsmod.core.feature.inventory.MenuTabs
import io.github.noxaur.dsmod.core.text.Role
import io.github.noxaur.dsmod.core.text.text
import io.github.noxaur.dsmod.core.ui.ItemSearch
import io.github.noxaur.dsmod.core.ui.MenuTab
import io.github.noxaur.dsmod.core.ui.TabEdge
import io.github.noxaur.dsmod.core.ui.TabLayout
import io.github.noxaur.dsmod.edge.ui.Icon
import io.github.noxaur.dsmod.edge.ui.Kit
import io.github.noxaur.dsmod.edge.ui.KitButton
import io.github.noxaur.dsmod.edge.ui.KitTab
import io.github.noxaur.dsmod.edge.ui.KitTextField
import java.util.BitSet
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents
import net.fabricmc.fabric.api.client.screen.v1.ScreenKeyboardEvents
import net.fabricmc.fabric.api.client.screen.v1.ScreenMouseEvents
import net.fabricmc.fabric.api.client.screen.v1.Screens
import net.minecraft.client.gui.components.AbstractWidget
import net.minecraft.client.gui.components.Tooltip
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen
import net.minecraft.core.component.DataComponents
import net.minecraft.world.item.ItemStack
import org.lwjgl.glfw.GLFW

/**
 * Menu Tabs, under Better UI: the player's tabs along the top and bottom of every menu and of the inventory, as
 * the settings' `TabPlacesEditor` sets them up. A command tab sends its command, and the one whose command opened the menu shown
 * now joins the panel. The search tab opens a bar along its edge toward the middle of the menu, drawn over the tabs
 * it covers, which dims every item whose name and lore lack a word of the query.
 */
object TabBar {
    private val shown get() = DsModClient.runs<BetterUi>() && DsModClient.runs<MenuTabs>()

    private const val SEARCH_LENGTH = 64

    // The query stays from menu to menu until the player closes the bar, so it can follow them through storage pages.
    private var query = ""
    private var words = emptyList<String>()
    private var searched: String? = null
    private var stacks = arrayOfNulls<ItemStack>(0)
    private val dimmed = BitSet()

    /** What a tab's tooltip says: its name, the command it sends and the click hint. */
    fun tooltip(tab: MenuTab) = text {
        if (tab.search) {
            body("Search")
            muted("\nDims the items that do not match.")
            action("\nClick to search.")
        } else {
            body(tab.tooltip.ifBlank { "/${tab.command}" })
            muted("\nSends /${tab.command}.")
            action("\nClick to send.")
        }
    }.toComponent()

    fun register() {
        ScreenHooks.onEveryLayout<AbstractContainerScreen<*>> { screen, opened ->
            if (screen.creative) return@onEveryLayout
            if (opened) {
                stacks = arrayOfNulls(0)
                searched = null
                dimmed.clear()
            }
            val left = screen.containerLeft
            val menuWidth = screen.containerWidth
            fun edgeY(edge: TabEdge) = if (edge == TabEdge.TOP) screen.containerTop else screen.containerBottom

            val widgets = Screens.getWidgets(screen)
            val tabs = DsModClient.feature<MenuTabs>().tabs.value.filter { it.slot in 0 until TabLayout.PER_EDGE }
            val selected = TabLayout.selected(tabs, Trail.opener(screen))
            val made = ArrayList<Pair<MenuTab, KitTab>>()
            val glass = { MenuSkin.glass(screen) }
            var search: MenuTab? = null
            var openSearch: () -> Unit = {}
            for ((i, tab) in tabs.withIndex()) {
                val x = left + TabLayout.x(tab.slot)
                val widget = if (tab.search) {
                    search = tab
                    KitTab(x, edgeY(tab.edge), tab.edge, "Search", null, Icon.SEARCH, glass) { openSearch() }
                } else {
                    KitTab(x, edgeY(tab.edge), tab.edge, tab.tooltip.ifBlank { "/${tab.command}" }, stackOf(tab.item), null, glass) { PlayerCommands.send(tab.command) }
                }
                widget.selected = i == selected
                widget.setTooltip(Tooltip.create(tooltip(tab)))
                made += tab to widget
                widgets.add(widget)
            }
            // The recipe book moves the inventory and the crafting table sideways without laying them out again.
            val ours = ArrayList<AbstractWidget>(made.map { it.second })
            var laidOut = left
            ScreenEvents.beforeExtract(screen).register { _, _, _, _, _ ->
                val dx = screen.containerLeft - laidOut
                if (dx == 0) return@register
                laidOut = screen.containerLeft
                for (widget in ours) widget.x += dx
            }
            val bar = search ?: return@onEveryLayout

            // The bar is a wide joined tab holding the field and a close button, laid over the tabs it covers.
            val (barLeft, barWidth) = TabLayout.searchBar(bar.slot, menuWidth)
            fun barX() = screen.containerLeft + barLeft
            // On a short screen an edge's tabs run off it. The bar stays on it, over the menu's edge, so its text shows.
            val barY = if (bar.edge == TabEdge.TOP) maxOf(edgeY(bar.edge), Kit.TAB_HEIGHT - 2) else minOf(edgeY(bar.edge), screen.height - Kit.TAB_HEIGHT + 2)
            val covered = made.filter { (tab, _) -> tab.edge == bar.edge && TabLayout.x(tab.slot) < barLeft + barWidth && TabLayout.x(tab.slot) + Kit.TAB_WIDTH > barLeft }
            val contentY = if (bar.edge == TabEdge.TOP) barY - Kit.TAB_HEIGHT + 3 else barY
            val field = KitTextField(barX() + 3, contentY, barWidth - 6 - Kit.BUTTON_HEIGHT - 2, "Search", query, SEARCH_LENGTH) {
                query = it
                words = ItemSearch.words(it)
            }.hint("Search...")
            var open = query.isNotEmpty()
            lateinit var close: KitButton

            fun show() {
                for ((_, widget) in made) widget.visible = shown && !(open && covered.any { it.second === widget })
                field.visible = shown && open
                close.visible = field.visible
            }

            fun shut() {
                field.value = ""
                open = false
                if (screen.focused === field) screen.focused = null
                show()
            }
            close = KitButton(barX() + barWidth - 3 - Kit.BUTTON_HEIGHT, contentY, Icon.CROSS, "Close Search", ::shut)
            close.glass = { glass() >= 0 }
            close.setTooltip(Tooltip.create(text { action("Click to stop searching.") }.toComponent()))
            openSearch = {
                open = true
                show()
                screen.focused = field
            }
            widgets.add(field)
            widgets.add(close)
            ours += field
            ours += close
            show()

            ScreenEvents.afterBackground(screen).register { _, graphics, _, _, _ ->
                if (!field.visible) return@register
                val alpha = glass()
                if (alpha < 0) Kit.tab(graphics, barX(), barY, true, bar.edge, width = barWidth) else Kit.glassTab(graphics, barX(), barY, true, alpha, bar.edge, width = barWidth)
            }
            ScreenEvents.afterTick(screen).register {
                show()
                search(screen, field)
            }
            ScreenEvents.afterForeground(screen).register { _, graphics, _, _, _ ->
                if (!field.visible) return@register
                for (slot in screen.menu.slots) {
                    if (dims(slot.index) && slot.isActive) Kit.slotDim(graphics, screen.screenX(slot), screen.screenY(slot))
                }
            }
            ScreenKeyboardEvents.allowKeyPress(screen).register { _, event ->
                when {
                    !shown -> true
                    // A focused field takes every key, so E, Q and the number keys type instead of closing or moving items.
                    field.isFocused -> {
                        if (event.isEscape || event.isConfirmation) {
                            if (query.isEmpty()) shut() else screen.focused = null
                        } else {
                            field.keyPressed(event)
                        }
                        false
                    }
                    event.hasControlDownWithQuirk() && event.key() == GLFW.GLFW_KEY_F -> {
                        openSearch()
                        false
                    }
                    else -> true
                }
            }
            // The screen focuses a clicked tab after its press, so the field takes focus back once the click is done.
            ScreenMouseEvents.afterMouseClick(screen).register { _, _, _ ->
                if (field.visible && screen.focused is KitTab) screen.focused = field
                true
            }
            ScreenMouseEvents.allowMouseClick(screen).register { _, event ->
                if (!field.visible) return@register true
                val over = field.isMouseOver(event.x(), event.y())
                if (over && event.button() == GLFW.GLFW_MOUSE_BUTTON_RIGHT) {
                    field.value = ""
                    screen.focused = field
                    return@register false
                }
                if (!over && !close.isMouseOver(event.x(), event.y())) {
                    if (query.isEmpty()) shut() else if (field.isFocused) screen.focused = null
                }
                true
            }
        }
    }

    /** Whether the search dims the open menu's slot at [index], because its item does not match. */
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
        val any = slots.indices.any { stacks[it]?.isEmpty == false && !dimmed[it] && !Slots.hides(it) }
        field.setTextColor(if (any) Role.BODY.argb() else Role.BAD.argb())
    }

    private fun matches(stack: ItemStack) =
        ItemSearch.matches(words, stack.hoverName.string, stack.get(DataComponents.LORE)?.lines()?.map { it.string }.orEmpty())
}
