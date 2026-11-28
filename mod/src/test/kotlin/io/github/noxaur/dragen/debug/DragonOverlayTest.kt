package io.github.noxaur.dragen.debug

import io.github.noxaur.dragen.api.ServerClock
import io.github.noxaur.dragen.api.DragonReplay
import io.github.noxaur.dragen.api.boss.dragon.DragonSystem
import io.github.noxaur.dragen.api.boss.dragon.Watch
import io.github.noxaur.dragen.core.config.ConfigFile
import io.github.noxaur.dragen.core.config.DragenConfig
import io.github.noxaur.dragen.core.config.SettingsTree
import io.github.noxaur.dragen.core.world.Cuboid
import io.github.noxaur.dragen.core.world.Segment
import io.github.noxaur.dragen.core.world.Waypoint
import io.github.noxaur.dragen.debug.dragon.DevOverlays
import io.github.noxaur.dragen.debug.dragon.DragonOverlay
import io.github.noxaur.dragen.debug.dragon.DragonProbe
import io.github.noxaur.dragen.debug.dragon.DragonView
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class DragonOverlayTest {
    private val fixture = Path.of("src/test/resources/api/dragon/protector-dragon.jsonl")

    @Test
    fun `the overlay shows the clock, cycle, dragon, attack, events and marks while on, and nothing watches once off`() {
        val system = DragonSystem(ServerClock())
        val probe = DragonProbe { system.api }
        val view = DragonView({ system.api }) { probe.recent }
        assertEquals(Watch.NOTHING, system.feed.watch, "Nothing watches before the overlay is on")
        probe.update(true)
        var text = ""
        var marks = emptyList<io.github.noxaur.dragen.core.world.Mark>()
        DragonReplay(system.feed) { tick ->
            if (tick == 440L) {
                text = view.block().rows.joinToString("\n") { it.text.plain }
                marks = view.marks()
            }
        }.play(fixture)
        for (expected in listOf("Clock: tick 440", "Cycle: Fighting", "Dragon #", "health:", "position:", "velocity:", "speed", "facing:", "movement:", "attack: Lightning", "next beat:", "ends:", "hazard: Lightning", "lands in", "Diagnostics:", "Events:")) {
            assertTrue(expected in text, "The overlay lacks `$expected`:\n$text")
        }
        assertTrue(marks.any { it is Waypoint }, "A dragon is marked: $marks")
        assertTrue(marks.any { it is Segment }, "Its velocity is drawn: $marks")
        assertTrue(marks.any { it is Cuboid }, "Its parts are boxed: $marks")
        assertTrue(probe.recent.size in 1..10, "The last ten events are kept, newest first: ${probe.recent}")
        assertTrue(probe.recent.first().startsWith("2051"), "The newest event is first: ${probe.recent}")

        probe.update(false)
        assertEquals(emptyList(), probe.recent)
        assertEquals(Watch.NOTHING, system.feed.watch, "A closed overlay leaves the API with nothing to watch")
    }

    @Test
    fun `an overlay that is on makes the API watch`() {
        val system = DragonSystem(ServerClock())
        val probe = DragonProbe { system.api }
        probe.update(true)
        DragonReplay(system.feed).play(fixture)
        assertNotEquals(Watch.NOTHING, system.feed.watch)
    }

    @Test
    fun `the Dev section is the last section, exists only when the debug build adds it, and saves under its own store`() {
        assertFalse(SettingsTree().sections.any { it.name == "Dev" }, "The release tree has no Dev section")
        assertFalse(SettingsTree().features.any { it.id == "dragon-api" })
        val config = DragenConfig()
        val tree = SettingsTree(config, dev = listOf(DevOverlays))
        assertEquals("Dev", tree.sections.last().name)
        assertEquals(listOf("dragon-api", "zone-picker"), tree.sections.last().groups.mapNotNull { it.switch?.id })
        assertEquals(SettingsTree().sections.map { it.name } + "Dev", tree.sections.map { it.name })
        tree.feature<DragonOverlay>().feature.enabled = true
        assertTrue("\"dev\"" in ConfigFile.encode(config), "The switch is saved in the dev store")
        assertFalse("\"dev\"" in ConfigFile.encode(DragenConfig().also { SettingsTree(it) }), "A release config never writes it")
        assertEquals(listOf("dragon-api"), SettingsTree.favorites(listOf("dragon-api"), listOf(DevOverlays)), "A starred Dev row survives a restart")
        assertEquals(emptyList(), SettingsTree.favorites(listOf("dragon-api")), "and a release build forgets it")
    }
}
