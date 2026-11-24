package io.github.noxaur.dsmod.core.heart

import kotlinx.serialization.Serializable
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToLong

/** @keep From dragsim.co/wiki/reference/mining, where Amethyst Block's numbers stand for every Amethyst size. */
enum class HeartBlock(
    private val label: String,
    val drop: String,
    val strength: Int,
    val xp: Int,
    val count: Int,
    val powder: Powder,
    val low: Int,
    val high: Int,
    val nodes: Boolean = false,
) {
    END_STONE("End Stone", "End Stone", 30, 5, 4, Powder.ENDER, 8, 16, nodes = true),
    OBSIDIAN("Obsidian", "Obsidian", 500, 20, 2, Powder.ENDER, 24, 40, nodes = true),
    CRYING_OBSIDIAN("Crying Obsidian", "Crying Obsidian", 800, 60, 1, Powder.VOID, 80, 140),
    AMETHYST("Amethyst", "Rough Amethyst", 600, 90, 2, Powder.VOID, 160, 240),
    ;

    override fun toString() = label
}

enum class MiningArea(private val label: String) {
    CRYSTAL_BAND("Crystal Band"),
    BRUISER_HIDEOUT("Bruiser Hideout"),
    ANYWHERE("Anywhere"),
    ;

    override fun toString() = label
}

enum class Goal { ITEMS, POWDER, XP, FLAWED, FINE, FRAGMENTS, RARE }

@Serializable
data class Base(
    val speed: Double = 1500.0,
    val fortune: Double = 100.0,
    val spread: Double = 0.0,
    val amethystSpread: Double = 0.0,
    val pristine: Double = 1.0,
    val wisdom: Double = 0.0,
    val magicFind: Double = 0.0,
    val cooldownCut: Double = 0.0,
)

/** @keep `hoursPerDay`, `instantTicks` and `pickobulusBlocks` are guesses until the mod measures them. */
data class PlanInput(
    val block: HeartBlock,
    val nodes: Boolean,
    val area: MiningArea,
    val goals: Set<Goal>,
    val tier: Int,
    val core: Int,
    val base: Base,
    val levels: Map<String, Int>,
    val hoursPerDay: Double = 2.0,
    val instantTicks: Int = 1,
    val pickobulusBlocks: Int = 30,
)

data class Step(val perk: String, val powder: Powder?, val from: Int, val to: Int, val cost: Long)

data class Plan(val input: PlanInput, val steps: List<Step>, val ability: String?) {
    fun after(levels: Map<String, Int>): List<Step> = steps.mapNotNull { step ->
        val level = levels[step.perk] ?: 0
        when {
            level >= step.to -> null
            level > step.from -> step.copy(from = level, cost = Perks.byName.getValue(step.perk).cost(level, step.to))
            else -> step
        }
    }
}

data class Batch(val now: List<Step>, val later: List<Step>)

/** @keep Formulas are from dragsim.co/wiki/article/mining-formulas and /wiki/article/loot-formulas, and the pickaxe
 * abilities' numbers at level 3 from a capture of /hotd. */
object Planner {
    private class Ability(
        val duration: Double,
        val cooldown: Double,
        val speedPct: Double = 0.0,
        val stack: Double = 0.0,
        val cap: Double = 0.0,
        val burst: Boolean = false,
        val spread: Double = 0.0,
        val voidDouble: Boolean = false,
    )

    private val ABILITIES = mapOf(
        "Mining Speed Boost" to Ability(20.0, 120.0, speedPct = 300.0),
        "Tunnel Vision" to Ability(30.0, 110.0, speedPct = 50.0),
        "Maniac Miner" to Ability(35.0, 120.0, stack = 15.0, cap = 500.0),
        "Pickobulus" to Ability(0.0, 40.0, burst = true),
        "Sheer Force" to Ability(30.0, 120.0, spread = 200.0),
        "Void Infusion" to Ability(30.0, 300.0, voidDouble = true),
    )
    private val NO_ABILITY = Ability(0.0, 1.0)

    private const val FRONT_LOADED_BLOCKS = 2500.0
    private const val DAILY_POWDER = 500.0
    private const val FRAGMENT_CHANCE = 1e-5
    private const val EPSILON = 1e-12

