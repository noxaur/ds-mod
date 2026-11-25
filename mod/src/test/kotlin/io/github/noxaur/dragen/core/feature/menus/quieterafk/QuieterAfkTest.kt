package io.github.noxaur.dragen.core.feature.menus.quieterafk

import io.github.noxaur.dragen.core.game.ContainerOpened
import io.github.noxaur.dragen.harness.Harness
import io.github.noxaur.dragen.harness.readCapture
import io.github.noxaur.dragen.harness.text
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class QuieterAfkTest : Harness(alone = QuieterAfk::class) {
    private val afk = tree.feature<QuieterAfk>()

    @Test
    fun `the screen is quiet only while a counting AFK menu is open`() {
        val quiet = ArrayList<String>()
        for (captured in readCapture(Path.of("src/test/resources/features/quieter-afk/open-and-leave.jsonl"))) {
            game.post(captured.time, captured.event)
            game.tick(captured.time)
            val opened = captured.event as? ContainerOpened ?: continue
            quiet += "${opened.title?.plain ?: "inventory"}: ${afk.quiet}"
        }
        val changes = quiet.filterIndexed { i, line -> i == 0 || line.substringAfterLast(' ') != quiet[i - 1].substringAfterLast(' ') }
        assertEquals(
            listOf(
                "inventory: false",
                "AFK Rewards · 15:00: true",
                "Compensation Rewards: false",
                "AFK Rewards · 15:00: true",
                "inventory: false",
                "AFK Rewards · 15:00: true",
                "inventory: false",
            ),
            changes,
            quiet.joinToString("\n"),
        )
    }

    @Test
    fun `the screen is not quiet with the feature off`() {
        post(ContainerOpened(5, text("${QuieterAfk.TITLE}15:00"), 90))
        afk.feature.enabled = false
        assertFalse(afk.quiet)
    }

    @Test
    fun `the menu stays lit for half a minute after the mouse last moved`() {
        post(ContainerOpened(5, text("${QuieterAfk.TITLE}15:00"), 90))
        assertTrue(afk.lit(time, 10, 10))
        assertTrue(afk.lit(time + QuieterAfk.LIT_MILLIS - 1, 10, 10))
        assertFalse(afk.lit(time + QuieterAfk.LIT_MILLIS, 10, 10))
        assertTrue(afk.lit(time + 40_000, 11, 10))
        assertTrue(afk.lit(time + 40_000 + QuieterAfk.LIT_MILLIS - 1, 11, 10))
    }

    @Test
    fun `the menu lights again on each visit, but not on the server's reopen each second`() {
        post(ContainerOpened(5, text("${QuieterAfk.TITLE}15:00"), 90))
        afk.lit(time, 10, 10)
        post(ContainerOpened(5, text("${QuieterAfk.TITLE}14:59"), 90))
        assertFalse(afk.lit(time + QuieterAfk.LIT_MILLIS, 10, 10))
        post(ContainerOpened(0, null, 46))
        post(ContainerOpened(7, text("${QuieterAfk.TITLE}15:00"), 90))
        assertTrue(afk.lit(time + QuieterAfk.LIT_MILLIS, 10, 10))
    }
}
