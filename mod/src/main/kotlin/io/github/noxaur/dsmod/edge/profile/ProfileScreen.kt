package io.github.noxaur.dsmod.edge.profile

import com.mojang.blaze3d.platform.cursor.CursorTypes
import io.github.noxaur.dsmod.core.profile.Container
import io.github.noxaur.dsmod.core.profile.ContainerSummary
import io.github.noxaur.dsmod.core.profile.DrawnItem
import io.github.noxaur.dsmod.core.profile.Dungeons
import io.github.noxaur.dsmod.core.profile.Failure
import io.github.noxaur.dsmod.core.profile.Kills
import io.github.noxaur.dsmod.core.profile.Load
import io.github.noxaur.dsmod.core.profile.Player
import io.github.noxaur.dsmod.core.profile.Profile
import io.github.noxaur.dsmod.core.profile.ProfileChoice
import io.github.noxaur.dsmod.core.profile.ProfileLookup
import io.github.noxaur.dsmod.core.profile.Progress
import io.github.noxaur.dsmod.core.profile.Rank
import io.github.noxaur.dsmod.core.profile.Stat
import io.github.noxaur.dsmod.core.game.ServerText
import io.github.noxaur.dsmod.core.text.Role
import io.github.noxaur.dsmod.core.text.Text
import io.github.noxaur.dsmod.core.text.text
import io.github.noxaur.dsmod.core.ui.TabEdge
import io.github.noxaur.dsmod.core.ui.Turntable
import io.github.noxaur.dsmod.edge.SlotArts
import io.github.noxaur.dsmod.edge.Slots
import io.github.noxaur.dsmod.edge.toComponent
import io.github.noxaur.dsmod.edge.ui.Icon
import io.github.noxaur.dsmod.edge.ui.Kit
import io.github.noxaur.dsmod.edge.ui.KitButton
import io.github.noxaur.dsmod.edge.ui.KitCycleButton
import io.github.noxaur.dsmod.edge.ui.KitTab
import io.github.noxaur.dsmod.edge.ui.fit
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.components.Tooltip
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.input.KeyEvent
import net.minecraft.client.input.MouseButtonEvent
import net.minecraft.core.component.DataComponents
import net.minecraft.network.chat.Component
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.minecraft.world.item.component.ResolvableProfile
import org.lwjgl.glfw.GLFW
import org.slf4j.LoggerFactory
import java.time.Duration
import java.time.Instant
import java.util.IdentityHashMap
import java.util.Locale
import java.util.UUID
import kotlin.math.roundToInt

// The panel is 423 wide, so it fits a screen 854 pixels wide at GUI scale 2.
private const val LEFT_WIDTH = 96
private const val CONTENT_WIDTH = 296
private const val INNER_HEIGHT = 184
private const val HEADER = 20

// The overview fits on one screen: skills and slayers down the left of the content, stats, dungeons and kills down the right.
private const val PROGRESS_WIDTH = 144
private const val LIST_WIDTH = CONTENT_WIDTH - PROGRESS_WIDTH - Kit.GAP
private const val CELL_STEP = 18
private const val LINE = 10
private const val ROW = 9
private const val SECTION = 12
private const val SCROLL_STEP = 18

// A container's grid is nine slots wide on the left, and its side panel fills the rest.
private const val GRID_WIDTH = 9 * Kit.SLOT_SIZE
private const val SIDE_X = GRID_WIDTH + 8
private const val SIDE_WIDTH = CONTENT_WIDTH - SIDE_X
private const val PAGE_BUTTON = 22
private const val PAGES_PER_ROW = 5

/** The kinds of container with a tab of their own, each with the item on its tab. One tab holds every page of a kind. */
private val TAB_ITEMS = mapOf(
    "inventory" to Items.CHEST,
    "ender_chest" to Items.ENDER_CHEST,
    "storage" to Items.BARREL,
    "wardrobe" to Items.LEATHER_CHESTPLATE,
    "accessories" to Items.BUNDLE,
)

/** How big a hidden container's grid is drawn behind its notice, since the backend sends no size for one. */
private val HIDDEN_SLOTS = mapOf("inventory" to 36, "ender_chest" to 27, "storage" to 45, "wardrobe" to 36, "accessories" to 18)

private val SKILL_ITEMS = mapOf(
    "combat" to Items.DIAMOND_SWORD,
    "mining" to Items.IRON_PICKAXE,
    "taming" to Items.LEAD,
    "catacombs" to Items.WITHER_SKELETON_SKULL,
    "dragon_slaying" to Items.DRAGON_HEAD,
    "farming" to Items.GOLDEN_HOE,
    "foraging" to Items.JUNGLE_SAPLING,
    "fishing" to Items.FISHING_ROD,
    "enchanting" to Items.ENCHANTING_TABLE,
    "alchemy" to Items.BREWING_STAND,
    "carpentry" to Items.CRAFTING_TABLE,
    "healer" to Items.POTION,
    "mage" to Items.BLAZE_ROD,
    "berserk" to Items.IRON_SWORD,
    "archer" to Items.BOW,
    "tank" to Items.LEATHER_CHESTPLATE,
    "zombie" to Items.ROTTEN_FLESH,
    "enderman" to Items.ENDER_PEARL,
    "spider" to Items.SPIDER_EYE,
    "wolf" to Items.BONE,
    "blaze" to Items.BLAZE_ROD,
)

/** The rarities a mark can show, in order, so a container's count of them reads from common up. */
private val RARITIES = listOf(Role.COMMON, Role.UNCOMMON, Role.RARE, Role.EPIC, Role.LEGENDARY, Role.MYTHIC, Role.DIVINE, Role.SPECIAL)

/**
 * The shape of a profile with nothing in it: as many skills, slayers, stats, classes and kills as most have. While a
 * profile loads or fails, the overview draws this with bones for its words and a veil over it, so the screen keeps
 * its shape and the notice sits where the profile will be.
 */