    private class Stats(
        var speed: Double, var speedPct: Double = 0.0, var fortune: Double, var spread: Double, var amethystSpread: Double,
        var pristine: Double, var wisdom: Double, var magicFind: Double, var powderPct: Double = 0.0, var doublePowder: Double = 0.0,
        var durationPct: Double = 0.0, var cooldownPct: Double, var frontLoaded: Boolean = false, var dailyEnder: Boolean = false,
        var dailyVoid: Boolean = false, var fineCut: Double = 0.0, var fragmentPct: Double = 0.0, var giftPct: Double = 0.0,
    ) {
        fun copy() = Stats(speed, speedPct, fortune, spread, amethystSpread, pristine, wisdom, magicFind, powderPct, doublePowder,
            durationPct, cooldownPct, frontLoaded, dailyEnder, dailyVoid, fineCut, fragmentPct, giftPct)
    }

    private class Context(val input: PlanInput, val block: HeartBlock, val node: Boolean, val voidWorth: Double)

    private class Rates {
        var items = 0.0
        var ender = 0.0
        var void = 0.0
        var xp = 0.0
        var flawed = 0.0
        var fine = 0.0
        var fragments = 0.0
        var rare = 0.0
        var powder = 0.0

        operator fun get(goal: Goal) = when (goal) {
            Goal.ITEMS -> items
            Goal.POWDER -> powder
            Goal.XP -> xp
            Goal.FLAWED -> flawed
            Goal.FINE -> fine
            Goal.FRAGMENTS -> fragments
            Goal.RARE -> rare
        }
    }

    fun tokens(tier: Int, core: Int): Int {
        var tokens = 0
        for (t in 1..tier) tokens += if (t <= 5) 1 else 2
        if (core >= 1) tokens++
        if (core >= 4) tokens++
        if (core >= 7) tokens++
        if (core >= 10) tokens += 2
        return tokens
    }

    /** @keep No Stone Unturned, Obsidian Insanium, Frugal Miner and Quick Forge are not mining rates. Dragon's Favor
     * grants one of five buffs a SkyBlock day, so a fifth of each counts */
    private fun add(a: Stats, perk: Perk, level: Int, c: Context) {
        if (level <= 0) return
        val v = perk.value[level - 1]
        val block = c.block
        val area = c.input.area
        when (perk.name) {
            "Mining Speed", "Speedy Mineman" -> a.speed += v
            "Mining Fortune", "Fortunate Mineman" -> a.fortune += v
            "Efficient Miner" -> a.spread += v
            "Mole" -> if (block == HeartBlock.END_STONE) a.spread += v
            "Steady Hand" -> a.amethystSpread += v
            "Blockhead" -> if (block == HeartBlock.END_STONE) a.fortune += v
            "Old-School" -> if (block == HeartBlock.OBSIDIAN) a.fortune += v
            "Metal Head" -> if (block == HeartBlock.CRYING_OBSIDIAN) a.fortune += v
            "Gem Lover" -> if (block == HeartBlock.AMETHYST) a.fortune += v
            "Rags to Riches" -> if (area == MiningArea.BRUISER_HIDEOUT) a.fortune += v
            "Professional" -> if (block == HeartBlock.AMETHYST) a.speed += v
            "Strong Arm" -> if (block == HeartBlock.OBSIDIAN || block == HeartBlock.CRYING_OBSIDIAN) a.speed += v
            "Spelunker" -> if (area == MiningArea.CRYSTAL_BAND) a.speed += v
            "Eager Adventurer" -> if (area == MiningArea.BRUISER_HIDEOUT) a.speed += v
            "Precision Mining" -> if (c.node) a.speedPct += v
            "Mining Madness" -> {
                a.speed += 50
                a.fortune += 50
            }
            "Void Blessing" -> {
                a.speed += 50
                a.fortune += 30
                a.wisdom += 10
            }
            "Seasoned Mineman", "Seasoned Spelunker" -> a.wisdom += v
            "Crystalline", "Mining Master" -> a.pristine += v
            "Powder Buff" -> a.powderPct += v
            "Surveyor" -> if (area == MiningArea.BRUISER_HIDEOUT) a.doublePowder += v
            "Keep It Cool" -> a.durationPct += v
            "Impatient Miner" -> a.cooldownPct += v
            "Dragon's Favor" -> {
                a.speed += 20
                a.fortune += 10
                a.powderPct += 3
                a.cooldownPct += 4
            }
            "Front Loaded" -> a.frontLoaded = true
            "Daily Grind" -> a.dailyEnder = true
            "Daily Powder" -> a.dailyVoid = true
            "Miner's Blessing" -> a.magicFind += 30
            "Fine Cut" -> a.fineCut += v
            "Void Seeker" -> a.fragmentPct += v
            "Gifts from the Void" -> a.giftPct += v
        }
    }

