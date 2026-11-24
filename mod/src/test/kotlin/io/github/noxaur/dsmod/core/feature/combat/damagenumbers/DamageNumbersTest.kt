package io.github.noxaur.dsmod.core.feature.combat.damagenumbers

import io.github.noxaur.dsmod.harness.Harness
import io.github.noxaur.dsmod.harness.text
import io.github.noxaur.dsmod.core.game.FloatingText
import io.github.noxaur.dsmod.core.game.ServerSpan
import io.github.noxaur.dsmod.core.game.ServerText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class DamageNumbersTest : Harness(alone = DamageNumbers::class) {
    private val feature = tree.feature<DamageNumbers>().also { it.feature.enabled = true }

    private val hit = text("74,540,375", GRAY)
    private val crit = text(ServerSpan("✧1,", WHITE), ServerSpan("461", YELLOW), ServerSpan(",8", RED), ServerSpan("45✧", WHITE))

    private fun seen(text: ServerText): String? = if (post(FloatingText(text))) (game.replaced?.plain ?: text.plain) else null

    @Test
    fun `a hit and a crit read short, the crit keeping its stars and colors`() {
        assertEquals("74.5M", seen(hit))
        assertEquals("✧1.5M✧", seen(crit))
        assertEquals(listOf(WHITE, WHITE, YELLOW, RED, WHITE, WHITE), game.replaced!!.spans.flatMap { span -> span.text.map { span.rgb } })
    }

    @Test
    fun `the decimals follow the setting`() {
        feature.decimals.value = 0f
        assertEquals("75M", seen(hit))
        feature.decimals.value = 3f
        assertEquals("74.54M", seen(hit))
    }

    @Test
    fun `a number under a thousand is left as the server sent it`() {
        val small = text("338", GRAY)
        assertEquals(true, post(FloatingText(small)))
        assertNull(game.replaced)
    }

    @Test
    fun `with short numbers off the server's number shows unchanged`() {
        feature.shorten.value = false
        assertEquals("74,540,375", seen(hit))
        assertNull(game.replaced)
    }

    @Test
    fun `crits only hides the hits and keeps the crits`() {
        feature.shown.value = DamageShown.CRITS
        assertNull(seen(hit))
        assertEquals("✧1.5M✧", seen(crit))
    }

    @Test
    fun `none hides every damage number`() {
        feature.shown.value = DamageShown.NONE
        assertNull(seen(hit))
        assertNull(seen(crit))
    }

    @Test
    fun `text with a letter or a slash is not a damage number`() {
        feature.shown.value = DamageShown.NONE
        for (other in listOf("[Lv55] Zealot 13000/13000", "Opens in 5s", "2x Protector Dragon Fragment", "13000/13000", "Healing Fel 1M❤", "⏣ Void Sepulture\nStep on the pad")) {
            assertEquals(other, seen(text(other)), other)
        }
        assertEquals(emptyMap(), game.metrics.snapshot().counters)
    }

    @Test
    fun `a starred text that is not a number counts as unparsed`() {
        seen(text("✧Critical✧"))
        assertEquals(1L, game.metrics.snapshot().counters["damage-numbers.unparsed"])
    }

    private companion object {
        const val GRAY = 0xAAAAAA
        const val WHITE = 0xFFFFFF
        const val YELLOW = 0xFFFF55
        const val RED = 0xFF5555
    }
}
