package io.github.noxaur.dsmod.core.feature.general

import io.github.noxaur.dsmod.core.config.DsModConfig
import io.github.noxaur.dsmod.core.feature.Game
import io.github.noxaur.dsmod.core.feature.features
import io.github.noxaur.dsmod.core.game.ServerSpan
import io.github.noxaur.dsmod.core.game.ServerText
import io.github.noxaur.dsmod.core.game.Sidebar
import io.github.noxaur.dsmod.core.text.Role
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ActivePetTest {
    private val config = DsModConfig()
    private val game = Game(features(config))
    private var time = 0L

    private fun sidebar(vararg lines: ServerText) {
        game.post(++time, Sidebar(ServerText(ServerSpan("DragSim")), listOf(ServerText(ServerSpan("Purse: 1,000")), *lines)))
        game.tick(time)
    }

    private fun pet(value: String, color: Int?) = ServerText(ServerSpan("Pet: ", 0xFFFFFF), ServerSpan("[Lvl 100] ", 0xAAAAAA), ServerSpan(value, color))

    private fun hud() = game.readouts.single { it.feature.id == "active-pet" }.hud

    @Test
    fun `the pet's name takes the color of its rarity, and its level is a number`() {
        sidebar(pet("Ender Dragon", Role.EPIC.rgb))
        val rows = hud()!!.rows.map { it.text }
        assertEquals(listOf("Pet: Ender Dragon", "Level: 100"), rows.map { it.plain })
        assertEquals(Role.EPIC.rgb, rows[0].spans.last().rgb)
    }

    @Test
    fun `an empty sidebar, as between worlds, keeps the pet`() {
        sidebar(pet("Ender Dragon", Role.EPIC.rgb))
        game.post(++time, Sidebar())
        game.tick(time)
        assertEquals("Ender Dragon", hud()?.rows?.first()?.text?.plain?.removePrefix("Pet: "))
    }

    @Test
    fun `no pet line, or Pet None, shows nothing`() {
        sidebar(pet("Ender Dragon", Role.EPIC.rgb))
        sidebar(ServerText(ServerSpan("Booster: Whale Booster")))
        assertNull(hud())
        sidebar(ServerText(ServerSpan("Pet: None")))
        assertNull(hud())
        assertEquals(null, game.metrics.snapshot().counters["active-pet.unparsed"])
    }

    @Test
    fun `a pet line in another shape is counted as unread`() {
        sidebar(ServerText(ServerSpan("Pet: Ender Dragon")))
        assertNull(hud())
        assertEquals(1L, game.metrics.snapshot().counters["active-pet.unparsed"])
    }
}