    fun everywhere(levels: Map<String, Int>): Base {
        val c = Context(PlanInput(HeartBlock.END_STONE, false, MiningArea.ANYWHERE, emptySet(), 0, 0, Base(), levels), HeartBlock.END_STONE, false, 1.0)
        val a = Stats(speed = 0.0, fortune = 0.0, spread = 0.0, amethystSpread = 0.0, pristine = 0.0, wisdom = 0.0, magicFind = 0.0, cooldownPct = 0.0)
        for (perk in Perks.all) if (perk.name in EVERYWHERE) add(a, perk, levels[perk.name] ?: 0, c)
        return Base(a.speed, a.fortune, a.spread, 0.0, a.pristine, a.wisdom, a.magicFind)
    }

    private val EVERYWHERE = setOf(
        "Mining Speed", "Speedy Mineman", "Mining Fortune", "Fortunate Mineman", "Efficient Miner", "Mining Madness", "Void Blessing",
        "Seasoned Mineman", "Seasoned Spelunker", "Crystalline", "Mining Master", "Miner's Blessing",
    )

    private fun stats(perks: List<Perk>, levels: Map<String, Int>, c: Context, skip: Perk? = null): Stats {
        val b = c.input.base
        val a = Stats(b.speed, fortune = b.fortune, spread = b.spread, amethystSpread = b.amethystSpread, pristine = b.pristine,
            wisdom = b.wisdom, magicFind = b.magicFind, cooldownPct = b.cooldownCut)
        for (p in perks) if (p !== skip) add(a, p, levels[p.name] ?: 0, c)
        return a
    }

    private fun breakTicks(speed: Double, c: Context): Double {
        val strength = c.block.strength.toDouble()
        val threshold = strength * if (c.node) 60 else 30
        return when {
            speed <= 0 -> 200.0
            speed >= threshold -> c.input.instantTicks.toDouble()
            else -> max(4.0, (strength * 30 / speed).roundToLong().toDouble())
        }
    }

