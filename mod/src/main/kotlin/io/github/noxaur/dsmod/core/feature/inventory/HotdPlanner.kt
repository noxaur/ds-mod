package io.github.noxaur.dsmod.core.feature.inventory

import io.github.noxaur.dsmod.core.config.DsModConfig
import io.github.noxaur.dsmod.core.config.Store
import io.github.noxaur.dsmod.core.container.SlotMark
import io.github.noxaur.dsmod.core.feature.Feature
import io.github.noxaur.dsmod.core.feature.HasFeature
import io.github.noxaur.dsmod.core.game.Area
import io.github.noxaur.dsmod.core.game.ContainerState
import io.github.noxaur.dsmod.core.game.SlotChanged
import io.github.noxaur.dsmod.core.game.TabList
import io.github.noxaur.dsmod.core.heart.Base
import io.github.noxaur.dsmod.core.heart.Goal
import io.github.noxaur.dsmod.core.heart.HeartBlock
import io.github.noxaur.dsmod.core.heart.MiningArea
import io.github.noxaur.dsmod.core.heart.Perks
import io.github.noxaur.dsmod.core.heart.Plan
import io.github.noxaur.dsmod.core.heart.PlanInput
import io.github.noxaur.dsmod.core.heart.Planner
import io.github.noxaur.dsmod.core.heart.Powder
import io.github.noxaur.dsmod.core.heart.Step
import io.github.noxaur.dsmod.core.heart.Tree
import io.github.noxaur.dsmod.core.player.Heart
import io.github.noxaur.dsmod.core.text.Role
import io.github.noxaur.dsmod.core.text.Text
import io.github.noxaur.dsmod.core.text.count
import io.github.noxaur.dsmod.core.text.text
import io.github.noxaur.dsmod.core.game.ServerSpan
import io.github.noxaur.dsmod.core.game.ServerText
import kotlinx.serialization.Serializable

@Serializable
data class MiningStats(val at: Long = 0, val base: Base? = null)

class HotdPlanner(config: DsModConfig) : HasFeature {
    override val feature = Feature(config, "hotd-planner", Area.ANYWHERE, Store.INVENTORY, "Mark what to click next in the Heart of the Dragon.", slots = ::slots)

    val block = feature.choice("hotdBlock", "Block", "Pick the block you mine.", HeartBlock.END_STONE)
    val nodes = feature.switch("hotdNodes", "Ender Nodes", "Plan for mining the Ender Nodes in your block.", default = false)
    val area = feature.choice("hotdArea", "Area", "Pick where you mine.", MiningArea.ANYWHERE)
    val items = feature.switch("hotdYield", "Yield", "Plan for more items from each block.", default = true)
    val powder = feature.switch("hotdPowder", "Powder", "Plan for more powder.", default = true)
    val xp = feature.switch("hotdXp", "XP", "Plan for more Mining XP.", default = false)
    val pets = feature.switch("hotdPets", "Pets", "Plan for more mining pets, and Fine Amethyst on Amethyst.", default = false)

    val pristine = feature.switch("hotdPristine", "Pristine", "Plan for more Flawed Amethyst.", default = false).also { it.shownWhile = ::amethyst }
    val fragments = feature.switch("hotdFragments", "Void Fragments", "Plan for more Void Fragments.", default = false).also { it.shownWhile = ::amethyst }

    fun amethyst() = block.value == HeartBlock.AMETHYST

    /** @keep Ender Nodes grow only in End Stone and Obsidian */
    fun hasNodes() = block.value.nodes

    private val stats = feature.profiled(MiningStats())

    data class Purse(val ender: Long, val void: Long, val enderShort: Boolean, val voidShort: Boolean, val enderName: Text, val voidName: Text)

    private fun name(label: String, color: Int?) = text { if (color == null) body(label) else server(label, ServerText(ServerSpan(label, color))) }

    var purse: Purse? = null
        private set

    private var plan: Plan? = null
    private var asked: PlanInput? = null
    private var read: Heart? = null
    private var tree: Tree? = null

    // @keep The tree is read again on each slot the menu sends, so a line it cannot read is told once
    private val told by feature.session { HashSet<String>() }

    private var waiting by feature.session<Pair<Long, Map<String, Int>>?> { null }

    init {
        feature.on<SlotChanged> { _, _ -> readStats() }
        feature.on<TabList> { _, _ -> readStats() }
    }

    fun shown(title: String?) = title == Tree.HEART

    private fun goals(): Set<Goal> {
        val amethyst = amethyst()
        return buildSet {
            if (items.value) add(Goal.ITEMS)
            if (powder.value) add(Goal.POWDER)
            if (xp.value) add(Goal.XP)
            if (pets.value) {
                add(Goal.RARE)
                if (amethyst) add(Goal.FINE)
            }
            if (amethyst && pristine.value) add(Goal.FLAWED)
            if (amethyst && fragments.value) add(Goal.FRAGMENTS)
        }
    }

    private fun complete(tree: Tree) = !tree.stale && Perks.all.none { it.tier <= tree.tier && it.name !in tree.levels }

