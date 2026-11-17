package io.github.noxaur.dsmod.core.hand

import com.google.gson.Gson
import io.github.noxaur.dsmod.core.config.SettingsTree
import io.github.noxaur.dsmod.core.feature.general.Animations
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class HeldItemTest {
    @Test
    fun `speed 1 leaves the game's own swing, even one Haste cut to nothing`() {
        assertEquals(6, HeldItem.swingTicks(6, 1f))
        assertEquals(0, HeldItem.swingTicks(0, 1f))
    }

    @Test
    fun `a faster swing is shorter and never under a tick, and a slower one longer`() {
        assertEquals(3, HeldItem.swingTicks(6, 2f))
        assertEquals(2, HeldItem.swingTicks(6, 3f))
        assertEquals(1, HeldItem.swingTicks(2, 3f))
        assertEquals(12, HeldItem.swingTicks(6, 0.5f))
        assertEquals(60, HeldItem.swingTicks(6, 0.1f))
    }

    @Test
    fun `the pose eases in over a few frames, holds, and eases back out to nothing`() {
        val ease = Ease()
        val second = 1_000_000_000L
        assertEquals(0f, ease.at(1f, second))
        val frame = second / 60
        val early = ease.at(1f, second + frame)
        assertTrue(early > 0.2f && early < 0.6f, "One frame in it is at $early")
        var now = second + frame
        repeat(12) {
            now += frame
            ease.at(1f, now)
        }
        assertTrue(ease.shown > 0.99f, "After a fifth of a second it is at ${ease.shown}")
        repeat(30) {
            now += frame
            ease.at(0f, now)
        }
        assertEquals(0f, ease.shown)
    }

    @Test
    fun `Reset puts every Animations setting back to its default and leaves the switch alone`() {
        val settings = SettingsTree().feature<Animations>()
        settings.enabled = false
        settings.swingStyle.value = SwingStyle.EXHIBITION
        settings.swingSpeed.value = 2f
        settings.itemSize.value = 0.5f
        settings.itemX.value = 0.3f
        settings.itemY.value = -0.2f
        settings.itemZ.value = 0.1f
        settings.itemYaw.value = 20f
        settings.itemPitch.value = -30f
        settings.itemRoll.value = 45f
        settings.scaleSwing.value = false
        settings.noSwing.value = true
        settings.noBowSwing.value = true
        settings.ignoreHaste.value = true
        settings.noEquipReset.value = true
        settings.reset()
        val fresh = SettingsTree().feature<Animations>()
        assertEquals(false, settings.enabled)
        fun Animations.values() = listOf(swingStyle, swingSpeed, itemSize, itemX, itemY, itemZ, itemYaw, itemPitch, itemRoll, scaleSwing, noSwing, noBowSwing, ignoreHaste, noEquipReset).map { it.value }
        assertEquals(fresh.values(), settings.values())
    }

    @Test
    fun `a saved Still style loads as Vanilla rather than nothing`() {
        assertEquals(SwingStyle.VANILLA, Gson().fromJson("\"STILL\"", SwingStyle::class.java))
        assertEquals("\"VANILLA\"", Gson().toJson(SwingStyle.VANILLA))
    }
}