private val SKELETON = run {
    fun progress(n: Int) = List(n) { Progress("", "", 0, 1, 0.0, 0.0, null) }
    Profile(
        snapshot = "", fetchedAt = "", player = Player("", "", Rank("", ServerText(emptyList()))), profileId = "",
        skills = progress(5), slayers = progress(3),
        stats = List(5) { Stat("", "", ServerText(emptyList())) },
        topKills = List(5) { Kills("", "", 0) },
        dungeons = Dungeons(classes = progress(5)),
    )
}

/**
 * The profile viewer: another player's profile, or the player's own, as the backend has it. A tab along the top for
 * the overview, the dungeons and each kind of container, the player on the left turning on a turntable, and the
 * profile picker under them. Items are real `ItemStack`s with the player's rarity marks, so they look and hover as in
 * a menu, and [hovered] says which one is under the mouse. While something loads, fails or is hidden, its part of the
 * screen keeps its shape under a veil with the notice in its middle. Closing goes back to [parent].
 */
class ProfileScreen(private val lookup: ProfileLookup, private val parent: Screen?) : Screen(text { body("Profile Viewer") }.toComponent()) {
    private val turntable = Turntable()
    private var model: ProfileModel? = null
    private var dressed: Profile? = null
    private var equipment = emptyList<Shown?>()

    /** What the screen was built for, so a change of the lookup's state rebuilds it. */
    private var built: Load<Profile>? = null
    private var last: Profile? = null

    private var tab = OVERVIEW
    private val pages = HashMap<String, Int>()

    // The shown container's items, built once for as long as it is the one shown. A switch or a new snapshot brings
    // another container under the same id, so it is matched by the answer, not by the id.
    private var stacked: Container? = null
    private var stacks = emptyList<Shown?>()
    private var tabs = emptyList<Tab>()
    private val skillIcons = HashMap<String, ItemStack>()

    private var panelX = 0
    private var panelY = 0
    private var left = 0
    private var top = 0

    /** Where the tab's content starts, which a game test aims the mouse from. */
    var contentX = 0
        private set
    val viewY get() = if (headless) top else top + HEADER

    // The overview and the dungeons have no title row, so their content starts at the top and has the whole height.
    private val headless get() = tab == OVERVIEW || tab == DUNGEONS || profile == null
    private val viewHeight get() = INNER_HEIGHT - (viewY - top)
    private var scroll = 0
    private var contentHeight = 0
    private var scrolling = false

    /** The item under the mouse, the hook for a tooltip of the mod's own later. Null when there is none. */
    var hovered: DrawnItem? = null
        private set

    private class Tab(val kind: String, val name: String, val icon: ItemStack, val pages: List<ContainerSummary>)

    /** An item ready to draw, with the rarity its mark shows, or null when it shows none. */
    private class Shown(val item: DrawnItem, val stack: ItemStack, val mark: Role?)

    /** What covers part of the screen: a line in the middle of a veil, with an icon and buttons. */
    private class Notice(val message: Component, val icon: Icon?, val color: Int, val wholeScreen: Boolean, val failure: Failure? = null)

    private val profile get() = (lookup.state as? Load.Loaded)?.value ?: last

    override fun init() {
        SlotArts.checkPacks()
        val width = Kit.INSET * 2 + LEFT_WIDTH + Kit.INSET + CONTENT_WIDTH + Kit.GAP + Kit.SCROLLBAR_WIDTH
        val height = Kit.INSET * 2 + INNER_HEIGHT
        panelX = (this.width - width) / 2
        panelY = (this.height - height + Kit.TAB_HEIGHT) / 2
        left = panelX + Kit.INSET
        top = panelY + Kit.INSET
        contentX = left + LEFT_WIDTH + Kit.INSET
        built = lookup.state
        (lookup.state as? Load.Loaded)?.value?.let { last = it }
        val profile = profile
        if (profile != null) {
            if (dressed !== profile) dress(profile)
            tabs = tabs(profile)
            if (tabs.none { it.kind == tab }) tab = OVERVIEW
            for ((i, t) in tabs.withIndex()) {
                addRenderableWidget(KitTab(panelX + 4 + i * 24, panelY, TabEdge.TOP, t.name, t.icon, null) { select(t.kind) }.apply {
                    selected = t.kind == tab
                    setTooltip(Tooltip.create(text { body(t.name) }.toComponent()))
                })
            }
            if (profile.profiles.size > 1) {
                addRenderableWidget(
                    KitCycleButton(left, top + INNER_HEIGHT - Kit.BUTTON_HEIGHT, LEFT_WIDTH, "Profile", profile.profiles, picked(profile), ::choiceName) {
                        lookup.switch(it.id)
                    },
                )
            }
            pagedTab()?.let { paged ->
                for (i in paged.pages.indices) {
                    val x = contentX + SIDE_X + i % PAGES_PER_ROW * (PAGE_BUTTON + Kit.GAP)
                    val y = viewY + SECTION + i / PAGES_PER_ROW * (Kit.BUTTON_HEIGHT + Kit.GAP)
                    addRenderableWidget(KitButton(x, y, "${i + 1}", { turn(paged, i) }, PAGE_BUTTON)).setTooltip(Tooltip.create(paged.pages[i].title.toComponent()))
                }
            }
        }
        val notice = notice()
        val failure = notice?.failure
        if (notice != null && failure != null) {
            val buttons = listOfNotNull(
                KitButton(0, 0, "Retry", { lookup.retry(); rebuildWidgets() }).takeIf { failure.retry },
                KitButton(0, 0, "Selected Profile", { lookup.selected() }).takeIf { failure.selected },
            )
            val (_, boxBottom) = noticeBox(notice, buttons.isNotEmpty())
            var x = noticeCenter(notice) - (buttons.sumOf { it.width } + Kit.GAP * (buttons.size - 1)) / 2
            for (button in buttons) {
                button.x = x
                button.y = boxBottom + Kit.GAP
                x += button.width + Kit.GAP
                addRenderableWidget(button)
            }
            if (failure.bug) LOG.warn("The profile viewer could not show {}", lookup.name, failure.cause)
        }
    }

