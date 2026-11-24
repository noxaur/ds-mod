package io.github.noxaur.dsmod.core.feature.end.protector.protectorstatus

import io.github.noxaur.dsmod.harness.chat
import io.github.noxaur.dsmod.harness.Harness
import io.github.noxaur.dsmod.harness.lines
import io.github.noxaur.dsmod.core.game.TabList
import kotlin.test.Test
import kotlin.test.assertEquals

class ProtectorStatusTest : Harness() {
    private fun tab(value: String) = post(TabList(lines("Area: The End", "Protector: $value")))

    private fun stage(): String? {
        pass(1_000)
        return rows(tree.feature<ProtectorStatus>())?.first()
    }

    @Test
    fun `a defeat line during the fight shows Defeated`() {
        tab("Spawned")
        post(chat("The Endstone Protector has been defeated!"))
        assertEquals("Stage: Defeated", stage())
    }

    @Test
    fun `a defeat line after the row has left Spawned does not stick the HUD on Defeated`() {
        tab("Resting (200)")
        post(chat("The Endstone Protector has been defeated!"))
        assertEquals("Stage: Resting", stage())
    }
}
