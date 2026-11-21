package io.github.noxaur.dsmod.core.hand

import io.github.noxaur.dsmod.core.config.SettingsTree
import io.github.noxaur.dsmod.core.feature.general.Animations
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
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
    fun `the preview swings through each tick of the swing, then rests before the next`() {
        val tick = 50_000_000L
        assertEquals(0f, HeldItem.previewSwing(0, 6))
        assertEquals(0.5f, HeldItem.previewSwing(3 * tick, 6))
        assertEquals(5 / 6f, HeldItem.previewSwing(5 * tick, 6))
        assertEquals(0f, HeldItem.previewSwing(6 * tick, 6), "The swing has ended")
        assertEquals(0f, HeldItem.previewSwing((6 + HeldItem.PREVIEW_REST - 1) * tick, 6), "It rests")
        assertEquals(0.5f, HeldItem.previewSwing((6 + HeldItem.PREVIEW_REST + 3) * tick, 6), "The next swing")
    }

    @Test
    fun `Left and Right step through the preview's views and stop at either end`() {
        assertEquals(HandPreview.SWINGING, HandPreview.HELD.step(1))
        assertEquals(HandPreview.SCREEN, HandPreview.SWINGING.step(1))
        assertEquals(HandPreview.SCREEN, HandPreview.SCREEN.step(1))
        assertEquals(HandPreview.HELD, HandPreview.HELD.step(-1))
    }

    @Test
    fun `a sword at rest shows its own corner of the screen, in the box's shape`() {
        val sword = Frame(0.6f, 0.55f, 0.85f, 1f)
        val frame = ScreenView.frame(sword, right = true, shape = 1.5f)
        assertEquals(1f, frame.right, 1e-5f)
        assertEquals(1f, frame.bottom, 1e-5f)
        assertEquals(1.5f, frame.width / frame.height, 1e-4f)
        assertTrue(frame.left <= sword.left && frame.top <= sword.top, "It holds the sword: $frame")
        assertTrue(frame.onScreen, "It stays on the screen: $frame")
    }

    @Test
    fun `an item moved to the top left shows the whole screen, and one in the left hand its own corner`() {
        val frame = ScreenView.frame(Frame(0.01f, 0.01f, 0.3f, 0.4f), right = true, shape = 1f)
        assertEquals(Frame.SCREEN, frame)
        val left = ScreenView.frame(Frame(0.15f, 0.55f, 0.4f, 1f), right = false, shape = 1.5f)
        assertEquals(0f, left.left, 1e-5f)
        assertEquals(1f, left.bottom, 1e-5f)
    }

    @Test
    fun `room around an item stops at the screen's edge, so an item that only reaches it stays on screen`() {
        val frame = ScreenView.frame(Frame(0.6f, 0.55f, 1f, 1f), right = true, shape = 1.5f)
        assertTrue(frame.onScreen, "$frame")
    }

    @Test
    fun `an item past the screen's edge takes the view past it too`() {
        val frame = ScreenView.frame(Frame(0.9f, 0.6f, 1.3f, 1f), right = true, shape = 1.5f)
        assertTrue(frame.right >= 1.3f, "$frame")
        assertFalse(frame.onScreen)
        assertEquals(1.5f, frame.width / frame.height, 1e-4f)
    }

    @Test
    fun `a close view centers the item with room around it, in the box's shape`() {
        val item = Frame(0.6f, 0.5f, 0.8f, 1f)
        val frame = ScreenView.fit(item, shape = 2f)
        assertEquals(0.7f, (frame.left + frame.right) / 2, 1e-5f)
        assertEquals(0.75f, (frame.top + frame.bottom) / 2, 1e-5f)
        assertEquals(2f, frame.width / frame.height, 1e-4f)
        assertTrue(frame.top < item.top && frame.bottom > item.bottom)
    }

    @Test
    fun `Reset puts every Animations setting back to its default and leaves the switch alone`() {
        val settings = SettingsTree().feature<Animations>()
        settings.feature.enabled = false
        settings.style.value = HandStyle.STAB
        settings.swingSpeed.value = 2f
        settings.turn.value = 40f
        settings.reach.value = 0f
        settings.itemSize.value = 0.5f
        settings.itemX.value = -1.5f
        settings.itemY.value = 1.2f
        settings.itemZ.value = 0.1f
        settings.itemYaw.value = 20f
        settings.itemPitch.value = -30f
        settings.itemRoll.value = 45f
        settings.flip.value = true
        settings.scaleSwing.value = false
        settings.noSwing.value = true
        settings.noBowSwing.value = true
        settings.ignoreHaste.value = true
        settings.noEquipReset.value = true
        settings.blocking.value = true
        settings.reset()
        val fresh = SettingsTree().feature<Animations>()
        assertEquals(false, settings.feature.enabled)
        fun Animations.values() = listOf(
            style, swingSpeed, turn, reach, itemSize, itemX, itemY, itemZ, itemYaw, itemPitch, itemRoll, flip, scaleSwing, noSwing, noBowSwing,
            ignoreHaste, noEquipReset, blocking,
        ).map { it.value }
        assertEquals(fresh.values(), settings.values())
    }

    @Test
    fun `each old swing style loads as the style that copies it, and Modern saves as Vanilla did`() {
        fun load(name: String) = Json.decodeFromString(HandStyle.serializer(), "\"$name\"")
        assertEquals(HandStyle.V1_7, load("BLOCK_HIT"))
        assertEquals(HandStyle.V1_7, load("EXHIBITION"))
        assertEquals(HandStyle.MODERN, load("VANILLA"))
        assertEquals(HandStyle.MODERN, load("SPIN"))
        assertEquals(HandStyle.MODERN, load("STILL"))
        assertEquals(HandStyle.STAB, load("STAB"))
        assertEquals("\"VANILLA\"", Json.encodeToString(HandStyle.serializer(), HandStyle.MODERN))
    }
}
