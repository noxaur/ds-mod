package io.github.noxaur.dsmod.edge.overview

import io.github.noxaur.dsmod.core.overview.Category
import io.github.noxaur.dsmod.core.overview.Copy
import io.github.noxaur.dsmod.core.overview.Entry
import io.github.noxaur.dsmod.core.overview.Look
import io.github.noxaur.dsmod.core.overview.Overview
import io.github.noxaur.dsmod.core.overview.Shelf
import io.github.noxaur.dsmod.core.overview.SortBy
import io.github.noxaur.dsmod.core.profile.Container
import io.github.noxaur.dsmod.core.profile.Load
import io.github.noxaur.dsmod.core.profile.ProfileLookup
import io.github.noxaur.dsmod.core.text.Role
import io.github.noxaur.dsmod.core.text.short
import io.github.noxaur.dsmod.core.text.text
import io.github.noxaur.dsmod.core.ui.Scroll
import io.github.noxaur.dsmod.core.ui.TabEdge
import io.github.noxaur.dsmod.edge.Slots
import io.github.noxaur.dsmod.edge.profile.toStack
import io.github.noxaur.dsmod.edge.toComponent
import io.github.noxaur.dsmod.edge.toLore
import io.github.noxaur.dsmod.edge.toStack
import io.github.noxaur.dsmod.edge.ui.Icon
import io.github.noxaur.dsmod.edge.ui.Kit
import io.github.noxaur.dsmod.edge.ui.KitButton
import io.github.noxaur.dsmod.edge.ui.KitCycleButton
import io.github.noxaur.dsmod.edge.ui.KitTab
import io.github.noxaur.dsmod.edge.ui.KitTextField
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.components.Tooltip
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.input.KeyEvent
import net.minecraft.client.input.MouseButtonEvent
import net.minecraft.core.component.DataComponents
import net.minecraft.network.chat.Component
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.minecraft.world.item.component.ItemLore
import org.lwjgl.glfw.GLFW
import org.slf4j.LoggerFactory

private const val COLUMNS = 14
private const val ROWS = 8
private const val GRID_WIDTH = COLUMNS * Kit.SLOT_SIZE
private const val GRID_HEIGHT = ROWS * Kit.SLOT_SIZE
private const val INNER_HEIGHT = Kit.BUTTON_HEIGHT + Kit.GAP + GRID_HEIGHT + Kit.GAP + 8
private const val PANEL_WIDTH = Kit.INSET * 2 + GRID_WIDTH
private const val PANEL_HEIGHT = Kit.INSET * 2 + INNER_HEIGHT
private const val SORT_WIDTH = 82
private const val TAB_STEP = 24
private const val MAX_PLACES = 8
private const val MAX_QUERY = 64

// @keep Where the game draws a stack's count: right-aligned 17 in from the item's left, on a line 8 tall ending 17 down.
private const val COUNT_ROOM = 17
private const val COUNT_RIGHT = 17
private const val COUNT_BOTTOM = 17
private const val COUNT_HEIGHT = 8

private val LOG = LoggerFactory.getLogger("dsmod")

private val TAB_ITEMS = mapOf(
    Category.ALL to Items.COMPASS,
    Category.INVENTORY to Items.CHEST,
    Category.ENDER_CHEST to Items.ENDER_CHEST,
    Category.STORAGE to Items.BARREL,
    Category.ACCESSORIES to Items.BUNDLE,
    Category.WARDROBE to Items.LEATHER_CHESTPLATE,
    Category.PETS to Items.BONE,
)

