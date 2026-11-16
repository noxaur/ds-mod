package io.github.noxaur.dsmod.core.feature.general

import io.github.noxaur.dsmod.core.config.DsModConfig
import io.github.noxaur.dsmod.core.feature.Game
import io.github.noxaur.dsmod.core.feature.features
import io.github.noxaur.dsmod.core.game.FloatingText
import io.github.noxaur.dsmod.core.game.ServerSpan
import io.github.noxaur.dsmod.core.game.ServerText
import io.github.noxaur.dsmod.core.game.SystemMessage
import io.github.noxaur.dsmod.core.game.TabList
import io.github.noxaur.dsmod.core.game.WorldChanged
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class DamageNumbersTest {
    private val config = DsModConfig().apply { general.damageNumbers = true }
    private val game = Game(features(config).filter { it is DamageNumbers })

    private val hit = ServerText(ServerSpan("74,540,375", GRAY))
    private val crit = ServerText(ServerSpan("✧1,", WHITE), ServerSpan("461", YELLOW), ServerSpan(",8", RED), ServerSpan("45✧", WHITE))

    /** What the player sees in place of [text], on a display [age] milliseconds old: null when it is hidden, else the text shown. */
    private fun seen(text: ServerText, age: Long = 0): String? = if (game.post(0, FloatingText(text, age))) (game.replaced?.plain ?: text.plain) else null

    private fun say(line: String) = game.post(0, SystemMessage(ServerText(ServerSpan(line))))

    private fun protector(stage: String) = game.post(0, TabList(listOf(ServerText(ServerSpan("Protector: $stage")))))

    @Test
    fun `a hit and a crit read short, the crit keeping its stars and colors`() {
        assertEquals("74.5M", seen(hit))
        assertEquals("✧1.5M✧", seen(crit))
        assertEquals(listOf(WHITE, WHITE, YELLOW, RED, WHITE, WHITE), game.replaced!!.spans.flatMap { span -> span.text.map { span.rgb } })
    }

    @Test
    fun `the decimals follow the setting`() {
        config.general.damageDecimals = 0f
        assertEquals("75M", seen(hit))
        config.general.damageDecimals = 3f
        assertEquals("74.54M", seen(hit))
    }

    @Test
    fun `a number under a thousand is left as the server sent it`() {
        val small = ServerText(ServerSpan("338", GRAY))
        assertEquals(true, game.post(0, FloatingText(small)))
        assertNull(game.replaced)
    }

    @Test
    fun `with short numbers off the server's number shows unchanged`() {
        config.general.damageShort = false
        assertEquals("74,540,375", seen(hit))
        assertNull(game.replaced)
    }

    @Test
    fun `crits only hides the hits and keeps the crits`() {
        config.general.damageShown = DamageShown.CRITS
        assertNull(seen(hit))
        assertEquals("✧1.5M✧", seen(crit))
    }

    @Test
    fun `none hides every damage number`() {
        config.general.damageShown = DamageShown.NONE
        assertNull(seen(hit))
        assertNull(seen(crit))
    }

    @Test
    fun `text with a letter or a slash is not a damage number`() {
        config.general.damageShown = DamageShown.NONE
        for (other in listOf("[Lv55] Zealot 13000/13000", "Opens in 5s", "2x Protector Dragon Fragment", "13000/13000", "Healing Fel 1M❤", "⏣ Void Sepulture\nStep on the pad")) {
            assertEquals(other, seen(ServerText(ServerSpan(other))), other)
        }
        assertEquals(emptyMap(), game.metrics.snapshot().counters)
    }

    @Test
    fun `a starred text that is not a number counts as unparsed`() {
        seen(ServerText(ServerSpan("✧Critical✧")))
        assertEquals(1L, game.metrics.snapshot().counters["damage-numbers.unparsed"])
    }

    @Test
    fun `a hit's edit holds until its number is two seconds old`() {
        assertEquals("74.5M", seen(hit))
        assertEquals(2_000L, game.heldUntil)
        config.general.damageShown = DamageShown.NONE
        assertNull(seen(hit))
        assertEquals(2_000L, game.heldUntil)
    }

    @Test
    fun `a number that stays up past two seconds is a hologram and keeps its own look`() {
        config.general.damageShown = DamageShown.NONE
        val hologram = ServerText(ServerSpan("1,250,000", WHITE))
        assertNull(seen(hologram))
        // The same display, still a number and now 2.5 seconds old, gets a new text: a hit's number never lives that long.
        assertEquals("1,250,000", seen(hologram, age = 2_500))
        assertNull(game.replaced)
        assertNull(game.heldUntil)
        assertEquals("1,300,000", seen(ServerText(ServerSpan("1,300,000", WHITE)), age = 60_000))
    }

    @Test
    fun `only during boss fights leaves the numbers alone outside a fight`() {
        config.general.damageInFights = true
        config.general.damageShown = DamageShown.CRITS
        assertEquals("74,540,375", seen(hit))
        assertEquals("✧1,461,845✧", seen(crit))
        assertNull(game.replaced)
    }

    @Test
    fun `only during boss fights changes the numbers from a slayer boss's spawn to its death`() {
        config.general.damageInFights = true
        say("☠ SLAYER BOSS SPAWNING!")
        assertEquals("74.5M", seen(hit))
        say("☠ NICE! SLAYER BOSS SLAIN!")
        assertEquals("74,540,375", seen(hit))
        say("☠ SLAYER BOSS SPAWNING!")
        say("  SLAYER QUEST FAILED")
        assertEquals("74,540,375", seen(hit))
    }

    @Test
    fun `only during boss fights follows a dragon until the egg returns`() {
        config.general.damageInFights = true
        say("☠ The Young Dragon has spawned!")
        assertEquals("74.5M", seen(hit))
        say("The Dragon Egg is respawning in 30 seconds.")
        assertEquals("74,540,375", seen(hit))
        say("☠ The Old Dragon has spawned!")
        say("☠ The Old Dragon has de-spawned.")
        assertEquals("74,540,375", seen(hit))
    }

    @Test
    fun `only during boss fights follows the Protector by its line and its tab row`() {
        config.general.damageInFights = true
        say("☠ The Endstone Protector has emerged from the ground! (Tier 5/5)")
        assertEquals("74.5M", seen(hit))
        say("The Endstone Protector has been defeated!")
        assertEquals("74,540,375", seen(hit))
        protector("Resting (200)")
        protector("Spawned")
        assertEquals("74.5M", seen(hit))
        protector("Resting (200)")
        assertEquals("74,540,375", seen(hit))
    }

    @Test
    fun `the Protector's defeat ends the fight while its tab row still reads Spawned`() {
        config.general.damageInFights = true
        say("☠ The Endstone Protector has emerged from the ground! (Tier 5/5)")
        protector("Spawned")
        say("The Endstone Protector has been defeated!")
        assertEquals("74,540,375", seen(hit))
        protector("Spawned")
        assertEquals("74,540,375", seen(hit))
        protector("Resting (200)")
        protector("Spawned")
        assertEquals("74.5M", seen(hit))
    }

    @Test
    fun `chat lines reach the fights with no other feature on`() {
        config.general.damageInFights = true
        assertEquals(true, game.wants(SystemMessage::class.java))
    }

    @Test
    fun `a new world ends every fight, and a player's chat cannot start one`() {
        config.general.damageInFights = true
        say("☠ SLAYER BOSS SPAWNING!")
        game.post(0, WorldChanged)
        assertEquals("74,540,375", seen(hit))
        say("Steve: ☠ SLAYER BOSS SPAWNING!")
        assertEquals("74,540,375", seen(hit))
    }

    private companion object {
        const val GRAY = 0xAAAAAA
        const val WHITE = 0xFFFFFF
        const val YELLOW = 0xFFFF55
        const val RED = 0xFF5555
    }
}