    /** The profile the picker shows: the one asked for while it loads, so the picker does not step back meanwhile. */
    private fun picked(profile: Profile) = profile.profiles.firstOrNull { it.id == lookup.asked && lookup.state is Load.Loading }
        ?: profile.choice ?: profile.profiles[0]

    private fun tabs(profile: Profile): List<Tab> {
        val head = ItemStack(Items.PLAYER_HEAD).apply {
            runCatching { UUID.fromString(profile.player.uuid) }.getOrNull()?.let { set(DataComponents.PROFILE, ResolvableProfile.createUnresolved(it)) }
        }
        val overview = Tab(OVERVIEW, "Overview", head, emptyList())
        val dungeons = Tab(DUNGEONS, "Dungeons", ItemStack(Items.WITHER_SKELETON_SKULL), emptyList()).takeIf { profile.dungeons.classes.isNotEmpty() }
        val kinds = profile.containers.groupBy { it.kind }.map { (kind, pages) ->
            val name = when (kind) {
                "storage" -> "Storage"
                "wardrobe" -> "Wardrobe"
                else -> pages[0].title.plain
            }
            Tab(kind, name, ItemStack(TAB_ITEMS[kind] ?: Items.PAPER), pages)
        }
        return listOfNotNull(overview, dungeons) + kinds
    }

    private fun dress(profile: Profile) {
        dressed = profile
        val worn = profile.equipment
        equipment = listOf(worn.helmet, worn.chestplate, worn.leggings, worn.boots, worn.held).map { it?.let(::shown) }
        val level = minecraft.level ?: return
        val uuid = runCatching { UUID.fromString(profile.player.uuid) }.getOrNull() ?: return
        val current = model?.takeIf { it.uuid == uuid } ?: ProfileModel(level, uuid, profile.player.name).also { model = it }
        current.dress(worn)
    }

    /** A stand-in player for the well while there is no profile, which the veil dims. */
    private val ghost by lazy { minecraft.level?.let { ProfileModel(it, UUID(0, 0), "Steve") } }

    private fun shown(item: DrawnItem): Shown {
        val rarity = item.rarity?.let { name -> RARITIES.firstOrNull { it.name == name } }
        return Shown(item, item.toStack(), rarity)
    }

    private fun select(kind: String) {
        if (kind == tab) return
        tab = kind
        scroll = 0
        rebuildWidgets()
    }

    private fun pagedTab() = tabs.firstOrNull { it.kind == tab }?.takeIf { it.pages.size > 1 }

    private fun page(t: Tab) = (pages[t.kind] ?: 0).coerceIn(0, (t.pages.size - 1).coerceAtLeast(0))

    private fun turn(t: Tab, to: Int) {
        pages[t.kind] = to
        scroll = 0
        rebuildWidgets()
    }

    private fun shownTab() = tabs.firstOrNull { it.kind == tab }

    private fun shownSummary(): ContainerSummary? = shownTab()?.let { t -> t.pages.getOrNull(page(t)) }

    private fun shownContainer(): Load<Container>? {
        val summary = shownSummary() ?: return null
        if (summary.hidden) return null
        return lookup.container(summary.id)
    }

    override fun onClose() = minecraft.gui.setScreen(parent)

    override fun tick() {
        // Answers arrive between frames, and the tabs, the picker and the buttons follow what arrived.
        if (lookup.state !== built || failedContainer() != builtFailure) rebuildWidgets()
    }

    private var builtFailure: Boolean = false

    private fun failedContainer() = shownContainer() is Load.Failed

    override fun rebuildWidgets() {
        builtFailure = failedContainer()
        super.rebuildWidgets()
    }

    /** What the screen should say over its veil now, if anything: the profile's state first, then the shown tab's. */
    private fun notice(): Notice? {
        val name = profile?.player?.name ?: lookup.name
        when (val state = lookup.state) {
            is Load.Loading -> return Notice(text { muted(LOADING) }.toComponent(), null, 0, wholeScreen = profile == null)
            is Load.Failed -> return Notice(state.failure.message.toComponent(), Icon.WARN, Role.BAD.argb(), wholeScreen = profile == null, failure = state.failure)
            is Load.Loaded -> Unit
        }
        val summary = shownSummary() ?: return null
        if (summary.hidden) return Notice(text { body("$name keeps their inventory hidden.") }.toComponent(), Icon.LOCK, Role.MUTED.argb(), wholeScreen = false)
        return when (val load = lookup.container(summary.id)) {
            is Load.Loading -> Notice(text { muted(LOADING) }.toComponent(), null, 0, wholeScreen = false)
            is Load.Failed -> Notice(load.failure.message.toComponent(), Icon.WARN, Role.BAD.argb(), wholeScreen = false, failure = load.failure)
            is Load.Loaded -> null
        }
    }

    /** The middle of what the notice covers: the whole inside of the panel, or the tab's content. */
    private fun noticeCenter(notice: Notice) = if (notice.wholeScreen) left + (LEFT_WIDTH + Kit.INSET + CONTENT_WIDTH) / 2 else contentX + CONTENT_WIDTH / 2

    /** The notice's box from top to bottom, centered with its buttons, if any, under it. */
    private fun noticeBox(notice: Notice, buttons: Boolean): Pair<Int, Int> {
        val lines = font.split(notice.message, NOTICE_TEXT).size.coerceIn(1, 2)
        val height = lines * LINE + 10
        val total = height + if (buttons) Kit.GAP + Kit.BUTTON_HEIGHT else 0
        val (areaTop, areaHeight) = if (notice.wholeScreen) top to INNER_HEIGHT else viewY to viewHeight
        val boxTop = areaTop + (areaHeight - total) / 2
        return boxTop to boxTop + height
    }

