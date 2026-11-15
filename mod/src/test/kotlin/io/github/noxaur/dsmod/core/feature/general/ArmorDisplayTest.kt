package io.github.noxaur.dsmod.core.feature.general

import io.github.noxaur.dsmod.core.config.DsModConfig
import io.github.noxaur.dsmod.core.feature.Game
import io.github.noxaur.dsmod.core.feature.features
import io.github.noxaur.dsmod.core.game.ContainerOpened
import io.github.noxaur.dsmod.core.game.GameEvent
import io.github.noxaur.dsmod.core.game.Item
import io.github.noxaur.dsmod.core.game.SlotChanged
import io.github.noxaur.dsmod.core.hud.ItemsRow
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ArmorDisplayTest {
    private val config = DsModConfig()
    private val game = Game(features(config))
    private var time = 0L

    private val helmet = Item("minecraft:player_head", 1)
    private val boots = Item("minecraft:leather_boots", 1)

    private fun post(vararg events: GameEvent) {
        for (event in events) game.post(++time, event)
        // A readout refreshes at least every 250 ms, which is how a changed setting shows with no event.
        time += 250
        game.tick(time)
    }

    private fun rows() = game.readouts.single { it.feature.id == "armor-display" }.hud?.rows?.map { (it as ItemsRow).items }

    private fun inventory(helmet: Item?, boots: Item?) =
        arrayOf<GameEvent>(ContainerOpened(0, null, 46), SlotChanged(5, helmet), SlotChanged(8, boots))

    @Test
    fun `the armor shows in a row, helmet first, with a gap for a piece not worn`() {
        post(*inventory(helmet, boots))
        assertEquals(listOf(listOf(helmet, null, null, boots)), rows())
    }

    @Test
    fun `the column layout gives each piece its own row`() {
        config.general.armorLayout = ArmorLayout.COLUMN
        post(*inventory(helmet, boots))
        assertEquals(listOf(listOf(helmet), listOf(null), listOf(null), listOf(boots)), rows())
    }

    @Test
    fun `only the picked pieces show, in the order picked`() {
        config.general.armorPieces = mutableListOf(ArmorPiece.BOOTS, ArmorPiece.HELMET)
        post(*inventory(helmet, boots))
        assertEquals(listOf(listOf(boots, helmet)), rows())

        config.general.armorPieces = mutableListOf(ArmorPiece.CHESTPLATE)
        post()
        assertNull(rows(), "A chestplate not worn is all there is to show")
    }

    @Test
    fun `another menu keeps the armor, and taking it all off hides the block`() {
        post(*inventory(helmet, boots))
        post(ContainerOpened(7, null, 54), SlotChanged(5, null), SlotChanged(8, null))
        assertEquals(listOf(listOf(helmet, null, null, boots)), rows())

        post(ContainerOpened(0, null, 46))
        assertNull(rows())
    }

    @Test
    fun `armor changed while the feature was off shows when it comes back`() {
        post(*inventory(helmet, null))
        config.general.armorDisplay = false
        post(SlotChanged(8, boots))
        config.general.armorDisplay = true
        post()
        assertEquals(listOf(listOf(helmet, null, null, boots)), rows())
    }
}
