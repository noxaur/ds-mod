package io.github.noxaur.dsmod.core.game

import java.util.Base64
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SkullsTest {
    private fun property(json: String) = Base64.getEncoder().encodeToString(json.toByteArray())

    @Test
    fun `only a skin on Mojang's texture host is drawn`() {
        assertTrue(Skulls.onMojang(property("""{"textures":{"SKIN":{"url":"http://textures.minecraft.net/texture/abc"}}}""")))
        assertTrue(Skulls.onMojang(property("""{"textures":{"SKIN":{"url":"https://textures.minecraft.net/texture/abc"},"CAPE":{"url":"http://textures.minecraft.net/texture/def"}}}""")))
        assertFalse(Skulls.onMojang(property("""{"textures":{"SKIN":{"url":"http://textures.minecraft.net.evil.example/texture/abc"}}}""")))
        assertFalse(Skulls.onMojang(property("""{"textures":{"SKIN":{"url":"http://textures.minecraft.net/a"},"CAPE":{"url":"http://example.com/b"}}}""")))
        assertFalse(Skulls.onMojang(property("""{"textures":{"SKIN":{"url":"file://textures.minecraft.net/a"}}}""")))
        assertFalse(Skulls.onMojang(property("""{"textures":{}}""")))
        assertFalse(Skulls.onMojang("not base64!"))
    }
}
