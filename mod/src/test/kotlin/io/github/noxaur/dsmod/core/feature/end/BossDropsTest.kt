package io.github.noxaur.dsmod.core.feature.end

import io.github.noxaur.dsmod.core.config.DsModConfig
import io.github.noxaur.dsmod.core.feature.Game
import io.github.noxaur.dsmod.core.feature.Saved
import io.github.noxaur.dsmod.core.feature.features
import io.github.noxaur.dsmod.core.game.ServerSpan
import io.github.noxaur.dsmod.core.game.ServerText
import io.github.noxaur.dsmod.core.game.SystemMessage
import io.github.noxaur.dsmod.core.game.TabList
import kotlin.test.Test
import kotlin.test.assertEquals

class BossDropsTest {
    private val game = Game(features(DsModConfig()))

    private fun say(time: Long, line: String) = game.post(time, SystemMessage(ServerText(ServerSpan(line))))

    private fun down(time: Long, name: String) = say(
        time,
        """
        ▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬
                            $name DOWN!

                    Your Damage: 5,552,752 (Position #4)

        ▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬
        """.trimIndent(),
    )

    @Suppress("UNCHECKED_CAST")
    private fun bosses() = (game.readouts.single { it.feature.id == "boss-drops" }.feature.state as Saved<BossLog>).value.bosses

    @Test
    fun `loot rows stay with the dragon whose header opened them`() {
        game.post(0, TabList(listOf(ServerText(ServerSpan("Area: The End")))))
        down(330_500, "UNSTABLE DRAGON")
        // A second dragon can fall during the ten seconds before the first dragon's loot.
        down(335_000, "CHAOTIC DRAGON")
        say(340_500, "Here is your loot from the last dragon:")
        say(340_500, " - 10x Unstable Dragon Fragment")

        assertEquals(mapOf("Unstable Dragon Fragment" to 10), bosses()["Unstable Dragon"]?.loot)
        assertEquals(emptyMap(), bosses()["Chaotic Dragon"]?.loot)
    }
}
