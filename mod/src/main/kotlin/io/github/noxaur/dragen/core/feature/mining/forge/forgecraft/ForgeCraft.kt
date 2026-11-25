package io.github.noxaur.dragen.core.feature.mining.forge.forgecraft

import io.github.noxaur.dragen.core.config.DragenConfig
import io.github.noxaur.dragen.core.config.Store
import io.github.noxaur.dragen.core.container.SlotMark
import io.github.noxaur.dragen.core.feature.Feature
import io.github.noxaur.dragen.core.feature.HasFeature
import io.github.noxaur.dragen.core.game.Area
import io.github.noxaur.dragen.core.game.ContainerOpened
import io.github.noxaur.dragen.core.game.Item
import io.github.noxaur.dragen.core.game.SlotChanged
import io.github.noxaur.dragen.core.text.Role
import io.github.noxaur.dragen.core.text.count

class ForgeCraft(config: DragenConfig) : HasFeature {
    override val feature = Feature(config, "forge-craft", Area.ANYWHERE, Store.INVENTORY, "Repeat your last forge craft, one click per key press.", slots = ::slots)

    val key = feature.keys("forgeCraftKey", "Craft Key", "Pick the key that clicks the next button of your last forge craft.", KEY_R, clicks = false)

    data class Material(val name: String, val count: Int, val icon: Item? = null)

    data class Recipe(val category: String, val item: Item, val materials: List<Material>) {
        val name: String get() = item.name?.plain.orEmpty()
    }

    data class Step(val menu: Int, val slot: Int, val blocked: Boolean)

    var recipe by feature.session<Recipe?> { null }
        private set

    private var menu by feature.world<String?> { null }
    private var category by feature.world<String?> { null }
    private var queued by feature.world { 0 }
    private var queueSize by feature.world { QUEUE_SIZE }
    private var forging by feature.world { FORGE_SLOTS.count() }

    private val offered by feature.world { HashMap<String, Item>() }
    private val seen by feature.world { HashMap<String, Item>() }

    private val categories by feature.world { HashSet<String>() }

    init {
        feature.on<ContainerOpened> { opened, _ ->
            val title = opened.title?.plain
            if (menu == SELECT && title != null && title != FORGE && title != CONFIRM) {
                category = title
                offered.clear()
            }
            menu = title
        }
        feature.on<SlotChanged> { changed, _ ->
            if (!forge(menu)) return@on
            if (menu == FORGE && changed.slot in FORGE_SLOTS) {
                val slots = feature.container.slots
                forging = FORGE_SLOTS.count { slots[it]?.lore?.any { line -> line.plain.startsWith(REMAINING) } == true }
            }
            val item = changed.item ?: return@on
            val name = item.name?.plain?.ifEmpty { null } ?: return@on
            seen.putIfAbsent(name, item)
            if (changed.slot !in feature.container.menuSlots) return@on
            val menu = menu
            when {
                menu == FORGE -> QUEUE.matchEntire(name)?.destructured?.let { (busy, size) ->
                    queued = count(busy) ?: return@on
                    queueSize = count(size) ?: return@on
                }
                menu == SELECT -> if (item.lore.any { it.plain == BROWSE }) categories += name
                menu == category -> if (item.lore.any { it.plain == REQUIRED }) offered[name] = item
            }
        }
        feature.line("Queued ", Regex("Queued (.+)! It starts automatically when a Forge slot finishes\\.")) { (name), _ -> queued(name) }
        feature.line("Forging ", Regex("Forging (.+)! Ready in .+\\.")) { (name), _ -> queued(name) }
    }

    fun forge(title: String?): Boolean = title == FORGE || title == SELECT || title == CONFIRM || title in categories || title == recipe?.category

    private fun queued(name: String) {
        if (recipe?.name == name) return
        val item = offered[name] ?: return
        val from = category ?: return
        val materials = materials(item).map { (material, count) -> Material(material, count, seen[material]) }
        recipe = Recipe(from, item, materials)
    }

    private fun find(name: (String) -> Boolean): Int? {
        val slots = feature.container.slots
        for (i in feature.container.menuSlots) if (slots[i]?.name?.plain?.let(name) == true) return i
        return null
    }

    fun next(): Step? {
        val recipe = recipe ?: return null
        val slots = feature.container.slots
        val id = feature.container.id
        return when (menu) {
            FORGE -> find { QUEUE.matches(it) }?.let { Step(id, it, full) }
            SELECT -> find { it == recipe.category }?.let { Step(id, it, false) }
            recipe.category -> find { it == recipe.name }?.let { Step(id, it, false) }
            CONFIRM -> if (slots.getOrNull(CONFIRM_ITEM)?.name?.plain != recipe.name) null
            else find { it == CONFIRM_BUTTON }?.let { Step(id, it, slots[it]?.id == GRAY) }
            else -> null
        }
    }

    fun has(name: String): Int {
        val slots = feature.container.slots
        var total = 0
        for (i in feature.container.playerSlots) slots[i]?.takeIf { it.name?.plain == name }?.let { total += it.count }
        return total
    }

    fun icon(material: Material): Item? = material.icon ?: seen[material.name] ?: feature.container.slots.firstOrNull { it?.name?.plain == material.name }

    fun covered(): Int = recipe?.materials?.minOfOrNull { has(it.name) / it.count.coerceAtLeast(1) } ?: 0

    val free: Int get() = (FORGE_SLOTS.count() - forging + queueSize - queued).coerceAtLeast(0)

    val full: Boolean get() = free == 0

    fun canCraft(): Int = minOf(covered(), free)

    private fun slots(now: Long): Map<Int, SlotMark> {
        val step = next() ?: return emptyMap()
        return mapOf(step.slot to SlotMark(if (step.blocked) Role.BAD else Role.ACTION, outline = true))
    }

    companion object {
        private const val KEY_R = 82
        private const val QUEUE_SIZE = 10
        private const val CONFIRM_ITEM = 4
        private val FORGE_SLOTS = 10..16

        const val FORGE = "The Forge"
        const val SELECT = "Select Process"
        const val CONFIRM = "Confirm Process"
        private const val CONFIRM_BUTTON = "Confirm"
        private const val BROWSE = "Click to browse!"
        private const val REQUIRED = "Items Required"
        private const val REMAINING = "Time Remaining: "
        private const val GRAY = "minecraft:gray_terracotta"
        private val QUEUE = Regex("Forge Queue \\((\\d+)/(\\d+)\\)")
        private val MATERIAL = Regex("(.+?)(?: x(\\d+))?")

        fun materials(item: Item): List<Pair<String, Int>> = item.section(REQUIRED).mapNotNull { line ->
            MATERIAL.matchEntire(line)?.destructured?.let { (name, amount) -> name to (count(amount) ?: 1) }
        }
    }
}
