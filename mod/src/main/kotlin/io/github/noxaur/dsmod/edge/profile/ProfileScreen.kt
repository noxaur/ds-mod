package io.github.noxaur.dsmod.edge.profile

import com.mojang.blaze3d.platform.cursor.CursorTypes
import io.github.noxaur.dsmod.core.game.ServerSpan
import io.github.noxaur.dsmod.core.game.ServerText
import io.github.noxaur.dsmod.core.profile.Container
import io.github.noxaur.dsmod.core.profile.ContainerSummary
import io.github.noxaur.dsmod.core.profile.DrawnItem
import io.github.noxaur.dsmod.core.profile.Dungeons
import io.github.noxaur.dsmod.core.profile.Failure
import io.github.noxaur.dsmod.core.profile.Floor
import io.github.noxaur.dsmod.core.profile.Kills
import io.github.noxaur.dsmod.core.profile.Load
import io.github.noxaur.dsmod.core.profile.Pet
import io.github.noxaur.dsmod.core.profile.Player
import io.github.noxaur.dsmod.core.profile.Profile
import io.github.noxaur.dsmod.core.profile.ProfileChoice
import io.github.noxaur.dsmod.core.profile.ProfileLookup
import io.github.noxaur.dsmod.core.profile.Progress
import io.github.noxaur.dsmod.core.profile.Rank
import io.github.noxaur.dsmod.core.profile.Stat
import io.github.noxaur.dsmod.core.profile.StatValue
import io.github.noxaur.dsmod.core.text.Role
import io.github.noxaur.dsmod.core.text.TextBuilder
import io.github.noxaur.dsmod.core.text.amount
import io.github.noxaur.dsmod.core.text.fixed
import io.github.noxaur.dsmod.core.text.grouped
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
import kotlin.math.floor
import kotlin.math.roundToInt

private const val LEFT_WIDTH = 96
private const val CONTENT_WIDTH = 296
private const val INNER_HEIGHT = 184
private const val HEADER = 20
private const val VIEW_WIDTH = CONTENT_WIDTH + Kit.GAP + Kit.SCROLLBAR_WIDTH
private const val PANEL_WIDTH = Kit.INSET * 2 + LEFT_WIDTH + Kit.INSET + VIEW_WIDTH
private const val PANEL_HEIGHT = Kit.INSET * 2 + INNER_HEIGHT

private const val PROGRESS_WIDTH = 144
private const val LIST_WIDTH = CONTENT_WIDTH - PROGRESS_WIDTH - Kit.GAP
private const val HALF_WIDTH = (CONTENT_WIDTH - Kit.GAP) / 2
private const val CELL_STEP = 18
private const val LINE = 10
private const val ROW = 9
private const val SECTION = 12
private const val COLUMN_GAP = 8
private const val SCROLL_STEP = 18

private const val GRID_WIDTH = 9 * Kit.SLOT_SIZE
private const val SIDE_X = GRID_WIDTH + 8
private const val SIDE_WIDTH = CONTENT_WIDTH - SIDE_X
private const val PAGE_BUTTON = 22
private const val PAGES_PER_ROW = 5

private fun pageX(i: Int) = i % PAGES_PER_ROW * (PAGE_BUTTON + Kit.GAP)

private fun pageY(i: Int) = SECTION + i / PAGES_PER_ROW * (Kit.BUTTON_HEIGHT + Kit.GAP)

private fun inside(mouseX: Int, mouseY: Int, x: Int, y: Int, width: Int, height: Int) = mouseX in x until x + width && mouseY in y until y + height

private fun extent(bottom: Int, top: Int) = bottom - Kit.GAP - top

private val Profile.playerUuid get() = runCatching { UUID.fromString(player.uuid) }.getOrNull()

private val TAB_ITEMS = mapOf(
    "inventory" to Items.CHEST,
    "ender_chest" to Items.ENDER_CHEST,
    "storage" to Items.BARREL,
    "wardrobe" to Items.LEATHER_CHESTPLATE,
    "accessories" to Items.BUNDLE,
    "pets" to Items.BONE,
)

/** @keep The backend sends no size for a hidden container, so its grid is drawn at the game's usual size. */
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

private val SKELETON = run {
    fun progress(n: Int) = List(n) { Progress("", "", 0, 1, 0.0, 0.0, null) }
    Profile(
        snapshot = "", fetchedAt = "", player = Player("", "", Rank("", ServerText(emptyList()))), profileId = "",
        skills = progress(5), slayers = progress(3),
        stats = List(7) { Stat("", "", ServerText(emptyList())) },
        topKills = List(10) { Kills("", "", 0) },
        dungeons = Dungeons(),
    )
}

class ProfileScreen(private val lookup: ProfileLookup, private val parent: Screen?) : Screen(text { body("Profile Viewer") }.toComponent()) {
    private val turntable = Turntable()
    private var model: ProfileModel? = null
    private var dressed: Profile? = null
    private var equipment = emptyList<Shown?>()

    private var built: Load<Profile>? = null
    private var last: Profile? = null

    private var tab = OVERVIEW
    private val pages = HashMap<String, Int>()

    // @keep A switch or a new snapshot brings another container under the same id, so the shown container's items are matched by the answer, not by the id.
    private var stacked: Container? = null
    private var stacks = emptyList<Shown?>()
    private var tabs = emptyList<Tab>()
    private val skillIcons = HashMap<String, ItemStack>()

    private var frame = Kit.Frame(0, 0)
    private val panelX get() = frame.x
    private val panelY get() = frame.y
    private val left get() = frame.left
    private val top get() = frame.top

    var contentX = 0
        private set
    val viewY get() = if (headless) top else top + HEADER