    // Where the mouse was on the last frame, for the arrow keys that turn the model under it.
    private var mouseX = 0.0
    private var mouseY = 0.0

    override fun extractRenderState(graphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, partialTick: Float) {
        this.mouseX = mouseX.toDouble()
        this.mouseY = mouseY.toDouble()
        val now = System.currentTimeMillis()
        hovered = null
        var tooltip: List<Component>? = null
        var item: ItemStack? = null
        val width = Kit.INSET * 2 + LEFT_WIDTH + Kit.INSET + CONTENT_WIDTH + Kit.GAP + Kit.SCROLLBAR_WIDTH
        Kit.panel(graphics, panelX, panelY, width, Kit.INSET * 2 + INNER_HEIGHT)

        val profile = profile
        val notice = notice()
        // Under a notice nothing answers the mouse, so no tooltip shows through the veil.
        val mx = if (notice == null) mouseX else -1
        val my = if (notice == null) mouseY else -1

        val wellHeight = INNER_HEIGHT - if (profile != null && profile.profiles.size > 1) Kit.BUTTON_HEIGHT + Kit.GAP else 0
        Kit.well(graphics, left, top, LEFT_WIDTH, wellHeight)
        val nameBottom = drawName(graphics, profile)
        (if (profile != null) model else ghost)?.let { m ->
            val boxTop = nameBottom + 2
            val boxBottom = top + wellHeight - 2
            m.draw(graphics, left + 1, boxTop, left + LEFT_WIDTH - 1, boxBottom, ((boxBottom - boxTop) / MODEL_HEIGHTS).roundToInt(), turntable.yaw(now), turntable.pitch)
            if (profile != null && (turntable.dragging || overModel(mouseX.toDouble(), mouseY.toDouble()))) graphics.requestCursor(CursorTypes.RESIZE_ALL)
        }
        if (profile?.stale == true && mouseX in left until left + LEFT_WIDTH && mouseY in nameBottom - LINE until nameBottom) {
            // The last line under the name says the copy is old, and its hover says how old.
            tooltip = listOf(
                text { warning("May be out of date") }.toComponent(),
                text { muted("dragsim.co did not answer, so this is from ${ago(profile.fetchedAt, now)}.") }.toComponent(),
            )
        }

        if (!headless) Kit.label(graphics, fit(shownSummary()?.title?.plain ?: shownTab()?.name.orEmpty(), GRID_WIDTH), contentX, top + (Kit.BUTTON_HEIGHT - 8) / 2)

        val inView = my in viewY until viewY + viewHeight
        val shownProfile = profile ?: SKELETON
        val ghostly = profile == null
        if (tab == OVERVIEW || tab == DUNGEONS || ghostly) {
            graphics.enableScissor(contentX, viewY, contentX + CONTENT_WIDTH, viewY + viewHeight)
            val y = viewY - scroll
            val hover: (List<Component>) -> Unit = { tooltip = it }
            contentHeight = if (tab == DUNGEONS && !ghostly) {
                dungeons(graphics, shownProfile, y, mx, if (inView) my else -1, hover)
            } else {
                overview(graphics, shownProfile, y, mx, if (inView) my else -1, ghostly, hover)
            }
            graphics.disableScissor()
        } else {
            contentHeight = container(graphics, mx, if (inView) my else -1) { drawn, stack ->
                hovered = drawn
                item = stack
            }
        }
        scroll = scroll.coerceIn(0, maxScroll())
        if (maxScroll() > 0) {
            val thumb = thumbHeight()
            Kit.scrollbar(graphics, scrollbarX(), viewY, viewHeight, (viewHeight - 2 - thumb) * scroll / maxScroll(), thumb)
        }
        if (notice != null) drawNotice(graphics, notice, now)

        super.extractRenderState(graphics, mouseX, mouseY, partialTick)
        item?.let { graphics.setTooltipForNextFrame(font, it, mouseX, mouseY) }
        tooltip?.let { graphics.setComponentTooltipForNextFrame(font, it, mouseX, mouseY) }
    }

    /** The veil over what is waiting or empty, and the notice's box in its middle. Loading's dots step in place. */
    private fun drawNotice(graphics: GuiGraphicsExtractor, notice: Notice, now: Long) {
        if (notice.wholeScreen) {
            Kit.veil(graphics, left, top, LEFT_WIDTH + Kit.INSET + CONTENT_WIDTH + Kit.GAP + Kit.SCROLLBAR_WIDTH, INNER_HEIGHT)
        } else {
            Kit.veil(graphics, contentX, viewY, CONTENT_WIDTH + Kit.GAP + Kit.SCROLLBAR_WIDTH, viewHeight)
        }
        val lines = font.split(notice.message, NOTICE_TEXT).take(2)
        val iconWidth = notice.icon?.let { it.width + 4 } ?: 0
        val textWidth = lines.maxOf { font.width(it) }
        val (boxTop, boxBottom) = noticeBox(notice, notice.failure?.let { it.retry || it.selected } == true)
        val boxWidth = iconWidth + textWidth + 16
        val boxX = noticeCenter(notice) - boxWidth / 2
        Kit.well(graphics, boxX, boxTop, boxWidth, boxBottom - boxTop)
        notice.icon?.let { Kit.icon(graphics, it, boxX + 8, boxTop + 5 + (8 - it.height) / 2, notice.color) }
        for ((i, line) in lines.withIndex()) graphics.text(font, line, boxX + 8 + iconWidth, boxTop + 6 + i * LINE, -1, true)
        // The dots go after a fixed "Loading", so the word holds still while they step.
        if (notice.message.string == LOADING) {
            val dots = text { muted(".".repeat((now / DOT_MILLIS % 4).toInt())) }.toComponent()
            graphics.text(font, dots, boxX + 8 + font.width(notice.message), boxTop + 6, -1, true)
        }
    }

