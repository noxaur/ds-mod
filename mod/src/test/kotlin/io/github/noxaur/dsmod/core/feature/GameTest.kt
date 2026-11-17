package io.github.noxaur.dsmod.core.feature

import io.github.noxaur.dsmod.core.config.DsModConfig
import io.github.noxaur.dsmod.core.config.SettingsTree
import io.github.noxaur.dsmod.core.config.Store
import io.github.noxaur.dsmod.core.feature.end.DragonTimer
import io.github.noxaur.dsmod.core.feature.end.ProtectorStatus
import io.github.noxaur.dsmod.core.game.Area
import io.github.noxaur.dsmod.core.game.LeftServer
import io.github.noxaur.dsmod.core.game.ServerSpan
import io.github.noxaur.dsmod.core.game.ServerText
import io.github.noxaur.dsmod.core.game.Sidebar
import io.github.noxaur.dsmod.core.game.SystemMessage
import io.github.noxaur.dsmod.core.game.TabList
import io.github.noxaur.dsmod.core.game.WorldChanged
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class GameTest {
    private val config = DsModConfig()
    private val tree = SettingsTree(config)
    private val game = Game(tree.features)

    private fun say(time: Long, line: String) = game.post(time, SystemMessage(ServerText(ServerSpan(line))))

    private fun hud(id: String) = game.readouts.single { it.feature.id == id }.hud

    @Test
    fun `a feature that was switched off comes back knowing nothing of the world it left`() {
        game.post(0, TabList(listOf(ServerText(ServerSpan("Area: The End")))))
        say(0, "☠ The Young Dragon has spawned!")
        game.tick(0)
        assertNotNull(hud("dragon-timer"))

        tree.feature<DragonTimer>().enabled = false
        game.tick(1_000)
        assertNull(hud("dragon-timer"))

        // The dragon died while the feature saw nothing, so the timer must not pick it up again.
        tree.feature<DragonTimer>().enabled = true
        game.tick(2_000)
        assertNull(hud("dragon-timer"))
    }

    @Test
    fun `a feature that is switched back on hears the tab list again`() {
        game.post(0, TabList(listOf("Area: The End", "Protector: Rising (Tier 4/5, 20)").map { ServerText(ServerSpan(it)) }))
        game.tick(0)
        val before = assertNotNull(hud("protector-status"))

        tree.feature<ProtectorStatus>().enabled = false
        game.tick(1_000)
        assertNull(hud("protector-status"))

        // No row changed meanwhile, so no new tab list is coming.
        tree.feature<ProtectorStatus>().enabled = true
        game.tick(2_000)
        assertEquals(before, hud("protector-status"))
    }

    @Test
    fun `a feature locked to an area hears nothing before the server names one`() {
        say(0, "☠ The Young Dragon has spawned!")
        game.tick(0)
        assertNull(hud("dragon-timer"))
    }

    @Test
    fun `an edge feature locked to an area runs only there, and stays on for the HUD`() {
        val eyes = game.readouts.single { it.feature.id == "eye-guard" }.feature
        assertEquals(false to true, eyes.runs to eyes.on)
        game.post(0, TabList(listOf(ServerText(ServerSpan("Area: The End")))))
        assertEquals(true, eyes.runs)
        game.post(1, WorldChanged)
        assertEquals(false, eyes.runs)
    }

    @Test
    fun `a feature that is switched back on hears the sidebar again`() {
        var heard = 0
        val listener = object : Feature(config, "listener", Area.ANYWHERE, Store.END, "Hears the sidebar in a test.") {
            init {
                on<Sidebar> { _, _ -> heard++ }
            }
        }
        val game = Game(listOf(listener))
        game.post(0, Sidebar(lines = listOf(ServerText(ServerSpan("⏣ Village")))))
        listener.enabled = false
        game.tick(1_000)
        listener.enabled = true
        game.tick(2_000)
        assertEquals(2, heard)
    }

    @Test
    fun `a feature locked to an area hears the player leave the server from anywhere`() {
        var heard = 0
        val listener = object : Feature(config, "listener", Area.END, Store.END, "Hears the player leave in a test.") {
            init {
                on<LeftServer> { _, _ -> heard++ }
            }
        }
        Game(listOf(listener)).post(0, LeftServer)
        assertEquals(1, heard)
    }

    @Test
    fun `a feature switched on after the player left the server hears none of its sidebar`() {
        var heard = 0
        val listener = object : Feature(config, "listener", Area.ANYWHERE, Store.END, "Hears the sidebar in a test.") {
            init {
                on<Sidebar> { _, _ -> heard++ }
            }
        }
        val game = Game(listOf(listener))
        listener.enabled = false
        game.tick(0)
        game.post(1, Sidebar(lines = listOf(ServerText(ServerSpan("⏣ Village")))))
        game.post(2, LeftServer)
        listener.enabled = true
        game.tick(3)
        assertEquals(0, heard)
    }

    @Test
    fun `the area of the world the player left does not outlive the hop`() {
        game.post(0, TabList(listOf(ServerText(ServerSpan("Area: The End")))))
        game.post(1, WorldChanged)
        say(2, "☠ The Young Dragon has spawned!")
        game.tick(2)
        assertNull(hud("dragon-timer"))
    }

    @Test
    fun `off dragsim co no feature hears a line, hides it or shows a readout`() {
        var active = false
        val game = Game(SettingsTree(config).features) { active }
        val tab = TabList(listOf("Area: The End", "Protector: Rising (Tier 4/5, 20)").map { ServerText(ServerSpan(it)) })
        val refusal = SystemMessage(ServerText(ServerSpan("This ability is on cooldown for 4.35s")))
        game.post(0, tab)
        game.tick(0)
        assertEquals(false, game.wants(SystemMessage::class.java))
        assertEquals(true, game.post(0, refusal))
        assertEquals(emptyList(), game.readouts.mapNotNull { it.hud })

        // Turning the setting off mid-session brings the features back with the tab list they missed.
        active = true
        game.tick(1_000)
        assertNotNull(game.readouts.single { it.feature.id == "protector-status" }.hud)
        assertEquals(false, game.post(1_000, refusal))
    }

    @Test
    fun `a feature on by default tells the player once where its switch is`() {
        say(0, "This ability is on cooldown for 4.35s")
        say(1, "This ability is on cooldown for 4.30s")
        assertEquals(listOf("Hid a message from the server. Turn off Hide Cooldown Messages under Chat in /dsmod to see them."), game.messages.map { it.detail.plain })
        assertEquals(setOf("hide-cooldown-messages"), game.hidden.value.told)
    }

    @Test
    fun `a feature the player turned on hides without telling them`() {
        val hider = object : Feature(config, "hider", Area.ANYWHERE, Store.GENERAL, "Hides every line in a test.", default = false) {
            init {
                on<SystemMessage> { _, _ -> hide() }
            }
        }
        hider.enabled = true
        val game = Game(listOf(hider))
        assertEquals(false, game.post(0, SystemMessage(ServerText(ServerSpan("Anything")))))
        assertEquals(emptyList(), game.messages)
    }
}
