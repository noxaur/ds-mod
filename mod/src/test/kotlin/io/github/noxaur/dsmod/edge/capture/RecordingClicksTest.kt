package io.github.noxaur.dsmod.edge.capture

import net.minecraft.client.KeyMapping
import kotlin.test.Test
import kotlin.test.assertFalse

class RecordingClicksTest {
    @Test
    fun `bracket presses with no run are not kept for the next one`() {
        val key = KeyMapping("key.dsmod.next_step", 93, KeyMapping.Category.MISC)
        val clicks = KeyMapping::class.java.getDeclaredField("clickCount").apply { isAccessible = true }
        clicks.setInt(key, 4)
        val next = Recording::class.java.getDeclaredField("nextStep").apply { isAccessible = true }
        next.set(Recording, key)

        val tick = Recording::class.java.getDeclaredMethod("tick", net.minecraft.client.Minecraft::class.java).apply { isAccessible = true }
        tick.invoke(Recording, null)

        assertFalse(key.consumeClick(), "A press survived a tick with no run")

        clicks.setInt(key, 2)
        assertFalse(Recording.cancel())
        assertFalse(key.consumeClick(), "A press survived cancel")
    }
}
