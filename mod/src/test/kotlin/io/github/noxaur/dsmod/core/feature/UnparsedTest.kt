package io.github.noxaur.dsmod.core.feature

import io.github.noxaur.dsmod.core.config.DsModConfig
import io.github.noxaur.dsmod.core.game.Area
import io.github.noxaur.dsmod.core.game.ServerSpan
import io.github.noxaur.dsmod.core.game.ServerText
import io.github.noxaur.dsmod.core.game.SystemMessage
import io.github.noxaur.dsmod.core.game.TabList
import kotlin.test.Test
import kotlin.test.assertEquals

class UnparsedTest {
    private val game = Game(listOf(features(DsModConfig()).single { it.id == "protector-status" }.also { it.enabled = true }))

    private fun tab(vararg rows: String) = TabList(rows.map { ServerText(listOf(ServerSpan(it))) })

    @Test
    fun `a line that reached a feature and failed to parse is counted and handed to a listener`() {
        val heard = ArrayList<Pair<String, String>>()
        game.metrics.onUnparsed = { feature, sample -> heard += feature to sample }

        game.post(1, tab("Protector: Hatching (soon)"))

        assertEquals(listOf("protector-status" to "Hatching (soon)"), heard)
        assertEquals(1L, game.metrics.snapshot().counters["protector-status.unparsed"])
    }

    @Test
    fun `nobody is told when nobody listens`() {
        game.post(1, tab("Protector: Hatching (soon)"))

        assertEquals(1L, game.metrics.snapshot().counters["protector-status.unparsed"])
    }

    @Test
    fun `a chat line whose head matches and whose pattern does not is counted for the feature`() {
        val eggs = ArrayList<String>()
        val egg = object : Feature("egg", ::on, Area.ANYWHERE) {
            init {
                line("The Dragon Egg is respawning", Regex("""The Dragon Egg is respawning in (\d+) seconds\.""")) { (seconds), _ -> eggs += seconds }
            }
        }
        val game = Game(listOf(egg))
        val heard = ArrayList<String>()
        game.metrics.onUnparsed = { _, sample -> heard += sample }

        for (line in listOf("The Dragon Egg is respawning in 30 seconds.", "The Dragon Egg is respawning soon!", "Player1: The Dragon Egg is respawning in 5 seconds.")) {
            game.post(1, SystemMessage(ServerText(listOf(ServerSpan(line)))))
        }

        assertEquals(listOf("30"), eggs)
        assertEquals(listOf("The Dragon Egg is respawning soon!"), heard)
    }

    private var on = true
}
