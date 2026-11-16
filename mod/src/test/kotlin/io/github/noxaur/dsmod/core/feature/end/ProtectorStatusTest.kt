package io.github.noxaur.dsmod.core.feature.end

import io.github.noxaur.dsmod.core.config.DsModConfig
import io.github.noxaur.dsmod.core.feature.Game
import io.github.noxaur.dsmod.core.feature.features
import io.github.noxaur.dsmod.core.game.ServerSpan
import io.github.noxaur.dsmod.core.game.ServerText
import io.github.noxaur.dsmod.core.game.SystemMessage
import io.github.noxaur.dsmod.core.game.TabList
import kotlin.test.Test
import kotlin.test.assertEquals

class ProtectorStatusTest {
    private val game = Game(features(DsModConfig()))

    private fun tab(value: String) =
        game.post(0, TabList(listOf("Area: The End", "Protector: $value").map { ServerText(ServerSpan(it)) }))

    private fun stage(): String? {
        game.tick(1_000)
        return game.readouts.single { it.feature.id == "protector-status" }.hud?.rows?.first()?.text?.plain
    }

    @Test
    fun `a defeat line during the fight shows Defeated`() {
        tab("Spawned")
        game.post(1, SystemMessage(ServerText(ServerSpan("The Endstone Protector has been defeated!"))))
        assertEquals("Stage: Defeated", stage())
    }

    @Test
    fun `a defeat line after the row has left Spawned does not stick the HUD on Defeated`() {
        tab("Resting (200)")
        game.post(1, SystemMessage(ServerText(ServerSpan("The Endstone Protector has been defeated!"))))
        assertEquals("Stage: Resting", stage())
    }
}
