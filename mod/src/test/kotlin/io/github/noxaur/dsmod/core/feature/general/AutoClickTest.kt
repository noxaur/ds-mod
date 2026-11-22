package io.github.noxaur.dsmod.core.feature.general

import io.github.noxaur.dsmod.core.camera.KeyMode
import io.github.noxaur.dsmod.core.config.KeyBind
import io.github.noxaur.dsmod.core.config.SettingsTree
import io.github.noxaur.dsmod.core.input.ClickBudget
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AutoClickTest {
    private companion object {
        const val TICK = 50_000_000L
    }

    private val tree = SettingsTree()
    private val trigger = tree.feature<AutoLeftClick>().click
    private val right = tree.feature<AutoRightClick>().click
    private val budget = ClickBudget()

    private data class Tick(val walking: Boolean = true, val pressed: Boolean = false, val down: Boolean = true, val on: Boolean = true)

    private var nanos = 0L

    private fun step(t: Tick): Boolean {
        nanos += TICK
        return trigger.tick(t.on, t.walking, t.pressed, t.down) && budget.spend(nanos)
    }

    private fun clicks(ticks: List<Tick>) = ticks.indices.filter { step(ticks[it]) }

    private fun held(count: Int, walking: Boolean = true) = listOf(Tick(walking, pressed = true)) + List(count - 1) { Tick(walking) }

    @Test
    fun `clicks 9 point 9 times a second on average while held and walking`() {
        assertEquals(990, clicks(held(20 * 100)).size)
    }

    @Test
    fun `clicks on the press and never two ticks running`() {
        val ticks = clicks(held(20 * 10))
        assertEquals(0, ticks.first())
        assertTrue(ticks.zipWithNext().all { (a, b) -> b - a in 2..3 })
    }

    @Test
    fun `auto clicks fill only what the player's own clicks leave of 9 point 9 a second`() {
        val auto = held(20 * 100).withIndex().count { (i, t) -> step(t).also { if (i % 4 == 0) budget.count(nanos) } }
        assertEquals(990, auto + 500)
    }

    @Test
    fun `the player's own clicks past 9 point 9 a second leave no auto clicks`() {
        assertEquals(0, held(20 * 10).count { t -> budget.count(nanos).let { step(t) } })
    }

    @Test
    fun `ticks the game catches up on after a freeze release no burst`() {
        clicks(held(20))
        nanos += 500_000_000L
        assertEquals(1, (0 until 10).count { trigger.tick(true, true, false, true) && budget.spend(nanos) })
    }

    @Test
    fun `uneven ticks never fit 11 clicks in a second`() {
        val random = Random(1)
        val at = (0 until 20 * 30).map { it * TICK + random.nextLong(-20_000_000L, 20_000_000L) }
        val admitted = at.filter { trigger.tick(true, true, false, true) && budget.spend(it) }
        assertTrue(admitted.size > 280)
        assertTrue(admitted.windowed(11).all { it.last() - it.first() >= 1_000_000_000L })
    }

    @Test
    fun `holds only while walking`() {
        assertEquals(emptyList(), clicks(held(100, walking = false)))
    }

    @Test
    fun `stops on the release under Hold`() {
        val ticks = clicks(held(20) + List(40) { Tick(down = false) })
        assertTrue(ticks.all { it < 20 })
    }

    @Test
    fun `Hold clicks for a key already down without a press`() {
        assertEquals(10, clicks(List(20) { Tick() }).size)
    }

    @Test
    fun `a stop saves up no burst`() {
        val walking = held(40) + List(100) { Tick(walking = false) } + List(40) { Tick() }
        val ticks = clicks(walking)
        assertTrue(ticks.zipWithNext().all { (a, b) -> b - a >= 2 })
        for (start in 0 until walking.size - 20) assertTrue(ticks.count { it in start until start + 20 } <= 10)
    }

    @Test
    fun `a press starts Toggle and the next stops it`() {
        trigger.mode.value = KeyMode.TOGGLE
        val tap = listOf(Tick(pressed = true), Tick(down = false))
        val ticks = clicks(tap + List(38) { Tick(down = false) } + tap + List(38) { Tick(down = false) })
        assertTrue(ticks.isNotEmpty() && ticks.all { it < 40 })
        assertTrue(ticks.count() >= 19)
    }

    @Test
    fun `a held key that repeats its press does not flip Toggle`() {
        trigger.mode.value = KeyMode.TOGGLE
        val ticks = clicks(List(40) { Tick(pressed = true) })
        assertEquals(20, ticks.size)
    }

    @Test
    fun `turning the feature off ends a toggle`() {
        trigger.mode.value = KeyMode.TOGGLE
        val ticks = clicks(listOf(Tick(pressed = true, down = false), Tick(on = false, down = false)) + List(20) { Tick(down = false) })
        assertEquals(listOf(0), ticks)
    }

    @Test
    fun `a press under Hold does not start Toggle`() {
        clicks(listOf(Tick(pressed = true), Tick(down = false)))
        trigger.mode.value = KeyMode.TOGGLE
        assertEquals(emptyList(), clicks(List(20) { Tick(down = false) }))
    }

    @Test
    fun `clearing the key ends a toggle`() {
        trigger.mode.value = KeyMode.TOGGLE
        clicks(listOf(Tick(pressed = true, down = false)))
        trigger.key.value = KeyBind.NONE
        assertEquals(emptyList(), clicks(List(20) { Tick(down = false) }))
    }

    @Test
    fun `a key the other button uses is refused`() {
        trigger.key.value = 1
        assertEquals(0, trigger.key.value)
        right.key.value = 0
        assertEquals(1, right.key.value)
        right.key.value = KeyBind.NONE
        trigger.key.value = 1
        assertEquals(1, trigger.key.value)
        trigger.key.value = KeyBind.NONE
        right.key.value = KeyBind.NONE
        assertEquals(KeyBind.NONE to KeyBind.NONE, trigger.key.value to right.key.value)
    }

    @Test
    fun `both start off, on the left and right mouse buttons held`() {
        assertEquals(false to false, tree.feature<AutoLeftClick>().feature.enabled to tree.feature<AutoRightClick>().feature.enabled)
        assertEquals(0 to KeyMode.HOLD, trigger.key.value to trigger.mode.value)
        assertEquals(1 to KeyMode.HOLD, right.key.value to right.mode.value)
    }
}