    private val headless get() = tab in SHEETS || profile == null
    private val viewHeight get() = INNER_HEIGHT - (viewY - top)
    private var scroll = 0
    private var contentHeight = 0
    private var scrolling = false

    private class Tab(val kind: String, val name: String, val icon: ItemStack, val pages: List<ContainerSummary>)

    private class Shown(val stack: ItemStack, val mark: Role?)

    private class Notice(val message: Component, val icon: Icon?, val color: Int, val wholeScreen: Boolean, val failure: Failure? = null)

    private class Row(val name: (Int) -> Component, val values: List<Component>, val tip: List<Component>? = null)

    private class Table(
        val title: String,
        val width: Int,
        val rows: List<List<Component>>,
        val columns: IntArray,
        val header: List<Component>?,
        val note: String,
        val noteWidth: Int,
        val gap: Int,
        val tips: List<List<Component>?>,
    ) {
        fun height(limit: Int = rows.size) = SECTION + ((if (header != null) 1 else 0) + rows.size.coerceAtMost(limit)).coerceAtLeast(1) * ROW + 5 + Kit.GAP
    }

    private val fetched get() = (lookup.state as? Load.Loaded)?.value

    private val profile get() = fetched ?: last

    private fun nameOf(profile: Profile?) = profile?.player?.name ?: lookup.name

