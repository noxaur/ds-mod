package io.github.noxaur.dsmod.edge

import io.github.noxaur.dsmod.core.feature.inventory.MenuTabs
import io.github.noxaur.dsmod.core.text.Role
import io.github.noxaur.dsmod.core.text.text
import io.github.noxaur.dsmod.core.ui.ItemSearch
import io.github.noxaur.dsmod.core.ui.MenuTab
import io.github.noxaur.dsmod.core.ui.TabEdge
import io.github.noxaur.dsmod.core.ui.TabLayout
import io.github.noxaur.dsmod.edge.input.InputApproved
import io.github.noxaur.dsmod.edge.input.PlayerCommands
import io.github.noxaur.dsmod.edge.input.Refusals
import io.github.noxaur.dsmod.edge.ui.Icon
import io.github.noxaur.dsmod.edge.ui.Kit
import io.github.noxaur.dsmod.edge.ui.KitButton
import io.github.noxaur.dsmod.edge.ui.KitTab
import io.github.noxaur.dsmod.edge.ui.KitTextField
import java.util.BitSet
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents
import net.fabricmc.fabric.api.client.screen.v1.ScreenMouseEvents
import net.fabricmc.fabric.api.client.screen.v1.Screens
import net.minecraft.client.gui.components.AbstractWidget
import net.minecraft.client.gui.components.Tooltip
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen
import net.minecraft.core.component.DataComponents
import net.minecraft.world.item.ItemStack
import org.lwjgl.glfw.GLFW

object TabBar {
    private lateinit var menuTabs: MenuTabs

    fun bind(menuTabs: MenuTabs) {
        this.menuTabs = menuTabs
    }

    private val shown get() = menuTabs.feature.runs

    private const val SEARCH_LENGTH = 64
    private const val CHEST_WIDTH = 176

    private var query = ""
    private var words = emptyList<String>()
    private var searched: String? = null
    private var stacks = arrayOfNulls<ItemStack>(0)
    private val dimmed = BitSet()

    /** @keep The words Menu Search dims by, for items drawn outside the menu's slots, such as the storage overview's other pages */
    val searchWords: List<String> get() = if (shown) words else emptyList()

    /** @keep Whether items drawn outside the menu's slots match, so the field turns red only when nothing on screen does */
    var alsoFound: () -> Boolean = { false }

    /** @keep The left edge of a chest-wide column to place the tabs in, on a screen much wider than a chest, such as the
     * storage overview, so they sit under its inventory and not at its far corner. Search joins them there, since the top
     * edge is far from the inventory. */
    var column: (AbstractContainerScreen<*>) -> Int? = { null }

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

    @InputApproved(reaches = ["PlayerCommands.send", "Refusals.allowKeyPress", "Refusals.allowMouseClick"])
    fun register() {
        ScreenHooks.onEveryLayout<AbstractContainerScreen<*>> { screen, opened ->
            if (screen.creative) return@onEveryLayout
            if (opened) {
                stacks = arrayOfNulls(0)
                searched = null
                dimmed.clear()
            }
            val chest = column(screen)
            val inset = chest?.minus(screen.containerLeft) ?: 0
            val left = screen.containerLeft + inset
            val menuWidth = if (chest == null) screen.containerWidth else CHEST_WIDTH
            fun edgeY(edge: TabEdge) = if (edge == TabEdge.TOP) screen.containerTop else screen.containerBottom

            val widgets = Screens.getWidgets(screen)
            val placed = menuTabs.tabs.value.filter { it.slot in 0 until TabLayout.PER_EDGE }
            val tabs = if (chest == null) placed else TabLayout.searchBelow(placed)
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
            // @keep The recipe book moves the inventory and the crafting table sideways without laying them out again.
            val ours = ArrayList<AbstractWidget>(made.map { it.second })
            var laidOut = screen.containerLeft
            ScreenEvents.beforeExtract(screen).register { _, _, _, _, _ ->
                val dx = screen.containerLeft - laidOut
                if (dx == 0) return@register
                laidOut = screen.containerLeft
                for (widget in ours) widget.x += dx
            }
            val bar = search ?: return@onEveryLayout

            val (barLeft, barWidth) = TabLayout.searchBar(bar.slot, menuWidth)
            fun barX() = screen.containerLeft + inset + barLeft
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
            Refusals.allowKeyPress(screen).register { _, event ->
                when {
                    !shown -> true
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
            // @keep The screen focuses a clicked tab after its press, so the field takes focus back once the click is done.
            ScreenMouseEvents.afterMouseClick(screen).register { _, _, _ ->
                if (field.visible && screen.focused is KitTab) screen.focused = field
                true
            }
            Refusals.allowMouseClick(screen).register { _, event ->
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

    // @keep A slot that is hidden or covered keeps its match, so it dims as it should once it shows again
    fun dims(index: Int) = words.isNotEmpty() && dimmed[index] && !Slots.hides(index)

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
            dimmed[i] = !stack.isEmpty && !matches(stack)
        }
        searched = query
        val any = slots.indices.any { stacks[it]?.isEmpty == false && !dimmed[it] && !Slots.hides(it) } || alsoFound()
        field.setTextColor(if (any) Role.BODY.argb() else Role.BAD.argb())
    }

    private fun matches(stack: ItemStack) =
        ItemSearch.matches(words, stack.hoverName.string, stack.get(DataComponents.LORE)?.lines()?.map { it.string }.orEmpty())
}