    /** @keep Mining pets and Void Fragments use the wiki's mining rare Find factor, where Magic Find already holds Miner's
     * Blessing's 30. Front Loaded covers the first 2,500 blocks a day, so its share of time depends on how fast they go */
    private fun rates(a: Stats, abilityName: String?, c: Context): Rates {
        val input = c.input
        val block = c.block
        val node = if (c.node) 2.0 else 1.0
        val ab = abilityName?.let(ABILITIES::get) ?: NO_ABILITY
        val cooldown = ab.cooldown * max(0.05, 1 - a.cooldownPct / 100)
        val duration = ab.duration * (1 + a.durationPct / 100)
        val uptime = if (ab.duration > 0) min(1.0, duration / cooldown) else 0.0
        val coreFlat = if (block.powder == Powder.ENDER) (if (input.core >= 3) 1 else 0) else if (input.core >= 5) 2 else 0
        val powderBase = (block.low + block.high) / 2.0 + coreFlat
        val amethyst = block == HeartBlock.AMETHYST
        val find = 2 + a.magicFind / 100
        val r = Rates()

        fun blocksPerSecond(active: Boolean, front: Boolean): Double {
            val speed = (a.speed + if (front) 250 else 0) * (1 + (a.speedPct + if (active) ab.speedPct else 0.0) / 100)
            val spread = if (amethyst) a.amethystSpread else a.spread + if (active) ab.spread else 0.0
            val blocks = 20 / breakTicks(speed, c) * (1 + floor(max(0.0, spread)) / 100)
            return if (ab.burst) blocks + input.pickobulusBlocks / cooldown else blocks
        }

        fun slice(weight: Double, active: Boolean, front: Boolean) {
            if (weight <= 0) return
            val blocks = blocksPerSecond(active, front)
            var fortune = a.fortune + if (front) 150 else 0
            if (active && ab.stack > 0) {
                val toCap = ab.cap / ab.stack / blocks
                fortune += if (toCap >= duration) ab.stack * blocks * duration / 2 else (ab.cap * (duration - toCap) + ab.cap / 2 * toCap) / duration
            }
            val items = blocks * block.count * node * (1 + fortune / 100)
            val doubled = if (active && ab.voidDouble) 2.0 else 1.0
            val powder = blocks * powderBase * (1 + a.powderPct / 100) * node * (if (front) 3 else 1) * (1 + a.doublePowder / 100) *
                if (block.powder == Powder.VOID) doubled else 1.0
            r.items += weight * items
            if (block.powder == Powder.ENDER) r.ender += weight * powder else r.void += weight * powder
            r.xp += weight * blocks * block.xp * node * (1 + max(0.0, a.wisdom) / 100)
            if (amethyst) {
                r.flawed += weight * items * a.pristine * doubled / 100
                r.fine += weight * blocks * a.fineCut / 100
                r.fragments += weight * blocks * FRAGMENT_CHANCE * find * (1 + (if (input.core >= 9) 1 else 0) + a.fragmentPct / 100) * (1 + a.giftPct / 100)
            }
            r.rare += weight * blocks * find
        }

        val day = input.hoursPerDay * 3600
        val front = if (a.frontLoaded) min(1.0, FRONT_LOADED_BLOCKS / ((1 - uptime) * blocksPerSecond(false, true) + uptime * blocksPerSecond(true, true)) / day) else 0.0
        slice((1 - front) * (1 - uptime), active = false, front = false)
        slice((1 - front) * uptime, active = true, front = false)
        slice(front * (1 - uptime), active = false, front = true)
        slice(front * uptime, active = true, front = true)
        if (a.dailyEnder) r.ender += DAILY_POWDER * input.tier / day
        if (a.dailyVoid) r.void += DAILY_POWDER * input.tier / day
        r.powder = r.ender + c.voidWorth * r.void
        return r
    }

    private class Best(val ability: String?, val value: Double)

    private fun score(r: Rates, ref: Map<Goal, Double>, goals: Set<Goal>): Double {
        var s = 0.0
        for (g in goals) {
            val top = ref[g] ?: 0.0
            if (top > 0) s += r[g] / top
        }
        return s
    }

    private fun best(a: Stats, abilities: List<String>, ref: Map<Goal, Double>, c: Context): Best {
        var out = Best(null, score(rates(a, null, c), ref, c.input.goals))
        for (ab in abilities) {
            val value = score(rates(a, ab, c), ref, c.input.goals)
            if (value > out.value + EPSILON) out = Best(ab, value)
        }
        return out
    }

    private fun powderWorth(perks: List<Perk>, input: PlanInput): Double {
        val none = stats(perks, emptyMap(), Context(input, HeartBlock.END_STONE, false, 1.0))
        fun fastest(powder: Powder) = HeartBlock.entries.filter { it.powder == powder }.maxOf {
            val r = rates(none, null, Context(input, it, false, 1.0))
            if (powder == Powder.ENDER) r.ender else r.void
        }
        return fastest(Powder.ENDER) / fastest(Powder.VOID)
    }