class ItemOverviewScreen(
    private val lookup: ProfileLookup,
    private val seen: List<Copy>,
    private val profile: String?,
    query: String,
    private val parent: Screen?,
    private val pick: (Entry) -> Boolean,
) :
    Screen(text { body("Item Overview") }.toComponent()) {
    private var category = Category.ALL
    private var sort = SortBy.AMOUNT
    private var descending = true
    private var query = query.take(MAX_QUERY)

    private var built: Load<List<Container>>? = null
    private var overview: Overview? = null
    private var shown = emptyList<Entry>()
    private var counts = emptyArray<String?>()
    private var countWidths = IntArray(0)
    private var total = ""
    private var hidden = false
    private var foot = ""
    private var footTip: List<Component>? = null
    private val stacks = HashMap<Entry, ItemStack>()
    private val tips = HashMap<Entry, ItemStack>()

    private val scroll = Scroll()
    private var dragging = false
    private var search: KitTextField? = null

    private var frame = Kit.Frame(0, 0)
    private val gridY get() = frame.top + Kit.BUTTON_HEIGHT + Kit.GAP

    private val state: Load<List<Container>> get() = when (val profile = lookup.state) {
        is Load.Loaded -> lookup.containers(this.profile)
        is Load.Failed -> profile
        Load.Loading -> Load.Loading
    }

    override fun init() {
        frame = Kit.underTabs(width, height, PANEL_WIDTH, PANEL_HEIGHT)
        refresh()
        val overview = overview
        val categories = Category.entries.filter { it == Category.ALL || overview == null || overview.count(it) > 0 }
        if (category !in categories) category = Category.ALL
        for ((i, c) in categories.withIndex()) {
            addRenderableWidget(KitTab(frame.x + 4 + i * TAB_STEP, frame.y, TabEdge.TOP, c.toString(), ItemStack(TAB_ITEMS.getValue(c)), null) { select(c) }.apply {
                selected = c == category
                setTooltip(Tooltip.create(text { body(c.toString()) }.toComponent()))
            })
        }
        val sortX = frame.left + GRID_WIDTH - Kit.BUTTON_HEIGHT - Kit.GAP - SORT_WIDTH
        val field = KitTextField(frame.left, frame.top, sortX - Kit.GAP - frame.left, "Search", this.query, MAX_QUERY) { changed ->
            query = changed
            filter(keepScroll = false)
        }.icon(Icon.SEARCH).hint("Search")
        search = addRenderableWidget(field)
        addRenderableWidget(KitCycleButton(sortX, frame.top, SORT_WIDTH, "Sort", SortBy.entries, sort, { "By $it" }) {
            sort = it
            filter(keepScroll = false)
        })
        addRenderableWidget(KitButton(sortX + SORT_WIDTH + Kit.GAP, frame.top, direction(), directionName()) {
            descending = !descending
            filter(keepScroll = false)
            rebuildWidgets()
        }).setTooltip(Tooltip.create(text { body(directionName()) }.toComponent()))
        setInitialFocus(field)
    }

    private fun direction() = if (descending) Icon.DOWN else Icon.UP

    private fun directionName() = if (descending) "Descending" else "Ascending"

    private fun select(next: Category) {
        if (next == category) return
        category = next
        filter(keepScroll = false)
        rebuildWidgets()
    }

    private fun refresh() {
        val state = state
        if (state === built) return
        built = state
        val backend = (state as? Load.Loaded)?.value
        overview = Overview(Overview.copies(backend.orEmpty(), seen))
        hidden = backend.orEmpty().any { it.hidden }
        (state as? Load.Failed)?.failure?.takeIf { it.bug }?.let { LOG.warn("The item overview could not load the backend's containers", it.cause) }
        stacks.clear()
        tips.clear()
        filter(keepScroll = true)
    }

    private fun filter(keepScroll: Boolean) {
        val overview = overview ?: return
        shown = overview.entries(category, query, sort, descending)
        counts = Array(shown.size) { i -> shown[i].count.takeIf { it > 1 }?.let { short(it.toDouble(), 1) } }
        countWidths = IntArray(shown.size) { i -> counts[i]?.let(font::width) ?: 0 }
        val value = Overview.total(shown)
        total = if (value > 0) "Value ${short(value, 1)}" else ""
        scroll.view = GRID_HEIGHT
        scroll.content = (shown.size + COLUMNS - 1) / COLUMNS * Kit.SLOT_SIZE
        if (!keepScroll) scroll.to(0)
        foot()
    }

    private fun foot() {
        val count = if (shown.size == 1) "1 item" else "${shown.size} items"
        when (val state = state) {
            is Load.Loading -> {
                foot = "Loading your storage"
                footTip = null
            }
            is Load.Failed -> {
                foot = "Showing only what you opened"
                footTip = listOf(state.failure.message.toComponent(), text { muted("Open the overview again to load the rest.") }.toComponent())
            }
            is Load.Loaded -> {
                foot = if (hidden) "Showing only what you opened" else profile?.let { "$count on $it" } ?: count
                footTip = if (hidden) listOf(text { muted("dragsim.co hides your items, so only the menus you opened show.") }.toComponent()) else null
            }
        }
    }

    override fun tick() {
        lookup.follow(profile)
        if (state !== built) rebuildWidgets()
    }

    override fun onClose() = minecraft.gui.setScreen(parent)

    private fun stack(entry: Entry) = stacks.getOrPut(entry) {
        when (val look = entry.item.look) {
            is Look.Drawn -> look.item.toStack()
            is Look.Seen -> look.item.toStack()
        }.also { it.count = 1 }
    }

    private fun tip(entry: Entry) = tips.getOrPut(entry) {
        val stack = stack(entry).copy()
        val lines = ArrayList(stack.get(DataComponents.LORE)?.lines().orEmpty())
        lines += Component.empty()
        for (place in entry.places.take(MAX_PLACES)) lines += text { muted("${place.title}: "); value(place.count.toLong()) }.toLore()
        if (entry.places.size > MAX_PLACES) lines += text { muted("and ${entry.places.size - MAX_PLACES} more") }.toLore()
        entry.value?.let { value ->
            lines += text {
                muted("NPC Value: ")
                value(short(value, 1))
                entry.each?.let { muted(" (${short(it, 1)} each)") }
            }.toLore()
        }
        entry.places.firstOrNull { Shelf.command(it.id) != null }?.let { lines += text { action("Click to open ${it.title}.") }.toLore() }
        stack.set(DataComponents.LORE, ItemLore(lines))
        stack
    }

    override fun extractRenderState(graphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, partialTick: Float) {
        Kit.panel(graphics, frame.x, frame.y, PANEL_WIDTH + if (scroll.overflows) Kit.GAP + Kit.SCROLLBAR_WIDTH else 0, PANEL_HEIGHT)
        val offset = scroll.offset
        val firstRow = offset / Kit.SLOT_SIZE
        val hovered = entryAt(mouseX.toDouble(), mouseY.toDouble())
        graphics.enableScissor(frame.left, gridY, frame.left + GRID_WIDTH, gridY + GRID_HEIGHT)
        for (row in firstRow..firstRow + ROWS) {
            val y = gridY + row * Kit.SLOT_SIZE - offset
            for (column in 0 until COLUMNS) {
                val x = frame.left + column * Kit.SLOT_SIZE
                Kit.slot(graphics, x, y)
                val i = row * COLUMNS + column
                val entry = shown.getOrNull(i) ?: continue
                val stack = stack(entry)
                val mark = entry.item.rarity?.let { name -> Role.RARITIES.firstOrNull { it.name == name } }?.takeIf { Slots.marks(it) }
                if (mark != null) Slots.rarityBack(graphics, mark, i, x + 1, y + 1)
                graphics.item(stack, x + 1, y + 1)
                graphics.itemDecorations(font, stack, x + 1, y + 1)
                counts[i]?.let { count(graphics, it, countWidths[i], x + 1, y + 1) }
                if (entry === hovered) Kit.slotHover(graphics, x + 1, y + 1)
            }
        }
        graphics.disableScissor()
        if (scroll.overflows) {
            val track = GRID_HEIGHT - 2
            Kit.scrollbar(graphics, scrollbarX(), gridY, GRID_HEIGHT, scroll.thumbY(track), scroll.thumb(track))
        }
        val footY = gridY + GRID_HEIGHT + Kit.GAP
        val state = state
        Kit.label(graphics, foot, frame.left, footY, Kit.Ink.MUTED)
        val why = footTip.takeIf { mouseX in frame.left until frame.left + font.width(foot) && mouseY in footY until footY + Kit.LABEL_HEIGHT }
        if (total.isNotEmpty()) Kit.label(graphics, total, frame.left + GRID_WIDTH - font.width(total), footY)
        if (shown.isEmpty() && state !is Load.Loading) {
            val empty = if (query.isBlank()) "Nothing here yet" else "Nothing matches"
            Kit.label(graphics, empty, frame.left + (GRID_WIDTH - font.width(empty)) / 2, gridY + GRID_HEIGHT / 2 - 4, Kit.Ink.MUTED)
        }
        super.extractRenderState(graphics, mouseX, mouseY, partialTick)
        hovered?.let { graphics.setTooltipForNextFrame(font, tip(it), mouseX, mouseY) }
        why?.let { graphics.setComponentTooltipForNextFrame(font, it, mouseX, mouseY) }
    }

    /** @keep Snapping the size to whole screen pixels kept it sharp but halved it at GUI scale 2, which was too small to read. */
    private fun count(graphics: GuiGraphicsExtractor, count: String, width: Int, x: Int, y: Int) {
        val scale = if (width <= COUNT_ROOM) 1f else COUNT_ROOM.toFloat() / width
        val pose = graphics.pose()
        pose.pushMatrix()
        pose.translate((x + COUNT_RIGHT).toFloat(), (y + COUNT_BOTTOM).toFloat())
        pose.scale(scale, scale)
        graphics.text(font, count, -width, -COUNT_HEIGHT, -1, true)
        pose.popMatrix()
    }

    private fun entryAt(x: Double, y: Double): Entry? {
        if (x < frame.left || x >= frame.left + GRID_WIDTH || y < gridY || y >= gridY + GRID_HEIGHT) return null
        val column = ((x - frame.left) / Kit.SLOT_SIZE).toInt()
        val row = ((y - gridY + scroll.offset) / Kit.SLOT_SIZE).toInt()
        return shown.getOrNull(row * COLUMNS + column)
    }

    private fun scrollbarX() = frame.left + GRID_WIDTH + Kit.GAP

    private fun overScrollbar(x: Double, y: Double) = scroll.overflows && x >= scrollbarX() && x < scrollbarX() + Kit.SCROLLBAR_WIDTH && y >= gridY && y < gridY + GRID_HEIGHT

    private fun dragThumb(y: Double) {
        val track = GRID_HEIGHT - 2
        scroll.drag(y - gridY - 1 - scroll.thumb(track) / 2.0, track)
    }

    override fun mouseClicked(event: MouseButtonEvent, doubleClick: Boolean): Boolean {
        val field = search
        if (event.button() == 1 && field != null && field.isMouseOver(event.x, event.y)) {
            field.value = ""
            setFocused(field)
            return true
        }
        if (super.mouseClicked(event, doubleClick)) return true
        val entry = entryAt(event.x, event.y)
        if (event.button() == 0 && entry != null) return pick(entry)
        if (event.button() == 0 && overScrollbar(event.x, event.y)) {
            dragging = true
            dragThumb(event.y)
            return true
        }
        return false
    }

    override fun mouseDragged(event: MouseButtonEvent, dx: Double, dy: Double): Boolean {
        if (dragging) return true.also { dragThumb(event.y) }
        return super.mouseDragged(event, dx, dy)
    }

    override fun mouseReleased(event: MouseButtonEvent): Boolean {
        dragging = false
        return super.mouseReleased(event)
    }

    override fun mouseScrolled(mouseX: Double, mouseY: Double, scrollX: Double, scrollY: Double): Boolean {
        if (scrollY != 0.0 && mouseY >= gridY && mouseY < gridY + GRID_HEIGHT) return true.also { scroll.by(if (scrollY > 0) -Kit.SLOT_SIZE else Kit.SLOT_SIZE) }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY)
    }

    override fun keyPressed(event: KeyEvent): Boolean {
        when (event.key()) {
            GLFW.GLFW_KEY_PAGE_UP -> scroll.by(-(GRID_HEIGHT - Kit.SLOT_SIZE))
            GLFW.GLFW_KEY_PAGE_DOWN -> scroll.by(GRID_HEIGHT - Kit.SLOT_SIZE)
            GLFW.GLFW_KEY_HOME -> if (search?.isFocused == true) return super.keyPressed(event) else scroll.to(0)
            GLFW.GLFW_KEY_END -> if (search?.isFocused == true) return super.keyPressed(event) else scroll.to(scroll.max)
            else -> return super.keyPressed(event)
        }
        return true
    }
}