    /**
     * The text the screen draws, built once per profile and not each frame. [names] is the rank and name, on one
     * line when they fit and two when not, then the profile's mode when it is not a normal one, then whether it is old.
     */
    private class Texts(
        val names: List<Component>,
        val labels: Map<Progress, String>,
        val stats: List<Pair<Component, Component>>,
        val kills: List<Pair<Component, Component>>,
        val allKills: List<Component>,
        val runs: List<Pair<Component, Component>>,
        val floors: List<List<Triple<String, String, String>>>,
        val classLevels: List<Component>,
        val completions: String,
    )

    private val texts = IdentityHashMap<Any, Texts>()

    private fun texts(profile: Profile?): Texts = texts.getOrPut(profile ?: lookup) {
        if (texts.size > 4) texts.clear()
        val name = text { body(profile?.player?.name ?: lookup.name) }.toComponent()
        val prefix = profile?.player?.rank?.prefix?.takeIf { it.plain.isNotBlank() }?.toComponent()
        val mode = profile?.choice?.mode?.takeIf { it != "NORMAL" }?.let { text { muted(title(it)) }.toComponent() }
        val joined = prefix?.let { Component.empty().append(it).append(" ").append(name) }
        val names = when {
            joined == null -> listOf(name)
            font.width(joined) <= LEFT_WIDTH - 6 -> listOf(joined)
            else -> listOf(prefix, name)
        } + listOfNotNull(mode, profile?.takeIf { it.stale }?.let { text { warning("Out of date") }.toComponent() })
        val dungeons = profile?.dungeons
        val progress = profile?.let { it.skills + it.slayers + it.dungeons.classes }.orEmpty()
        val kills = profile?.topKills.orEmpty().map { kill -> row(text { value(kill.kills) }.toComponent()) { text { body(fit(kill.name, it)) } } }
        fun count(name: String, value: Long) = row(text { value(value) }.toComponent()) { text { muted(fit(name, it)) } }
        Texts(
            names,
            progress.associateWith { fit("${it.name} ${it.level}", PROGRESS_WIDTH - 20 - if (it == dungeons?.selected) Icon.CHECK.width + 3 else 0) },
            profile?.stats.orEmpty().map { stat -> row(stat.value.toComponent()) { text { muted(fit(stat.name, it)) } } },
            kills.take(KILLS_SHOWN),
            listOf(text { body("Top Kills") }.toComponent()) + profile?.topKills.orEmpty().map { text { muted("${it.name}: "); value(it.kills) }.toComponent() },
            listOfNotNull(
                dungeons?.let { count("Secrets", it.secrets) },
                dungeons?.let { count("Completions", it.completions) },
            ),
            dungeons?.floors.orEmpty().partition { !it.master }.toList().map { list ->
                list.map { Triple(it.id, String.format(Locale.US, "%,d", it.completions), it.bestScore?.toString() ?: "-") }
            },
            dungeons?.classes.orEmpty().map { c -> (if (c == dungeons?.selected) text { good("${c.level}") } else text { value("${c.level}") }).toComponent() },
            dungeons?.takeIf { it.completions > 0 }?.let { String.format(Locale.US, "%,d runs", it.completions) }.orEmpty(),
        )
    }

    /** A row of a list: the name, cut to the room [value] leaves it, then the value. */
    private fun row(value: Component, name: (Int) -> Text) =
        name(LIST_WIDTH - 12 - font.width(value)).toComponent() to value

    /** The rank, name, mode and age at the top of the well. Returns the y below them. */
    private fun drawName(graphics: GuiGraphicsExtractor, profile: Profile?): Int {
        val lines = texts(profile).names
        val center = left + LEFT_WIDTH / 2
        for ((i, line) in lines.withIndex()) graphics.text(font, line, center - font.width(line) / 2, top + 5 + i * LINE, -1, true)
        return top + 5 + lines.size * LINE
    }

    private fun icon(id: String) = skillIcons.getOrPut(id) { ItemStack(SKILL_ITEMS[id] ?: Items.BOOK) }

    /** A heading and a bar for each of [list], one below the other. With [ghostly], bones stand in for the words. Returns the y below them. */
    private fun progress(graphics: GuiGraphicsExtractor, profile: Profile, title: String, list: List<Progress>, x: Int, top: Int, mouseX: Int, mouseY: Int, ghostly: Boolean, hover: (List<Component>) -> Unit): Int {
        if (list.isEmpty()) return top
        val texts = texts(profile)
        val selected = profile.dungeons.selected
        var y = top
        Kit.label(graphics, title, x, y, Kit.Ink.MUTED)
        y += SECTION
        for ((i, p) in list.withIndex()) {
            if (ghostly) {
                Kit.bone(graphics, x, y, 16, 16)
                Kit.bone(graphics, x + 20, y, 50 + i * 13 % 40, 7)
            } else {
                graphics.item(icon(p.id), x, y)
                val label = texts.labels.getValue(p)
                Kit.label(graphics, label, x + 20, y)
                // The class the player picked, which a run starts them as.
                if (p == selected) Kit.icon(graphics, Icon.CHECK, x + 23 + font.width(label), y + 1, Role.GOOD.argb())
            }
            Kit.bar(graphics, x + 20, y + 10, PROGRESS_WIDTH - 20, p.fraction, (if (p.maxed) Role.GOOD else Role.VALUE).argb())
            if (mouseX in x until x + PROGRESS_WIDTH && mouseY in y until y + 16) hover(progressTooltip(p, p == selected))
            y += CELL_STEP
        }
        return y + Kit.GAP
    }

