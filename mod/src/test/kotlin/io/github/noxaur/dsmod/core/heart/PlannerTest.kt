package io.github.noxaur.dsmod.core.heart

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PlannerTest {
    private val endStone = PlanInput(HeartBlock.END_STONE, false, MiningArea.ANYWHERE, setOf(Goal.ITEMS, Goal.POWDER), 10, 10, Base(), emptyMap())

    private fun levelsAfter(steps: List<Step>): Map<String, Int> = buildMap { for (s in steps) put(s.perk, s.to) }

    @Test
    fun following_the_plan_and_planning_again_gives_the_rest_of_it() {
        val plan = Planner.plan(endStone)
        assertTrue(plan.steps.size > 100, "The plan stopped early: ${plan.steps.size} steps")
        for (done in listOf(1, 20, 21, plan.steps.size / 3, plan.steps.size - 1)) {
            val levels = levelsAfter(plan.steps.take(done))
            val again = Planner.plan(endStone.copy(levels = levels))
            assertEquals(plan.after(levels), again.steps, "Planning again after $done steps")
        }
    }

    @Test
    fun tokens_go_first_and_never_past_what_the_tier_and_core_give() {
        val steps = Planner.plan(endStone).steps
        val unlocks = steps.takeWhile { it.powder == null }
        assertEquals(20, Planner.tokens(10, 10))
        assertTrue(unlocks.size in 1..Planner.tokens(10, 10))
        assertTrue(steps.drop(unlocks.size).all { it.powder != null })
    }

    @Test
    fun the_powder_you_have_pays_for_one_upgrade_per_perk_in_plan_order() {
        val steps = Planner.plan(endStone).steps
        val batch = Planner.batch(steps, 1_334_335, 1_073_221)
        assertEquals(batch.now.map { it.perk }.distinct(), batch.now.map { it.perk })
        assertTrue(batch.now.filter { it.powder == Powder.ENDER }.sumOf { it.cost } <= 1_334_335)
        assertTrue(batch.now.filter { it.powder == Powder.VOID }.sumOf { it.cost } <= 1_073_221)
        assertEquals(steps.sumOf { it.cost }, batch.now.sumOf { it.cost } + batch.later.sumOf { it.cost })
        val tokensOnly = Planner.batch(steps, 0, 0)
        assertEquals(steps.filter { it.powder == null }.map { it.perk }, tokensOnly.now.map { it.perk })
    }

    @Test
    fun a_level_bought_shortens_its_step_and_its_cost() {
        val plan = Planner.plan(endStone)
        val run = plan.steps.first { it.powder != null && it.to - it.from > 1 }
        val levels = levelsAfter(plan.steps.takeWhile { it !== run }) + (run.perk to run.from + 1)
        val left = plan.after(levels).first { it.perk == run.perk }
        assertEquals(run.from + 1, left.from)
        assertEquals(Perks.byName.getValue(run.perk).cost(run.from + 1, run.to), left.cost)
    }
}