    override fun init() {
        frame = Kit.underTabs(width, height, PANEL_WIDTH, PANEL_HEIGHT)
        contentX = left + LEFT_WIDTH + Kit.INSET
        built = lookup.state
        fetched?.let { last = it }
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
            if (profile.choices.size > 1) {
                addRenderableWidget(
                    KitCycleButton(left, top + INNER_HEIGHT - Kit.BUTTON_HEIGHT, LEFT_WIDTH, "Profile", profile.choices, picked(profile), ::choiceName) {
                        lookup.switch(it.id)
                    },
                )
            }
            pagedTab()?.let { paged ->
                for (i in paged.pages.indices) {
                    addRenderableWidget(KitButton(contentX + SIDE_X + pageX(i), viewY + pageY(i), "${i + 1}", { turn(paged, i) }, PAGE_BUTTON)).setTooltip(Tooltip.create(paged.pages[i].title.toComponent()))
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

    private fun picked(profile: Profile) = profile.choices.firstOrNull { it.id == lookup.asked && lookup.state is Load.Loading } ?: profile.choice

    private fun tabs(profile: Profile): List<Tab> {
        val head = ItemStack(Items.PLAYER_HEAD).apply {
            profile.playerUuid?.let { set(DataComponents.PROFILE, ResolvableProfile.createUnresolved(it)) }
        }
        fun sheet(kind: String, name: String, icon: ItemStack, shown: Boolean) = Tab(kind, name, icon, emptyList()).takeIf { shown }
        val stats = profile.playerStats
        val first = listOfNotNull(
            Tab(OVERVIEW, "Overview", head, emptyList()),
            sheet(STATS, "Player Stats", ItemStack(Items.GOLDEN_APPLE), (stats.base + stats.additive + stats.multiplicative + stats.increaseMax).isNotEmpty()),
            sheet(DUNGEONS, "Dungeons", ItemStack(Items.WITHER_SKELETON_SKULL), profile.dungeons != null),
            sheet(MINING, "Mining", ItemStack(Items.GOLDEN_PICKAXE), profile.hotd != null || profile.forge.isNotEmpty()),
        )
        val kinds = profile.containers.groupBy { it.kind }.map { (kind, pages) ->
            val name = when (kind) {
                "storage" -> "Storage"
                "wardrobe" -> "Wardrobe"
                else -> pages[0].title.plain
            }
            Tab(kind, name, ItemStack(TAB_ITEMS[kind] ?: Items.PAPER), pages)
        }
        val last = listOfNotNull(
            sheet(BESTIARY, "Bestiary", ItemStack(Items.ZOMBIE_HEAD), profile.bestiary.isNotEmpty()),
            sheet(COLLECTIONS, "Collections", ItemStack(Items.PAINTING), profile.collections.isNotEmpty() || profile.rngMeters.isNotEmpty()),
        )
        return first + kinds + last
    }

    private fun dress(profile: Profile) {
        dressed = profile
        val worn = profile.equipment
        equipment = listOf(worn.helmet, worn.chestplate, worn.leggings, worn.boots, worn.held).map { it?.let(::shown) }
        val level = minecraft.level ?: return
        val uuid = profile.playerUuid ?: return
        val current = model?.takeIf { it.uuid == uuid } ?: ProfileModel(level, uuid, profile.player.name).also { model = it }
        current.dress(worn)
    }

    private val ghost by lazy { minecraft.level?.let { ProfileModel(it, UUID(0, 0), "Steve") } }

    private fun shown(item: DrawnItem): Shown {
        val rarity = item.rarity?.let { name -> Role.RARITIES.firstOrNull { it.name == name } }
        return Shown(item.toStack(), rarity)
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
        if (summary.hidden || summary.id == PETS) return null
        return lookup.container(summary.id)
    }

    override fun onClose() = minecraft.gui.setScreen(parent)

    override fun tick() {
        if (lookup.state !== built || failedContainer() != builtFailure) rebuildWidgets()
    }

    private var builtFailure: Boolean = false

    private fun failedContainer() = shownContainer() is Load.Failed

    override fun rebuildWidgets() {
        builtFailure = failedContainer()
        super.rebuildWidgets()
    }

    private fun notice(): Notice? {
        val name = nameOf(profile)
        when (val state = lookup.state) {
            is Load.Loading -> return loading(profile == null)
            is Load.Failed -> return failed(state.failure, profile == null)
            is Load.Loaded -> Unit
        }
        val summary = shownSummary() ?: return null
        if (summary.hidden) return Notice(text { body("$name keeps their inventory hidden.") }.toComponent(), Icon.LOCK, Role.MUTED.argb(), wholeScreen = false)
        return when (val load = shownContainer()) {
            is Load.Loading -> loading(false)
            is Load.Failed -> failed(load.failure, false)
            is Load.Loaded, null -> null
        }
    }

    private fun loading(wholeScreen: Boolean) = Notice(text { muted(LOADING) }.toComponent(), null, 0, wholeScreen)

    private fun failed(failure: Failure, wholeScreen: Boolean) = Notice(failure.message.toComponent(), Icon.WARN, Role.BAD.argb(), wholeScreen, failure)

    private fun noticeCenter(notice: Notice) = if (notice.wholeScreen) left + (LEFT_WIDTH + Kit.INSET + CONTENT_WIDTH) / 2 else contentX + CONTENT_WIDTH / 2

    private fun noticeBox(notice: Notice, buttons: Boolean): Pair<Int, Int> {
        val lines = font.split(notice.message, NOTICE_TEXT).size.coerceIn(1, 2)
        val height = lines * LINE + 10
        val total = height + if (buttons) Kit.GAP + Kit.BUTTON_HEIGHT else 0
        val (areaTop, areaHeight) = if (notice.wholeScreen) top to INNER_HEIGHT else viewY to viewHeight
        val boxTop = areaTop + (areaHeight - total) / 2
        return boxTop to boxTop + height
    }

    private var mouseX = 0.0
    private var mouseY = 0.0

    override fun extractRenderState(graphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, partialTick: Float) {
        this.mouseX = mouseX.toDouble()
        this.mouseY = mouseY.toDouble()
        val now = System.currentTimeMillis()
        var tooltip: List<Component>? = null
        var item: ItemStack? = null
        Kit.panel(graphics, panelX, panelY, PANEL_WIDTH, PANEL_HEIGHT)

        val profile = profile
        val notice = notice()
        val mx = if (notice == null) mouseX else -1
        val my = if (notice == null) mouseY else -1

        val wellHeight = INNER_HEIGHT - if (profile != null && profile.choices.size > 1) Kit.BUTTON_HEIGHT + Kit.GAP else 0
        Kit.well(graphics, left, top, LEFT_WIDTH, wellHeight)
        val nameBottom = drawName(graphics, profile)
        (if (profile != null) model else ghost)?.let { m ->
            val boxTop = nameBottom + 2
            val boxBottom = top + wellHeight - 2
            m.draw(graphics, left + 1, boxTop, left + LEFT_WIDTH - 1, boxBottom, ((boxBottom - boxTop) / MODEL_HEIGHTS).roundToInt(), turntable.yaw(now), turntable.pitch)
            if (profile != null && (turntable.dragging || overModel(mouseX.toDouble(), mouseY.toDouble()))) graphics.requestCursor(CursorTypes.RESIZE_ALL)
        }
        if (profile != null && inside(mouseX, mouseY, left, top, LEFT_WIDTH, nameBottom - top)) tooltip = ages(profile, now)

        if (!headless) Kit.label(graphics, fit(shownSummary()?.title?.plain ?: shownTab()?.name.orEmpty(), GRID_WIDTH), contentX, top + (Kit.BUTTON_HEIGHT - 8) / 2)

        val inView = my in viewY until viewY + viewHeight
        val vy = if (inView) my else -1
        val hover: (List<Component>) -> Unit = { tooltip = it }
        if (headless) {
            graphics.enableScissor(contentX, viewY, contentX + CONTENT_WIDTH, viewY + viewHeight)
            val y = viewY - scroll
            contentHeight = when {
                profile == null -> overview(graphics, SKELETON, y, mx, vy, true, hover)
                tab == STATS -> playerStats(graphics, profile, y, mx, vy, hover)
                tab == DUNGEONS -> dungeons(graphics, profile, y, mx, vy, hover)
                tab == MINING -> mining(graphics, profile, y, now)
                tab == BESTIARY -> extent(side(graphics, texts(profile).bestiary, contentX, y), y)
                tab == COLLECTIONS -> collections(graphics, profile, y)
                else -> overview(graphics, profile, y, mx, vy, false, hover)
            }
            graphics.disableScissor()
        } else {
            contentHeight = container(graphics, profile, mx, vy, hover) { item = it }
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

    private fun ages(profile: Profile, now: Long): List<Component> = listOfNotNull(
        text { body(profile.player.name) }.toComponent(),
        profile.savedAt?.let { text { muted("Saved by dragsim.co "); value(ago(it, now)) }.toComponent() },
        text { muted("Read by ds-mod "); value(ago(profile.fetchedAt, now)) }.toComponent(),
        if (profile.stale) text { warning("dragsim.co did not answer, so this may be out of date.") }.toComponent() else null,
    )

    private fun drawNotice(graphics: GuiGraphicsExtractor, notice: Notice, now: Long) {
        if (notice.wholeScreen) {
            Kit.veil(graphics, left, top, LEFT_WIDTH + Kit.INSET + VIEW_WIDTH, INNER_HEIGHT)
        } else {
            Kit.veil(graphics, contentX, viewY, VIEW_WIDTH, viewHeight)
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
        if (notice.message.string == LOADING) {
            val dots = text { muted(".".repeat((now / DOT_MILLIS % 4).toInt())) }.toComponent()
            graphics.text(font, dots, boxX + 8 + font.width(notice.message), boxTop + 6, -1, true)
        }
    }

    private inner class Texts(private val profile: Profile?) {
        private val shown = profile ?: SKELETON

        val names: List<Component> = run {
            val name = text { body(nameOf(profile)) }.toComponent()
            val prefix = profile?.player?.rank?.prefix?.takeIf { it.plain.isNotBlank() }?.toComponent()
            val mode = profile?.profileMode?.takeIf { it != "NORMAL" }?.let { text { muted(title(it)) }.toComponent() }
            val joined = prefix?.let { Component.empty().append(it).append(" ").append(name) }
            when {
                joined == null -> listOf(name)
                font.width(joined) <= LEFT_WIDTH - 6 -> listOf(joined)
                else -> listOf(prefix, name)
            } + listOfNotNull(mode, profile?.takeIf { it.stale }?.let { text { warning("Out of date") }.toComponent() })
        }

        val dungeons = shown.dungeons

        val classes = dungeons?.allClasses.orEmpty()

        val catacombs = shown.skills.filter { it.id == "catacombs" } + classes

        val labels: Map<Progress, String> = (shown.skills + shown.slayers + classes).associateWith {
            fit("${it.name} ${it.level}", PROGRESS_WIDTH - 20 - if (dungeons?.picked(it) == true) Icon.CHECK.width + 3 else 0)
        }

        val classLevels: List<String> = classes.map { it.level.toString() }

        val completions = dungeons?.takeIf { it.completions > 0 }?.let { "${grouped(it.completions)} runs" }.orEmpty()

        val stats = table("Stats", LIST_WIDTH, shown.stats.map { stat -> Row(muted(stat.name), listOf(stat.value.toComponent())) })

        val kills = table("Top Kills", LIST_WIDTH, shown.topKills.map { Row(body(it.name), listOf(text { value(it.kills) }.toComponent())) })

        val allKills = listOf(text { body("Top Kills") }.toComponent()) + shown.topKills.map { text { muted("${it.name}: "); value(it.kills) }.toComponent() }

        val playerStats by lazy {
            val sheet = shown.playerStats
            val groups = listOf("Base" to sheet.base, "Additive" to sheet.additive, "Multiplicative" to sheet.multiplicative, "Increase Max" to sheet.increaseMax)
            val rows = groups.flatMap { it.second }.distinctBy { it.id }.map { stat ->
                val base = sheet.base.firstOrNull { it.id == stat.id }?.value
                val tip = listOf(colored(stat)(Int.MAX_VALUE)) + groups.mapNotNull { (name, group) ->
                    group.firstOrNull { it.id == stat.id }?.let { text { muted("$name: "); value(amount(it.value)) }.toComponent() }
                }
                Row(colored(stat), listOf((if (base != null) text { value(amount(base)) } else text { muted("0") }).toComponent()), tip)
            }
            halves("Player Stats", CONTENT_WIDTH, rows, splitAt = 1)
        }

        val runs by lazy {
            val d = dungeons ?: Dungeons()
            table(
                "Runs", LIST_WIDTH,
                listOf("Completions" to d.completions.toDouble(), "Secrets" to d.secrets, "Zombie Kills" to d.zombieKills, "Skeletor Kills" to d.skeletorKills)
                    .map { (name, n) -> figure(muted(name), n) },
            )
        }

        val floors by lazy {
            val list = dungeons?.floors.orEmpty()
            if (list.isEmpty()) return@lazy table("Floors", LIST_WIDTH, emptyList(), empty = "No floors finished yet.")
            fun number(floor: Floor) = if (floor.id.length > 1 && floor.id[0] in "FM") floor.id.drop(1) else floor.id
            val master = list.any { it.master }
            fun cells(floor: Floor?) = listOf(
                if (floor == null) text { muted("-") } else text { value(floor.completions) },
                text { body(floor?.bestScore?.toString() ?: "-") },
            ).map { it.toComponent() }
            val rows = list.map(::number).distinct().map { n ->
                Row(body(n), cells(list.firstOrNull { !it.master && number(it) == n }) + if (master) cells(list.firstOrNull { it.master && number(it) == n }) else emptyList())
            }
            val header = listOf("", "Runs", "Best") + if (master) listOf("Runs", "Best") else emptyList()
            table("Floors", LIST_WIDTH, rows, header = header, note = if (master) "Master Mode" else "", gap = 6)
        }

        val essence by lazy {
            halves("Essence", PROGRESS_WIDTH, dungeons?.essence.orEmpty().map { figure(body(it.name.removeSuffix(" Essence")), it.amount) })
        }

        val heart by lazy {
            val hotd = shown.hotd ?: return@lazy null
            val rows = listOf("Tier" to hotd.tier, "Experience" to hotd.exp, "Core" to hotd.core, "Ender" to hotd.ender, "Void" to hotd.void, "Tokens" to hotd.tokens, "Resets" to hotd.resets)
                .map { (name, n) -> figure(muted(name), n) } +
                listOfNotNull(hotd.ability?.let { Row(muted("Ability"), listOf(text { body(it.name) }.toComponent())) })
            table("Heart of the Dragon", PROGRESS_WIDTH, rows)
        }

        val nodes by lazy {
            shown.hotd?.let { hotd ->
                table("Nodes", LIST_WIDTH, hotd.nodes.map { figure(body(it.name), it.level) }, empty = "No nodes yet.")
            }
        }

        val bestiary by lazy {
            val total = shown.bestiary.sumOf { it.kills }
            val rows = shown.bestiary.map { mob ->
                if (mob.kills == 0L) Row(muted(mob.name), listOf(text { muted("0") }.toComponent())) else Row(body(mob.name), listOf(text { value(mob.kills) }.toComponent()))
            }
            halves("Bestiary", CONTENT_WIDTH, rows, note = "${grouped(total)} kills")
        }

        val collections by lazy { counts("Collections", shown.collections.map { it.name to it.amount }) }

        val meters by lazy { counts("RNG Meters", shown.rngMeters.map { it.name to it.progress }) }

        val pets: List<Shown?> by lazy { shown.pets.map { shown(it.item) } }

        val activePet by lazy {
            val pet = shown.pets.firstOrNull { it.active } ?: return@lazy table("Active Pet", SIDE_WIDTH, emptyList(), empty = "None.")
            table("Active Pet", SIDE_WIDTH, petRows(pet))
        }

        private var forgeAt = -1L
        private var forgeTable: Table? = null

        fun forge(now: Long): Table? {
            if (shown.forge.isEmpty()) return null
            val second = now / 1000
            if (second != forgeAt) {
                forgeAt = second
                val rows = shown.forge.sortedBy { it.slot }.map { slot ->
                    val left = slot.finishesAt - now
                    val status = when {
                        slot.stage == "READY" || left <= 0 -> text { good("Ready") }
                        slot.stage == "RUNNING" -> text { value(remaining(left)) }
                        else -> text { muted(title(slot.stage)) }
                    }
                    Row(body(slot.name), listOf(status.toComponent()))
                }
                forgeTable = table("Forge", PROGRESS_WIDTH, rows)
            }
            return forgeTable
        }
    }

    private fun petRows(pet: Pet): List<Row> {
        val rarity = Role.RARITIES.firstOrNull { it.name == pet.rarity }
        fun fact(name: String, value: String) = Row(muted(name), listOf(text { body(fit(value, SIDE_WIDTH - 8 - font.width(name) - COLUMN_GAP)) }.toComponent()))
        return listOfNotNull(
            Row({ w -> (if (rarity != null) text { rarity(fit(pet.name, w), rarity) } else text { body(fit(pet.name, w)) }).toComponent() }, emptyList()),
            pet.category?.let { fact("Category", it) },
            fact("Level", pet.level?.let { "$it / ${pet.maxLevel ?: "?"}" } ?: "Unknown"),
            pet.xpForNextLevel?.takeIf { it > 0 }?.let { next ->
                val exact = text { muted("Progress: "); value("${grouped(pet.xpIntoLevel.toLong())} / ${grouped(next.toLong())}") }.toComponent()
                Row(muted("Progress"), listOf(text { value("${fixed(floor(pet.xpIntoLevel / next * 1000) / 10, 1)}%") }.toComponent()), listOf(exact))
            },
            Row(muted("Total XP"), listOf(text { value(amount(pet.totalXp)) }.toComponent())),
            pet.heldItem?.let { fact("Held", it.name) },
            pet.skinOverride?.let { fact("Skin", it.name.removeSuffix(" Skin")) },
            Row(muted("Candies"), listOf(text { value(pet.candiesUsed.toLong()) }.toComponent())),
        ) + pet.perks.map { perk -> Row(colored(perk), listOf(text { value("+${amount(perk.value)}/lvl") }.toComponent())) } +
            pet.description.map { line -> Row({ _ -> line.toComponent() }, emptyList()) }
    }

    private val texts = IdentityHashMap<Any, Texts>()

    private fun texts(profile: Profile?): Texts = texts.getOrPut(profile ?: lookup) {
        if (texts.size > 4) texts.clear()
        Texts(profile)
    }

    private fun body(name: String) = named(name, TextBuilder::body)

    private fun muted(name: String) = named(name, TextBuilder::muted)

    private fun named(name: String, role: TextBuilder.(String) -> Unit): (Int) -> Component = { w -> text { role(fit(name, w)) }.toComponent() }

    private fun counts(title: String, entries: List<Pair<String, Double>>) = table(title, HALF_WIDTH, entries.map { (name, n) -> figure(body(name), n) }, empty = "None yet.")

    private fun figure(name: (Int) -> Component, n: Double) = Row(name, listOf(text { value(amount(n)) }.toComponent()))

    private fun colored(stat: StatValue): (Int) -> Component = { w ->
        val rgb = stat.rgb
        if (rgb == null) text { muted(fit(stat.name, w)) }.toComponent() else ServerText(listOf(ServerSpan(fit(stat.name, w), rgb))).toComponent()
    }

    private fun table(title: String, width: Int, rows: List<Row>, header: List<String>? = null, note: String = "", empty: String? = null, gap: Int = COLUMN_GAP): Table {
        val head = header?.map { text { muted(it) }.toComponent() }
        val count = maxOf(rows.maxOfOrNull { it.values.size } ?: 0, (head?.size ?: 1) - 1)
        val columns = IntArray(count) { i -> maxOf(head?.getOrNull(i + 1)?.let(font::width) ?: 0, rows.maxOfOrNull { r -> r.values.getOrNull(i)?.let(font::width) ?: 0 } ?: 0) }
        val nameWidth = width - 8 - columns.sum() - gap * count
        val built = rows.map { row ->
            val room = if (count == 1) width - 8 - row.values.sumOf { font.width(it) + gap } else nameWidth
            listOf(row.name(room)) + row.values
        }.ifEmpty { listOfNotNull(empty?.let { listOf(text { muted(it) }.toComponent()) }) }
        return Table(title, width, built, columns, head, note, font.width(note), gap, rows.map { it.tip })
    }

    private fun halves(title: String, width: Int, rows: List<Row>, note: String = "", splitAt: Int = SPLIT_AT): List<Table> {
        if (rows.size <= splitAt) return listOf(table(title, width, rows, note = note, empty = "None yet."))
        val half = (width - Kit.GAP) / 2
        val cut = (rows.size + 1) / 2
        return listOf(table(title, half, rows.take(cut)), table("", half, rows.drop(cut), note = note))
    }

    private fun drawName(graphics: GuiGraphicsExtractor, profile: Profile?): Int {
        val lines = texts(profile).names
        val center = left + LEFT_WIDTH / 2
        for ((i, line) in lines.withIndex()) graphics.text(font, line, center - font.width(line) / 2, top + 5 + i * LINE, -1, true)
        return top + 5 + lines.size * LINE
    }

    private fun icon(id: String) = skillIcons.getOrPut(id) { ItemStack(SKILL_ITEMS[id.lowercase(Locale.US)] ?: Items.BOOK) }

    private fun progress(graphics: GuiGraphicsExtractor, texts: Texts, title: String, list: List<Progress>, x: Int, top: Int, mouseX: Int, mouseY: Int, ghostly: Boolean, hover: (List<Component>) -> Unit): Int {
        if (list.isEmpty()) return top
        var y = top
        Kit.label(graphics, title, x, y, Kit.Ink.MUTED)
        y += SECTION
        for ((i, p) in list.withIndex()) {
            val picked = texts.dungeons?.picked(p) == true
            if (ghostly) {
                Kit.bone(graphics, x, y, 16, 16)
                Kit.bone(graphics, x + 20, y, 50 + i * 13 % 40, 7)
            } else {
                graphics.item(icon(p.id), x, y)
                val label = texts.labels.getValue(p)
                Kit.label(graphics, label, x + 20, y)
                if (picked) Kit.icon(graphics, Icon.CHECK, x + 23 + font.width(label), y + 1, Role.GOOD.argb())
            }
            Kit.bar(graphics, x + 20, y + 10, PROGRESS_WIDTH - 20, p.fraction, (if (p.maxed) Role.GOOD else Role.VALUE).argb())
            if (inside(mouseX, mouseY, x, y, PROGRESS_WIDTH, 16)) hover(progressTooltip(p, picked))
            y += CELL_STEP
        }
        return y + Kit.GAP
    }

    private fun draw(graphics: GuiGraphicsExtractor, t: Table, x: Int, top: Int, mouseX: Int = -1, mouseY: Int = -1, ghostly: Boolean = false, limit: Int = t.rows.size, tip: List<Component>? = null, hover: (List<Component>) -> Unit = {}): Int {
        if (t.title.isNotEmpty()) Kit.label(graphics, t.title, x, top, Kit.Ink.MUTED)
        if (t.note.isNotEmpty()) Kit.label(graphics, t.note, x + t.width - t.noteWidth, top, Kit.Ink.MUTED)
        val y = top + SECTION
        val bottom = top + t.height(limit) - Kit.GAP
        Kit.well(graphics, x, y, t.width, bottom - y)
        val head = t.header
        val header = if (head != null) 1 else 0
        val shown = t.rows.size.coerceAtMost(limit)
        for (line in 0 until header + shown) {
            val lineY = y + 3 + line * ROW
            if (ghostly && line >= header) {
                Kit.bone(graphics, x + 4, lineY, 40 + line * 17 % 36, 7)
                Kit.bone(graphics, x + t.width - 34, lineY, 30, 7)
                continue
            }
            val cells = if (head != null && line < header) head else t.rows[line - header]
            graphics.text(font, cells[0], x + 4, lineY, -1, true)
            var right = x + t.width - 4 - t.columns.sum() - t.gap * (t.columns.size - 1)
            for (c in t.columns.indices) {
                right += t.columns[c]
                cells.getOrNull(c + 1)?.let { graphics.text(font, it, right - font.width(it), lineY, -1, true) }
                right += t.gap
            }
        }
        if (inside(mouseX, mouseY, x, y, t.width, bottom - y)) {
            if (tip != null) hover(tip) else t.tips.getOrNull((mouseY - y - 3) / ROW - header)?.let(hover)
        }
        return bottom + Kit.GAP
    }

    private fun side(graphics: GuiGraphicsExtractor, tables: List<Table>, x: Int, top: Int, mouseX: Int = -1, mouseY: Int = -1, hover: (List<Component>) -> Unit = {}): Int {
        var at = x
        var bottom = top
        for (t in tables) {
            bottom = maxOf(bottom, draw(graphics, t, at, top, mouseX, mouseY, hover = hover))
            at += t.width + Kit.GAP
        }
        return bottom
    }

    private fun classes(graphics: GuiGraphicsExtractor, texts: Texts, x: Int, top: Int, mouseX: Int, mouseY: Int, ghostly: Boolean, hover: (List<Component>) -> Unit): Int {
        val classes = texts.classes
        if (classes.isEmpty()) return top
        Kit.label(graphics, "Dungeons", x, top, Kit.Ink.MUTED)
        if (texts.completions.isNotEmpty()) Kit.label(graphics, texts.completions, x + LIST_WIDTH - font.width(texts.completions), top, Kit.Ink.MUTED)
        val y = top + SECTION
        Kit.well(graphics, x, y, LIST_WIDTH, CLASS_HEIGHT)
        val cell = (LIST_WIDTH - 4) / classes.size
        for ((i, c) in classes.withIndex()) {
            val cx = x + 2 + i * cell
            val ix = cx + (cell - 16) / 2
            if (ghostly) {
                Kit.bone(graphics, ix, y + 2, 16, 16)
                continue
            }
            val picked = texts.dungeons?.picked(c) == true
            if (picked) Kit.ring(graphics, cx, y + 1, cell, CLASS_HEIGHT - 2, Role.GOOD.argb())
            val stack = icon(c.id)
            graphics.item(stack, ix, y + 2)
            graphics.itemDecorations(font, stack, ix, y + 2, texts.classLevels[i])
            if (inside(mouseX, mouseY, cx, y, cell, CLASS_HEIGHT)) hover(progressTooltip(c, picked))
        }
        return y + CLASS_HEIGHT + Kit.GAP
    }

    private fun overview(graphics: GuiGraphicsExtractor, profile: Profile, top: Int, mouseX: Int, mouseY: Int, ghostly: Boolean, hover: (List<Component>) -> Unit): Int {
        val texts = texts(profile)
        var left = progress(graphics, texts, "Skills", profile.skills, contentX, top, mouseX, mouseY, ghostly, hover)
        left = progress(graphics, texts, "Slayers", profile.slayers, contentX, left, mouseX, mouseY, ghostly, hover)
        val x = rightX
        var right = if (profile.stats.isEmpty()) top else draw(graphics, texts.stats, x, top, ghostly = ghostly)
        right = classes(graphics, texts, x, right, mouseX, mouseY, ghostly, hover)
        if (texts.kills.rows.isNotEmpty()) {
            val room = (top + viewHeight - right - SECTION - 5) / ROW
            val limit = room.coerceIn(1, texts.kills.rows.size)
            right = draw(graphics, texts.kills, x, right, mouseX, mouseY, ghostly, limit, texts.allKills.takeIf { limit < texts.kills.rows.size }, hover)
        }
        return extent(maxOf(left, right), top)
    }

    private fun playerStats(graphics: GuiGraphicsExtractor, profile: Profile, top: Int, mouseX: Int, mouseY: Int, hover: (List<Component>) -> Unit) =
        extent(side(graphics, texts(profile).playerStats, contentX, top, mouseX, mouseY, hover), top)

    private fun dungeons(graphics: GuiGraphicsExtractor, profile: Profile, top: Int, mouseX: Int, mouseY: Int, hover: (List<Component>) -> Unit): Int {
        val texts = texts(profile)
        var left = progress(graphics, texts, "Catacombs", texts.catacombs, contentX, top, mouseX, mouseY, false, hover)
        if (texts.dungeons?.essence?.isNotEmpty() == true) left = side(graphics, texts.essence, contentX, left)
        val x = rightX
        val right = draw(graphics, texts.floors, x, draw(graphics, texts.runs, x, top))
        return extent(maxOf(left, right), top)
    }

    private fun mining(graphics: GuiGraphicsExtractor, profile: Profile, top: Int, now: Long): Int {
        val texts = texts(profile)
        var left = texts.heart?.let { draw(graphics, it, contentX, top) } ?: top
        texts.forge(now)?.let { left = draw(graphics, it, contentX, left) }
        val right = texts.nodes?.let { draw(graphics, it, rightX, top) } ?: top
        return extent(maxOf(left, right), top)
    }

    private fun collections(graphics: GuiGraphicsExtractor, profile: Profile, top: Int): Int {
        val texts = texts(profile)
        return extent(side(graphics, listOf(texts.collections, texts.meters), contentX, top), top)
    }

    private fun progressTooltip(p: Progress, selected: Boolean): List<Component> {
        val lines = ArrayList<Component>()
        lines += text { body("${p.name} "); value(p.level.toLong()) }.toComponent()
        if (selected) lines += text { good("Selected class") }.toComponent()
        val next = p.xpForNextLevel
        when {
            p.maxed -> lines += text { good("Maxed") }.toComponent()
            next != null -> lines += text { muted("Progress: "); value("${grouped(p.xpIntoLevel.toLong())} / ${grouped(next.toLong())}") }.toComponent()
        }
        lines += text { muted("Total XP: "); value(grouped(p.totalXp.toLong())) }.toComponent()
        return lines
    }

    private fun container(graphics: GuiGraphicsExtractor, profile: Profile?, mouseX: Int, mouseY: Int, hover: (List<Component>) -> Unit, item: (ItemStack) -> Unit): Int {
        val summary = shownSummary() ?: return 0
        val texts = texts(profile)
        val pets = summary.id == PETS
        val items: List<Shown?>? = when {
            pets -> texts.pets
            summary.hidden -> null
            else -> (shownContainer() as? Load.Loaded)?.value?.let { loaded ->
                if (stacked !== loaded) {
                    stacked = loaded
                    stacks = loaded.items.map { it?.let(::shown) }
                }
                stacks
            }
        }
        val columns = summary.columns.coerceIn(1, 9)
        val slots = if (summary.hidden) HIDDEN_SLOTS[summary.kind] ?: columns * 3 else summary.slots
        val rows = (slots + columns - 1) / columns
        val inventory = summary.id == "inventory"
        val hotbar = inventory && rows > 1
        val height = rows * Kit.SLOT_SIZE + if (hotbar) Kit.GAP else 0
        val worn = profile?.equippedWardrobeSlot?.takeIf { summary.id == "wardrobe:${it / 9}" }?.rem(9)

        graphics.enableScissor(contentX, viewY, contentX + GRID_WIDTH, viewY + viewHeight)
        val top = viewY - scroll
        for (i in 0 until slots) {
            val row = i / columns
            val column = i % columns
            val x = contentX + column * Kit.SLOT_SIZE
            val y = top + row * Kit.SLOT_SIZE + if (hotbar && row == rows - 1) Kit.GAP else 0
            val shown = items?.getOrNull(i) ?: if (column == worn && items != null) equipment.getOrNull(row) else null
            slot(graphics, shown, i, x, y, mouseX, mouseY, item)
        }
        if (worn != null && items != null) Kit.ring(graphics, contentX + worn * Kit.SLOT_SIZE, top, Kit.SLOT_SIZE, 4 * Kit.SLOT_SIZE, Role.GOOD.argb())
        if (pets) {
            val active = profile?.pets?.indexOfFirst { it.active } ?: -1
            if (active >= 0) Kit.ring(graphics, contentX + active % columns * Kit.SLOT_SIZE, top + active / columns * Kit.SLOT_SIZE, Kit.SLOT_SIZE, Kit.SLOT_SIZE, Role.GOOD.argb())
        }
        graphics.disableScissor()

        val sx = contentX + SIDE_X
        val sideTop = viewY - scroll
        graphics.enableScissor(sx, viewY, contentX + CONTENT_WIDTH, viewY + viewHeight)
        var y = sideTop
        val paged = pagedTab()
        if (paged != null) {
            val buttons = children().filterIsInstance<KitButton>().filter { it.message.string.all(Char::isDigit) }
            for (i in buttons.indices) buttons[i].y = sideTop + pageY(i)
        }
        if (paged != null) {
            Kit.label(graphics, "Pages", sx, y, Kit.Ink.MUTED)
            val rowsOfPages = (paged.pages.size + PAGES_PER_ROW - 1) / PAGES_PER_ROW
            val shown = page(paged)
            val px = sx + pageX(shown)
            val py = y + pageY(shown)
            y += SECTION + rowsOfPages * (Kit.BUTTON_HEIGHT + Kit.GAP) + Kit.GAP
            Kit.ring(graphics, px - 1, py - 1, PAGE_BUTTON + 2, Kit.BUTTON_HEIGHT + 2, Kit.theme.accent)
        }
        if (inventory) {
            Kit.label(graphics, "Equipment", sx, y, Kit.Ink.MUTED)
            y += SECTION
            for ((i, w) in equipment.withIndex()) slot(graphics, w.takeUnless { summary.hidden }, i, sx + i * Kit.SLOT_SIZE, y, mouseX, mouseY, item)
            y += Kit.SLOT_SIZE + Kit.GAP * 2
        }
        if (pets) y = draw(graphics, texts.activePet, sx, y, mouseX, mouseY, hover = hover)
        if (items != null) y = draw(graphics, contents(summary, items, worn), sx, y)
        graphics.disableScissor()
        return maxOf(height, y - sideTop)
    }

    private var counted: List<Shown?>? = null
    private var countedWorn: Int? = null
    private var counts: Table? = null

    private fun contents(summary: ContainerSummary, items: List<Shown?>, worn: Int?): Table {
        val cached = counts
        if (cached != null && counted === items && countedWorn == worn) return cached
        val used = items.count { it != null }
        val rows = listOf(Row(muted("Slots used"), listOf(text { value("$used / ${summary.slots}") }.toComponent()))) +
            listOfNotNull(worn?.let { Row(muted("Worn"), listOf(text { good("Slot ${it + 1}") }.toComponent())) }) +
            Role.RARITIES.mapNotNull { rarity ->
                val n = items.count { it?.mark == rarity }
                if (n == 0) null else Row({ w -> text { rarity(fit(title(rarity.name), w), rarity) }.toComponent() }, listOf(text { value(n.toLong()) }.toComponent()))
            }
        return table("Contents", SIDE_WIDTH, rows).also {
            counted = items
            countedWorn = worn
            counts = it
        }
    }

    private fun slot(graphics: GuiGraphicsExtractor, shown: Shown?, index: Int, x: Int, y: Int, mouseX: Int, mouseY: Int, hover: (ItemStack) -> Unit) {
        Kit.slot(graphics, x, y)
        shown ?: return
        val mark = shown.mark?.takeIf { Slots.marks(it) }
        if (mark != null) Slots.rarityBack(graphics, mark, index, x + 1, y + 1)
        graphics.item(shown.stack, x + 1, y + 1)
        graphics.itemDecorations(font, shown.stack, x + 1, y + 1)
        val over = inside(mouseX, mouseY, x, y, Kit.SLOT_SIZE, Kit.SLOT_SIZE)
        if (over) Kit.slotHover(graphics, x + 1, y + 1)
        if (over) hover(shown.stack)
    }

    internal fun maxScroll() = (contentHeight - viewHeight).coerceAtLeast(0)

    private fun thumbHeight() = ((viewHeight - 2) * viewHeight / contentHeight.coerceAtLeast(viewHeight)).coerceAtLeast(8)

    private val rightX get() = contentX + PROGRESS_WIDTH + Kit.GAP

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

    private fun dragThumb(mouseY: Double) {
        val thumb = thumbHeight()
        val at = ((mouseY - viewY - 1 - thumb / 2.0) / (viewHeight - 2 - thumb)).coerceIn(0.0, 1.0)
        scroll = (at * maxScroll()).roundToInt()
    }

    private fun choiceName(choice: ProfileChoice) = choice.name.plain

    private companion object {
        const val OVERVIEW = "overview"
        const val STATS = "player_stats"
        const val DUNGEONS = "dungeons"
        const val MINING = "mining"
        const val BESTIARY = "bestiary"
        const val COLLECTIONS = "collections"
        const val PETS = "pets"

        val SHEETS = setOf(OVERVIEW, STATS, DUNGEONS, MINING, BESTIARY, COLLECTIONS)

        const val LOADING = "Loading"
        const val DOT_MILLIS = 350L
        const val NOTICE_TEXT = 220

        const val SPLIT_AT = 4

        const val CLASS_HEIGHT = 20

        const val MODEL_HEIGHTS = 2.8
        val LOG = LoggerFactory.getLogger("dsmod")

        fun title(mode: String) = mode.lowercase(Locale.US).split('_').joinToString(" ") { word -> word.replaceFirstChar { it.titlecase(Locale.US) } }

        fun remaining(millis: Long): String {
            val s = millis / 1000
            return when {
                s >= 3600 -> "${s / 3600}h ${s % 3600 / 60}m"
                s >= 60 -> "${s / 60}m ${s % 60}s"
                else -> "${s}s"
            }
        }

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