    /** A heading and a well of name and value rows. Returns the y below them. */
    private fun rows(graphics: GuiGraphicsExtractor, title: String, list: List<Pair<Component, Component>>, x: Int, top: Int, mouseX: Int, mouseY: Int, ghostly: Boolean, hover: () -> Unit = {}): Int {
        if (list.isEmpty()) return top
        Kit.label(graphics, title, x, top, Kit.Ink.MUTED)
        val y = top + SECTION
        val height = list.size * ROW + 5
        Kit.well(graphics, x, y, LIST_WIDTH, height)
        for ((i, row) in list.withIndex()) {
            val lineY = y + 3 + i * ROW
            if (ghostly) {
                Kit.bone(graphics, x + 4, lineY, 40 + i * 17 % 36, 7)
                Kit.bone(graphics, x + LIST_WIDTH - 34, lineY, 30, 7)
                continue
            }
            graphics.text(font, row.first, x + 4, lineY, -1, true)
            graphics.text(font, row.second, x + LIST_WIDTH - 4 - font.width(row.second), lineY, -1, true)
        }
        if (mouseX in x until x + LIST_WIDTH && mouseY in y until y + height) hover()
        return y + height + Kit.GAP
    }

    /** The five classes side by side, each its icon over its level, the picked one ringed. Returns the y below them. */
    private fun classes(graphics: GuiGraphicsExtractor, profile: Profile, x: Int, top: Int, mouseX: Int, mouseY: Int, ghostly: Boolean, hover: (List<Component>) -> Unit): Int {
        val classes = profile.dungeons.classes
        if (classes.isEmpty()) return top
        val texts = texts(profile)
        Kit.label(graphics, "Dungeons", x, top, Kit.Ink.MUTED)
        if (texts.completions.isNotEmpty()) Kit.label(graphics, texts.completions, x + LIST_WIDTH - font.width(texts.completions), top, Kit.Ink.MUTED)
        val y = top + SECTION
        val height = CLASS_HEIGHT
        Kit.well(graphics, x, y, LIST_WIDTH, height)
        val cell = (LIST_WIDTH - 4) / classes.size
        for ((i, c) in classes.withIndex()) {
            val cx = x + 2 + i * cell
            if (ghostly) {
                Kit.bone(graphics, cx + (cell - 16) / 2, y + 3, 16, 16)
                Kit.bone(graphics, cx + (cell - 10) / 2, y + 21, 10, 7)
                continue
            }
            if (c == profile.dungeons.selected) Kit.ring(graphics, cx, y + 1, cell, height - 2, Role.GOOD.argb())
            graphics.item(icon(c.id), cx + (cell - 16) / 2, y + 3)
            val level = texts.classLevels[i]
            graphics.text(font, level, cx + (cell - font.width(level)) / 2, y + 21, -1, true)
            if (mouseX in cx until cx + cell && mouseY in y until y + height) hover(progressTooltip(c, c == profile.dungeons.selected))
        }
        return y + height + Kit.GAP
    }

    /** Skills and slayers down the left, and stats, the classes and the top kills down the right, all on one screen. */
    private fun overview(graphics: GuiGraphicsExtractor, profile: Profile, top: Int, mouseX: Int, mouseY: Int, ghostly: Boolean, hover: (List<Component>) -> Unit): Int {
        val texts = texts(profile)
        var left = progress(graphics, profile, "Skills", profile.skills, contentX, top, mouseX, mouseY, ghostly, hover)
        left = progress(graphics, profile, "Slayers", profile.slayers, contentX, left, mouseX, mouseY, ghostly, hover)
        val x = contentX + PROGRESS_WIDTH + Kit.GAP
        var right = rows(graphics, "Stats", texts.stats.ifEmpty { if (ghostly) List(profile.stats.size) { EMPTY_ROW } else emptyList() }, x, top, mouseX, mouseY, ghostly)
        right = classes(graphics, profile, x, right, mouseX, mouseY, ghostly, hover)
        val kills = texts.kills.ifEmpty { if (ghostly) List(profile.topKills.size) { EMPTY_ROW } else emptyList() }
        right = rows(graphics, "Top Kills", kills, x, right, mouseX, mouseY, ghostly) { if (texts.allKills.size - 1 > KILLS_SHOWN) hover(texts.allKills) }
        return maxOf(left, right) - Kit.GAP - top
    }

    /**
     * The Catacombs: its level and each class down the left, the picked class ticked, and the runs and each floor's
     * completions and best score down the right, master floors beside the rest.
     */
    private fun dungeons(graphics: GuiGraphicsExtractor, profile: Profile, top: Int, mouseX: Int, mouseY: Int, hover: (List<Component>) -> Unit): Int {
        val texts = texts(profile)
        var left = progress(graphics, profile, "Level", profile.skills.filter { it.id == "catacombs" }, contentX, top, mouseX, mouseY, false, hover)
        left = progress(graphics, profile, "Classes", profile.dungeons.classes, contentX, left, mouseX, mouseY, false, hover)
        val x = contentX + PROGRESS_WIDTH + Kit.GAP
        var right = rows(graphics, "Runs", texts.runs, x, top, mouseX, mouseY, false)
        val columns = texts.floors.filter { it.isNotEmpty() }
        Kit.label(graphics, "Floors", x, right, Kit.Ink.MUTED)
        val y = right + SECTION
        if (columns.isEmpty()) {
            Kit.well(graphics, x, y, LIST_WIDTH, ROW + 5)
            graphics.text(font, NO_FLOORS, x + 4, y + 3, -1, true)
            return maxOf(left, y + ROW + 5) - top
        }
        val height = (columns.maxOf { it.size } + 1) * ROW + 5
        Kit.well(graphics, x, y, LIST_WIDTH, height)
        val width = LIST_WIDTH / 2
        for ((c, column) in columns.withIndex()) {
            val cx = x + c * width
            fun line(row: Int, floor: Component, runs: Component, best: Component) {
                val lineY = y + 3 + row * ROW
                graphics.text(font, floor, cx + 4, lineY, -1, true)
                graphics.text(font, runs, cx + width - 30 - font.width(runs), lineY, -1, true)
                graphics.text(font, best, cx + width - 4 - font.width(best), lineY, -1, true)
            }
            line(0, Component.empty(), FLOOR_RUNS, FLOOR_BEST)
            for ((i, floor) in column.withIndex()) {
                line(i + 1, text { body(floor.first) }.toComponent(), text { value(floor.second) }.toComponent(), text { body(floor.third) }.toComponent())
            }
        }
        right = y + height + Kit.GAP
        return maxOf(left, right) - Kit.GAP - top
    }

