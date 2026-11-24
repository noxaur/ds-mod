package io.github.noxaur.dsmod.core.feature

import io.github.noxaur.dsmod.core.config.DsModConfig
import io.github.noxaur.dsmod.harness.chat
import io.github.noxaur.dsmod.harness.Harness
import io.github.noxaur.dsmod.harness.lines
import io.github.noxaur.dsmod.core.feature.end.protector.protectorstatus.ProtectorStatus
import io.github.noxaur.dsmod.core.config.Store
import io.github.noxaur.dsmod.core.game.Area
import io.github.noxaur.dsmod.core.game.TabList
import kotlin.test.Test
import kotlin.test.assertEquals

class UnparsedTest : Harness(alone = ProtectorStatus::class) {
    private fun tab(vararg rows: String) = TabList(lines(*rows))

    @Test
    fun `a line that reached a feature and failed to parse is counted and handed to a listener`() {
        val heard = ArrayList<Pair<String, String>>()
        game.metrics.onUnparsed = { feature, sample -> heard += feature to sample }

        post(tab("Protector: Hatching (soon)"))

        assertEquals(listOf("protector-status" to "Hatching (soon)"), heard)
        assertEquals(1L, game.metrics.snapshot().counters["protector-status.unparsed"])
    }

    @Test
    fun `nobody is told when nobody listens`() {
        post(tab("Protector: Hatching (soon)"))

        assertEquals(1L, game.metrics.snapshot().counters["protector-status.unparsed"])
    }

    @Test
    fun `a chat line whose head matches and whose pattern does not is counted for the feature`() {
        val eggs = ArrayList<String>()
        val egg = Feature(DsModConfig(), "egg", Area.ANYWHERE, Store.END, "Reads the egg line in a test.").apply {
            line("The Dragon Egg is respawning", Regex("""The Dragon Egg is respawning in (\d+) seconds\.""")) { (seconds), _ -> eggs += seconds }
        }
        val game = Game(listOf(egg))
        val heard = ArrayList<String>()
        game.metrics.onUnparsed = { _, sample -> heard += sample }

        for (line in listOf("The Dragon Egg is respawning in 30 seconds.", "The Dragon Egg is respawning soon!", "Player1: The Dragon Egg is respawning in 5 seconds.")) {
            game.post(1, chat(line))
        }

        assertEquals(listOf("30"), eggs)
        assertEquals(listOf("The Dragon Egg is respawning soon!"), heard)
    }
}