    /** @keep The Dragon Sim Menu counts the perks that hold on every block in its numbers, so a reading waits for a
     * complete tree to add them back. */
    private fun readStats() {
        val readings = STATS.mapNotNull { feature.player.stats[it] }
        val at = readings.maxOfOrNull { it.at } ?: return
        if (at <= stats.value.at) return
        val read = Tree.read(feature.player.heart)
        val levels = read?.levels.orEmpty()
        val waiting = waiting
        if (waiting == null || waiting.first != at) this.waiting = at to levels
        else if (waiting.second.any { (perk, level) -> levels[perk] != level }) {
            stats.value = stats.value.copy(at = at)
            return
        }
        val tree = read?.takeIf { complete(it) } ?: return
        val shown = STATS.associateWith { feature.player.stats[it]?.value }
        val perks = Planner.everywhere(tree.levels)
        val none = Base()
        fun base(stat: String, perk: Double, default: Double) = shown[stat]?.let { it - perk } ?: default
        stats.value = MiningStats(at, Base(
            speed = base(SPEED, perks.speed, none.speed),
            fortune = base(FORTUNE, perks.fortune, none.fortune),
            spread = base(SPREAD, perks.spread, none.spread),
            pristine = base(PRISTINE, perks.pristine, none.pristine),
            wisdom = base(WISDOM, perks.wisdom, none.wisdom),
            magicFind = base(MAGIC_FIND, perks.magicFind, none.magicFind),
        ))
    }

    private fun input(tree: Tree) = PlanInput(
        block.value, nodes.value, area.value, goals(), tree.tier, tree.core, stats.value.base ?: Base(),
        tree.levels.filterValues { it > 0 },
    )

    data class Next(val slot: Int, val clicks: Int, val short: Boolean)

    private fun slots(now: Long): Map<Int, SlotMark> {
        val container = feature.container
        if (!shown(container.title?.plain)) {
            purse = null
            return emptyMap()
        }
        val marks = HashMap<Int, SlotMark>()
        val tiers = HashMap<Int, Int>()
        for (row in 0 until ROWS) {
            val pane = container.slots.getOrNull(row * ContainerState.ROW_SLOTS) ?: continue
            val tier = TIER.matchEntire(pane.name?.plain.orEmpty())?.groupValues?.get(1)?.let(::count) ?: continue
            tiers[tier] = row
            if (pane.id == LIME) marks[row * ContainerState.ROW_SLOTS] = DIM
        }
        val heart = feature.player.heart
        if (heart !== read) {
            read = heart
            tree = Tree.read(heart) { if (told.add(it)) feature.unparsed(it) }
        }
        val tree = tree
        purse = tree?.let { Purse(it.ender, it.void, false, false, name(ENDER, it.enderColor), name(VOID, it.voidColor)) }
        if (tree == null) return marks
        // @keep The server fills a menu a slot at a time, so a slot is marked once it sent its item.
        next(tree, tiers)?.takeIf { container.slots.getOrNull(it.slot) != null }?.let { marks[it.slot] = SlotMark(if (it.short) Role.BAD else Role.GOOD, count = it.clicks) }
        return marks
    }

    private fun next(tree: Tree, tiers: Map<Int, Int>): Next? {
        val top = tiers.keys.maxOrNull() ?: return null
        fun scroll(tier: Int, short: Boolean): Next? = when {
            tier > top -> Next(SCROLL_UP, tier - top, short)
            tier < top - ROWS + 1 -> Next(SCROLL_DOWN, top - ROWS + 1 - tier, short)
            else -> null
        }
        fun at(step: Step, short: Boolean): Next? {
            val perk = Perks.byName.getValue(step.perk)
            val row = tiers[perk.tier] ?: return scroll(perk.tier, short)
            return Next(row * ContainerState.ROW_SLOTS + perk.column, step.to - step.from, short)
        }

        val unseen = Perks.all.filter { it.tier <= tree.tier && it.name !in tree.levels }.map { it.tier }
        if (unseen.isNotEmpty()) return scroll(unseen.minBy { if (it > top) it - top else top - ROWS + 1 - it }, false)
        if (tree.stale) return scroll(if (top - ROWS + 1 > 1) 1 else tree.tier, false)

        val input = input(tree)
        if (input != asked) {
            asked = input
            feature.background({ Planner.plan(input) }) { done -> if (done.input == asked) plan = done }
        }
        val plan = plan?.takeIf { p -> p.input.copy(levels = input.levels) == input && p.input.levels.all { (perk, level) -> (tree.levels[perk] ?: 0) >= level } }
            ?: return null
        val rest = plan.after(tree.levels)
        fun short(powder: Powder) = rest.firstOrNull { it.powder == powder }?.let { Perks.byName.getValue(it.perk).cost[it.from] > tree.have(powder) } == true
        purse = purse?.copy(enderShort = short(Powder.ENDER), voidShort = short(Powder.VOID))
        val batch = Planner.batch(rest, tree.ender, tree.void)
        if (batch.now.isEmpty()) return batch.later.firstOrNull()?.let { at(it, true) }
        return at(batch.now.firstOrNull { Perks.byName.getValue(it.perk).tier in tiers } ?: batch.now.first(), false)
    }

    companion object {
        private const val ROWS = 5
        private const val SCROLL_UP = 8
        private const val SCROLL_DOWN = 44
        private const val LIME = "minecraft:lime_stained_glass_pane"
        private val TIER = Regex("Tier (\\d+)")
        private val DIM = SlotMark(Role.MUTED, dim = true)

        private const val ENDER = "Ender"
        private const val VOID = "Void"
        private const val SPEED = "Mining Speed"
        private const val FORTUNE = "Mining Fortune"
        private const val SPREAD = "Mining Spread"
        private const val PRISTINE = "Pristine"
        private const val WISDOM = "Mining Wisdom"
        private const val MAGIC_FIND = "Magic Find"
        private val STATS = listOf(SPEED, FORTUNE, SPREAD, PRISTINE, WISDOM, MAGIC_FIND)
    }
}