    private fun progressTooltip(p: Progress, selected: Boolean): List<Component> {
        val lines = ArrayList<Component>()
        lines += text { body("${p.name} "); value(p.level.toLong()) }.toComponent()
        if (selected) lines += text { good("Selected class") }.toComponent()
        val next = p.xpForNextLevel
        when {
            p.maxed -> lines += text { good("Maxed") }.toComponent()
            next != null -> lines += text { muted("Progress: "); value("${number(p.xpIntoLevel)} / ${number(next)}") }.toComponent()
        }
        lines += text { muted("Total XP: "); value(number(p.totalXp)) }.toComponent()
        return lines
    }

    /**
     * A container's tab: its grid on the left, and beside it its pages, what the player wears when it is the
     * inventory, and a count of what it holds. A grid still loading, failed or hidden is drawn empty for its notice.
     */
    private fun container(graphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, item: (DrawnItem, ItemStack) -> Unit): Int {
        val summary = shownSummary() ?: return 0
        val load = if (summary.hidden) null else lookup.container(summary.id)
        val loaded = (load as? Load.Loaded)?.value
        if (loaded != null && stacked !== loaded) {
            stacked = loaded
            stacks = loaded.items.map { it?.let(::shown) }
        }
        val items = if (loaded != null) stacks else emptyList()
        val columns = summary.columns.coerceIn(1, 9)
        val slots = if (summary.hidden) HIDDEN_SLOTS[summary.kind] ?: columns * 3 else summary.slots
        val rows = (slots + columns - 1) / columns
        // The inventory's last row is the hotbar, set apart as the game sets it apart.
        val hotbar = summary.kind == "inventory" && rows > 1
        val height = rows * Kit.SLOT_SIZE + if (hotbar) Kit.GAP else 0

        graphics.enableScissor(contentX, viewY, contentX + GRID_WIDTH, viewY + viewHeight)
        val top = viewY - scroll
        for (i in 0 until slots) {
            val row = i / columns
            val x = contentX + i % columns * Kit.SLOT_SIZE
            val y = top + row * Kit.SLOT_SIZE + if (hotbar && row == rows - 1) Kit.GAP else 0
            slot(graphics, items.getOrNull(i), x, y, mouseX, mouseY, item)
        }
        graphics.disableScissor()

        val sx = contentX + SIDE_X
        var y = viewY
        val paged = pagedTab()
        if (paged != null) {
            Kit.label(graphics, "Pages", sx, y, Kit.Ink.MUTED)
            val rowsOfPages = (paged.pages.size + PAGES_PER_ROW - 1) / PAGES_PER_ROW
            val shown = page(paged)
            val px = sx + shown % PAGES_PER_ROW * (PAGE_BUTTON + Kit.GAP)
            val py = y + SECTION + shown / PAGES_PER_ROW * (Kit.BUTTON_HEIGHT + Kit.GAP)
            y += SECTION + rowsOfPages * (Kit.BUTTON_HEIGHT + Kit.GAP) + Kit.GAP
            // The page shown is ringed, as a selected tab joins its panel.
            Kit.ring(graphics, px - 1, py - 1, PAGE_BUTTON + 2, Kit.BUTTON_HEIGHT + 2, Kit.theme.accent)
        }
        if (summary.kind == "inventory") {
            Kit.label(graphics, "Equipment", sx, y, Kit.Ink.MUTED)
            y += SECTION
            for ((i, worn) in equipment.withIndex()) slot(graphics, worn.takeUnless { summary.hidden }, sx + i * Kit.SLOT_SIZE, y, mouseX, mouseY, item)
            y += Kit.SLOT_SIZE + Kit.GAP * 2
        }
        if (loaded != null) {
            val used = items.count { it != null }
            val counts = RARITIES.mapNotNull { rarity -> items.count { it?.mark == rarity }.takeIf { it > 0 }?.let { rarity to it } }
            val lines = listOf(row(text { value("$used / ${loaded.slots}") }.toComponent()) { text { muted("Slots used") } }) +
                counts.map { (rarity, n) -> row(text { value(n.toLong()) }.toComponent()) { text { rarity(title(rarity.name), rarity) } } }
            Kit.label(graphics, "Contents", sx, y, Kit.Ink.MUTED)
            y += SECTION
            val box = lines.size * ROW + 5
            Kit.well(graphics, sx, y, SIDE_WIDTH, box)
            for ((i, line) in lines.withIndex()) {
                graphics.text(font, line.first, sx + 4, y + 3 + i * ROW, -1, true)
                graphics.text(font, line.second, sx + SIDE_WIDTH - 4 - font.width(line.second), y + 3 + i * ROW, -1, true)
            }
        }
        return height
    }

    /** One slot of a grid the screen draws: the frame, the rarity mark behind and over the item, and the hover. */
    private fun slot(graphics: GuiGraphicsExtractor, shown: Shown?, x: Int, y: Int, mouseX: Int, mouseY: Int, hover: (DrawnItem, ItemStack) -> Unit) {
        Kit.slot(graphics, x, y)
        shown ?: return
        val mark = shown.mark?.takeIf { Slots.marks(it) }
        if (mark != null) Slots.rarityBack(graphics, mark, x + 1, y + 1)
        graphics.item(shown.stack, x + 1, y + 1)
        graphics.itemDecorations(font, shown.stack, x + 1, y + 1)
        val over = mouseX in x until x + Kit.SLOT_SIZE && mouseY in y until y + Kit.SLOT_SIZE
        if (over) Kit.slotHover(graphics, x + 1, y + 1)
        if (mark != null) Slots.rarityFront(graphics, mark, x + 1, y + 1)
        if (over) hover(shown.item, shown.stack)
    }

