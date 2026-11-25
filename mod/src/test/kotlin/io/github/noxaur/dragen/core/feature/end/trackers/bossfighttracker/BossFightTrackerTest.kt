package io.github.noxaur.dragen.core.feature.end.trackers.bossfighttracker

import io.github.noxaur.dragen.harness.Harness
import io.github.noxaur.dragen.harness.lines
import io.github.noxaur.dragen.core.feature.Saved
import io.github.noxaur.dragen.core.game.TabList
import io.github.noxaur.dragen.core.game.WorldChanged
import kotlin.test.Test
import kotlin.test.assertEquals

class BossFightTrackerTest : Harness() {

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
    private fun bosses() = (readout(tree.feature<BossFightTracker>()).feature.state as Saved<BossLog>).value.bosses

    @Test
    fun `loot rows stay with the dragon whose header opened them`() {
        game.post(0, TabList(lines("Area: The End")))
        down(330_500, "UNSTABLE DRAGON")
        down(335_000, "CHAOTIC DRAGON")
        say(340_500, "Here is your loot from the last dragon:")
        say(340_500, " - 10x Unstable Dragon Fragment")

        assertEquals(mapOf("Unstable Dragon Fragment" to 10), bosses()["Unstable Dragon"]?.loot)
        assertEquals(emptyMap(), bosses()["Chaotic Dragon"]?.loot)
    }

    @Test
    fun `a header with no earlier one goes to the dragon still inside the loot window`() {
        game.post(0, TabList(lines("Area: The End")))
        down(0, "UNSTABLE DRAGON")
        down(60_000, "CHAOTIC DRAGON")
        say(70_000, "Here is your loot from the last dragon:")
        say(70_000, " - 8x Chaotic Dragon Fragment")

        assertEquals(emptyMap(), bosses()["Unstable Dragon"]?.loot)
        assertEquals(mapOf("Chaotic Dragon Fragment" to 8), bosses()["Chaotic Dragon"]?.loot)
    }

    @Test
    fun `a world change drops a dragon still waiting for its loot`() {
        game.post(0, TabList(lines("Area: The End")))
        down(0, "UNSTABLE DRAGON")
        game.post(1_000, WorldChanged)
        game.post(1_500, TabList(lines("Area: The End")))
        down(2_000, "CHAOTIC DRAGON")
        say(12_000, "Here is your loot from the last dragon:")
        say(12_000, " - 8x Chaotic Dragon Fragment")

        assertEquals(emptyMap(), bosses()["Unstable Dragon"]?.loot)
        assertEquals(mapOf("Chaotic Dragon Fragment" to 8), bosses()["Chaotic Dragon"]?.loot)
    }
}
