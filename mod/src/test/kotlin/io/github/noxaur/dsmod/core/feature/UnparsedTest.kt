package io.github.noxaur.dsmod.core.feature

import io.github.noxaur.dsmod.core.config.DsModConfig
import io.github.noxaur.dsmod.core.game.ServerSpan
import io.github.noxaur.dsmod.core.game.ServerText
import io.github.noxaur.dsmod.core.game.TabList
import kotlin.test.Test
import kotlin.test.assertEquals

class UnparsedTest {
    private val game = Game(listOf(features(DsModConfig()).single { it.id == "golem-status" }.also { it.enabled = true }))

    private fun tab(vararg rows: String) = TabList(rows.map { ServerText(listOf(ServerSpan(it))) })

    @Test
    fun `a line that reached a feature and failed to parse is counted and handed to a listener`() {
        val heard = ArrayList<Pair<String, String>>()
        game.metrics.onUnparsed = { feature, sample -> heard += feature to sample }

        game.post(1, tab("Protector: Hatching (soon)"))

        assertEquals(listOf("golem-status" to "Hatching (soon)"), heard)
        assertEquals(1L, game.metrics.snapshot().counters["golem-status.unparsed"])
    }

    @Test
    fun `nobody is told when nobody listens`() {
        game.post(1, tab("Protector: Hatching (soon)"))

        assertEquals(1L, game.metrics.snapshot().counters["golem-status.unparsed"])
    }
}