    private fun maxScroll() = (contentHeight - viewHeight).coerceAtLeast(0)

    /** Whether the tab shown needs its scrollbar. The overview and the dungeons are laid out to fit without one. */
    val overflows get() = maxScroll() > 0

    private fun thumbHeight() = ((viewHeight - 2) * viewHeight / contentHeight.coerceAtLeast(viewHeight)).coerceAtLeast(8)

    private fun scrollbarX() = contentX + CONTENT_WIDTH + Kit.GAP

    private fun overModel(x: Double, y: Double) = model != null && profile != null && x >= left && x < left + LEFT_WIDTH && y >= top + 14 && y < top + INNER_HEIGHT - Kit.BUTTON_HEIGHT - Kit.GAP

    override fun mouseClicked(event: MouseButtonEvent, doubleClick: Boolean): Boolean {
        if (super.mouseClicked(event, doubleClick)) return true
        if (event.button() != 0) return false
        if (maxScroll() > 0 && event.x >= scrollbarX() && event.x < scrollbarX() + Kit.SCROLLBAR_WIDTH && event.y >= viewY && event.y < viewY + viewHeight) {
            scrolling = true
            dragThumb(event.y)
            return true
        }
        if (overModel(event.x, event.y)) {
            turntable.press(event.x, event.y, System.currentTimeMillis())
            return true
        }
        return false
    }

    override fun mouseDragged(event: MouseButtonEvent, dx: Double, dy: Double): Boolean {
        if (scrolling) return true.also { dragThumb(event.y) }
        if (turntable.dragging) return true.also { turntable.drag(event.x, event.y) }
        return super.mouseDragged(event, dx, dy)
    }

    override fun mouseReleased(event: MouseButtonEvent): Boolean {
        scrolling = false
        turntable.release(System.currentTimeMillis())
        return super.mouseReleased(event)
    }

    override fun mouseScrolled(mouseX: Double, mouseY: Double, scrollX: Double, scrollY: Double): Boolean {
        if (super.mouseScrolled(mouseX, mouseY, scrollX, scrollY)) return true
        if (scrollY == 0.0 || mouseX < contentX) return false
        scrollBy(if (scrollY > 0) -SCROLL_STEP else SCROLL_STEP)
        return true
    }

    override fun keyPressed(event: KeyEvent): Boolean {
        val now = System.currentTimeMillis()
        if (event.isEscape && turntable.cancel(now)) return true
        if (overModel(mouseX, mouseY)) {
            when {
                event.isLeft -> return true.also { turntable.turn(-Turntable.KEY_YAW, 0f, now) }
                event.isRight -> return true.also { turntable.turn(Turntable.KEY_YAW, 0f, now) }
                event.isUp -> return true.also { turntable.turn(0f, -Turntable.KEY_PITCH, now) }
                event.isDown -> return true.also { turntable.turn(0f, Turntable.KEY_PITCH, now) }
            }
        }
        when (event.key()) {
            GLFW.GLFW_KEY_PAGE_UP -> scrollBy(-(viewHeight - SCROLL_STEP))
            GLFW.GLFW_KEY_PAGE_DOWN -> scrollBy(viewHeight - SCROLL_STEP)
            GLFW.GLFW_KEY_HOME -> scroll = 0
            GLFW.GLFW_KEY_END -> scroll = maxScroll()
            else -> return super.keyPressed(event)
        }
        return true
    }

    private fun scrollBy(by: Int) {
        scroll = (scroll + by).coerceIn(0, maxScroll())
    }

    /** Puts the thumb's middle under the mouse. */
    private fun dragThumb(mouseY: Double) {
        val thumb = thumbHeight()
        val at = ((mouseY - viewY - 1 - thumb / 2.0) / (viewHeight - 2 - thumb)).coerceIn(0.0, 1.0)
        scroll = (at * maxScroll()).roundToInt()
    }

    /** A profile in the picker by its name. The line under the player's name says its mode. */
    private fun choiceName(choice: ProfileChoice) = choice.name.plain

    private companion object {
        const val OVERVIEW = "overview"
        const val DUNGEONS = "dungeons"
        const val KILLS_SHOWN = 5
        const val LOADING = "Loading"
        const val DOT_MILLIS = 350L
        const val NOTICE_TEXT = 220

        // The classes' row: an icon and a level under it, inside the well's edge.
        const val CLASS_HEIGHT = 32
        val FLOOR_RUNS: Component = text { muted("Runs") }.toComponent()
        val FLOOR_BEST: Component = text { muted("Best") }.toComponent()
        val NO_FLOORS: Component = text { muted("No floors finished yet.") }.toComponent()
        val EMPTY_ROW: Pair<Component, Component> = Component.empty() to Component.empty()

        // How many of the player's heights the model's box holds, which leaves room above the head as it tips.
        const val MODEL_HEIGHTS = 2.8
        val LOG = LoggerFactory.getLogger("dsmod")

        /** A mode such as `IRONMAN` as the player reads it: Ironman. An unknown one is shown the same way. */
        fun title(mode: String) = mode.lowercase(Locale.US).split('_').joinToString(" ") { word -> word.replaceFirstChar { it.titlecase(Locale.US) } }

        fun number(value: Double) = String.format(Locale.US, "%,d", value.toLong())

        /** How long ago [iso] was, in the largest unit that is at least one. */
        fun ago(iso: String, now: Long): String {
            val then = runCatching { Instant.parse(iso).toEpochMilli() }.getOrNull() ?: return "earlier"
            val minutes = Duration.ofMillis(now - then).toMinutes()
            fun count(n: Long, unit: String) = "$n $unit${if (n == 1L) "" else "s"} ago"
            return when {
                minutes < 1 -> "just now"
                minutes < 60 -> count(minutes, "minute")
                minutes < 60 * 24 -> count(minutes / 60, "hour")
                else -> count(minutes / 60 / 24, "day")
            }
        }
    }
}
