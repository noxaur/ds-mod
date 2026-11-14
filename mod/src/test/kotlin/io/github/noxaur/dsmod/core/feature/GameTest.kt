package io.github.noxaur.dsmod.core.feature

import io.github.noxaur.dsmod.core.config.DsModConfig
import io.github.noxaur.dsmod.core.game.ServerSpan
import io.github.noxaur.dsmod.core.game.ServerText
import io.github.noxaur.dsmod.core.game.SystemMessage
import io.github.noxaur.dsmod.core.game.TabList
import io.github.noxaur.dsmod.core.game.WorldChanged
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class GameTest {
    private val config = DsModConfig()
    private val game = Game(features(config))

    private fun say(time: Long, line: String) = game.post(time, SystemMessage(ServerText(ServerSpan(line))))

    private fun hud(id: String) = game.readouts.single { it.feature.id == id }.hud

    @Test
    fun `a feature that was switched off comes back knowing nothing of the world it left`() {
        game.post(0, TabList(listOf(ServerText(ServerSpan("Area: The End")))))
        say(0, "☠ The Young Dragon has spawned!")
        game.tick(0)
        assertNotNull(hud("dragon-timer"))

        config.end.dragonTimer = false
        game.tick(1_000)
        assertNull(hud("dragon-timer"))

        // The dragon died while the feature saw nothing, so the timer must not pick it up again.
        config.end.dragonTimer = true
        game.tick(2_000)
        assertNull(hud("dragon-timer"))
    }

    @Test
    fun `a feature that is switched back on hears the tab list again`() {
        game.post(0, TabList(listOf("Area: The End", "Protector: Rising (Tier 4/5, 20)").map { ServerText(ServerSpan(it)) }))
        game.tick(0)
        val before = assertNotNull(hud("golem-status"))

        config.end.golemStatus = false
        game.tick(1_000)
        assertNull(hud("golem-status"))

        // No row changed meanwhile, so no new tab list is coming.
        config.end.golemStatus = true
        game.tick(2_000)
        assertEquals(before, hud("golem-status"))
    }

    @Test
    fun `a feature locked to an area hears nothing before the server names one`() {
        say(0, "☠ The Young Dragon has spawned!")
        game.tick(0)
        assertNull(hud("dragon-timer"))
    }

    @Test
    fun `the area of the world the player left does not outlive the hop`() {
        game.post(0, TabList(listOf(ServerText(ServerSpan("Area: The End")))))
        game.post(1, WorldChanged)
        say(2, "☠ The Young Dragon has spawned!")
        game.tick(2)
        assertNull(hud("dragon-timer"))
    }
}
