package io.github.noxaur.dsmod.core.feature.general

import io.github.noxaur.dsmod.core.camera.KeyMode
import io.github.noxaur.dsmod.core.config.KeyBind
import io.github.noxaur.dsmod.core.config.SettingsTree
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AutoClickerTest {
    private val clicker = SettingsTree().feature<AutoClicker>()
    private val trigger = clicker.left

    private data class Tick(val walking: Boolean = true, val pressed: Boolean = false, val down: Boolean = true, val on: Boolean = true)

    private fun clicks(ticks: List<Tick>) = ticks.indices.filter { ticks[it].let { t -> trigger.tick(t.on, t.walking, t.pressed, t.down) } }

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
    fun `starts off, with the left and right mouse buttons held`() {
        assertEquals(false, clicker.feature.enabled)
        assertEquals(0 to KeyMode.HOLD, clicker.left.key.value to clicker.left.mode.value)
        assertEquals(1 to KeyMode.HOLD, clicker.right.key.value to clicker.right.mode.value)
    }
}