    fun plan(input: PlanInput): Plan {
        val perks = Perks.all
        val c = Context(input, input.block, input.nodes && input.block.nodes, powderWorth(perks, input))
        fun weight(p: Perk) = if (p.powder == Powder.VOID) c.voidWorth else 1.0
        val allAbilities = perks.filter { it.ability }.map { it.name }
        val start = perks.associate { it.name to min(it.max, input.levels[it.name] ?: 0) }
        val maxed = stats(perks, perks.associate { it.name to it.max }, c)
        val ref = Goal.entries.associateWith { g -> (listOf(null) + allAbilities).maxOf { rates(maxed, it, c)[g] } }

        val picked = LinkedHashSet(perks.filter { start.getValue(it.name) > 0 }.map { it.name })
        val unlocks = ArrayList<Perk>()
        fun pickedAbilities() = allAbilities.filter { it in picked }
        var free = tokens(input.tier, input.core) - picked.size
        while (free > 0) {
            val atMax = HashMap<String, Int>()
            for (p in perks) atMax[p.name] = if (p.name in picked) p.max else 0
            val base = best(stats(perks, atMax, c), pickedAbilities(), ref, c).value
            var top: Perk? = null
            var topGain = 0.0
            for (p in perks) {
                if (p.name in picked || p.tier > input.tier) continue
                picked += p.name
                atMax[p.name] = p.max
                val gain = best(stats(perks, atMax, c), pickedAbilities(), ref, c).value - base
                atMax[p.name] = 0
                picked -= p.name
                if (gain > 1e-9 && (top == null || gain > topGain + EPSILON)) {
                    top = p
                    topGain = gain
                }
            }
            top ?: break
            picked += top.name
            unlocks += top
            free--
        }

        val levels = HashMap(start)
        for (p in unlocks) levels[p.name] = 1
        val abilities = pickedAbilities()
        val steps = unlocks.mapTo(ArrayList()) { Step(it.name, null, 0, 1, 0) }
        var current = best(stats(perks, levels, c), abilities, ref, c)

        fun buy(p: Perk, to: Int) {
            val from = levels.getValue(p.name)
            steps += Step(p.name, p.powder, from, to, p.cost(from, to))
            levels[p.name] = to
            current = best(stats(perks, levels, c), abilities, ref, c)
        }

        fun open() = perks.filter { it.name in picked && levels.getValue(it.name) in 1 until it.max }
        while (true) {
            var top: Perk? = null
            var topTo = 0
            var topRatio = 0.0
            for (p in open()) {
                val without = stats(perks, levels, c, p)
                var spent = 0.0
                for (to in levels.getValue(p.name) + 1..p.max) {
                    spent += p.cost[to - 1] * weight(p)
                    val a = without.copy()
                    add(a, p, to, c)
                    val gain = best(a, abilities, ref, c).value - current.value
                    val ratio = gain / max(1.0, spent)
                    if (gain > EPSILON && (top == null || ratio > topRatio * (1 + 1e-9))) {
                        top = p
                        topTo = to
                        topRatio = ratio
                    }
                }
            }
            if (top != null) {
                buy(top, topTo)
                continue
            }
            val left = open()
            if (left.isEmpty()) break
            val all = HashMap(levels)
            for (p in left) all[p.name] = p.max
            if (best(stats(perks, all, c), abilities, ref, c).value - current.value <= EPSILON) break
            fun next(p: Perk) = p.cost[levels.getValue(p.name)] * weight(p)
            val cheapest = left.reduce { x, y -> if (next(y) < next(x)) y else x }
            buy(cheapest, levels.getValue(cheapest.name) + 1)
        }
        return Plan(input, steps, current.ability)
    }

    fun batch(steps: List<Step>, ender: Long, void: Long): Batch {
        val purse = longArrayOf(ender, void)
        val blocked = booleanArrayOf(false, false)
        val now = LinkedHashMap<String, Step>()
        val later = ArrayList<Step>()
        fun buy(s: Step) {
            val n = now[s.perk]
            now[s.perk] = if (n == null) s else n.copy(to = s.to, cost = n.cost + s.cost, powder = n.powder ?: s.powder)
        }
        for (s in steps) {
            val powder = s.powder
            if (powder == null) {
                buy(s)
                continue
            }
            var to = s.from
            if (!blocked[powder.ordinal]) {
                val perk = Perks.byName.getValue(s.perk)
                while (to < s.to && perk.cost[to] <= purse[powder.ordinal]) purse[powder.ordinal] -= perk.cost[to++]
                if (to > s.from) buy(s.copy(to = to, cost = perk.cost(s.from, to)))
                if (to == s.to) continue
                blocked[powder.ordinal] = true
            }
            val rest = if (to == s.from) s else s.copy(from = to, cost = Perks.byName.getValue(s.perk).cost(to, s.to))
            val last = later.lastOrNull()
            if (last != null && last.perk == s.perk) later[later.lastIndex] = last.copy(to = s.to, cost = last.cost + rest.cost)
            else later += rest
        }
        return Batch(now.values.toList(), later)
    }
}
