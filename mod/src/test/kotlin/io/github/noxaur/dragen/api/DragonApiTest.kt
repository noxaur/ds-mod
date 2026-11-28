package io.github.noxaur.dragen.api

import io.github.noxaur.dragen.api.ServerClock
import io.github.noxaur.dragen.api.boss.Hazard
import io.github.noxaur.dragen.api.boss.dragon.Cycle
import io.github.noxaur.dragen.api.boss.dragon.DragonEvent
import io.github.noxaur.dragen.api.boss.dragon.DragonSystem
import io.github.noxaur.dragen.api.boss.dragon.Movement
import io.github.noxaur.dragen.api.boss.dragon.on
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.DynamicTest.dynamicTest
import org.junit.jupiter.api.TestFactory
import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.nameWithoutExtension
import kotlin.io.path.readText
import kotlin.io.path.writeText
import kotlin.test.assertEquals
import kotlin.test.fail

class DragonApiTest {
    private fun <T> Known<T>.said(show: (T) -> String) = when (this) {
        is Known.Value -> show(value)
        Known.Unknown -> "unknown"
    }

    private fun Tick.said() = whole.toString()

    private fun Hazard.said() = when (this) {
        is Hazard.Strike -> "${kind.name} at ${lands.said { it.said() }}" + at.said { " (${it.x.toInt()}, ${it.y.toInt()}, ${it.z.toInt()})" }.removePrefix("unknown")
        is Hazard.Projectile -> "${kind.name} thrown at ${velocity.length.toString().take(4)} blocks a tick"
    }

    private fun said(event: DragonEvent): String = when (event) {
        is DragonEvent.EyePlaced -> "An eye was placed, ${event.eyes}/${event.needed}"
        is DragonEvent.EyeRemoved -> "An eye was taken back, ${event.eyes}/${event.needed}"
        is DragonEvent.Summoned -> "The eyes summoned a dragon, due at ${event.spawns.said { it.said() }}"
        is DragonEvent.Spawned -> "A ${event.type.said { it.name }} Dragon spawned"
        is DragonEvent.AttackStarted -> "It began ${event.attack.kind.name}, ending at ${event.attack.ends.said { it.said() }}"
        is DragonEvent.BeatReached -> "${event.attack.name} reached its ${event.beat.hazard.name} beat"
        is DragonEvent.AttackEnded -> "${event.attack.name} ended"
        is DragonEvent.HazardIncoming -> "Incoming: ${event.hazard.said()}"
        is DragonEvent.HazardLanded -> "Landed: ${event.hazard.said()}"
        is DragonEvent.DebuffApplied -> "Debuff ${event.debuff.kind.name}"
        is DragonEvent.DebuffEnded -> "Debuff ${event.debuff.name} ended"
        is DragonEvent.DiveStarted -> "It dove from (${event.from.x.toInt()}, ${event.from.y.toInt()}, ${event.from.z.toInt()})"
        is DragonEvent.DiveEnded -> "Its dive bottomed at (${event.bottom.x.toInt()}, ${event.bottom.y.toInt()}, ${event.bottom.z.toInt()})" + event.purge?.let { ", the ${it.name.lowercase()} purge" }.orEmpty()
        is DragonEvent.Despawned -> "The ${event.type.said { it.name }} Dragon died at (${event.deathSite.x.toInt()}, ${event.deathSite.y.toInt()}, ${event.deathSite.z.toInt()})"
        is DragonEvent.EggRespawning -> "The egg is rebuilding, due at ${event.egg.said { it.said() }}"
        is DragonEvent.EggSpawned -> "The egg is whole"
    }

    private fun said(cycle: Cycle): String = when (cycle) {
        Cycle.Unknown -> "Unknown"
        is Cycle.Summoning -> "Summoning, ${cycle.eyes}/${cycle.needed} eyes"
        is Cycle.Arriving -> "Arriving at ${cycle.spawns.said { it.said() }}"
        is Cycle.Fighting -> "Fighting " + cycle.dragons.joinToString { dragon ->
            val moving = when (val movement = dragon.movement) {
                Movement.Flying -> "flying"
                is Movement.Hovering -> "hovering"
                is Movement.Recovering -> "recovering"
                is Movement.Diving -> "diving"
            }
            "${dragon.type.said { it.name }}, $moving" + (dragon.attack?.let { ", casting ${it.kind.name}" } ?: "")
        }
        is Cycle.Respawning -> "Respawning, egg at ${cycle.egg.said { it.said() }}"
    }

    @TestFactory
    fun `the dragon API reads each fixture as its transcript says`(): List<DynamicTest> =
        Path.of("src/test/resources/api/dragon").listDirectoryEntries("*.jsonl").sorted().map { fixture ->
            dynamicTest(fixture.nameWithoutExtension) {
                val clock = ServerClock()
                val system = DragonSystem(clock)
                val api = system.api
                val text = StringBuilder()
                val events = ArrayList<DragonEvent>()
                api.on<DragonEvent> { }
                for (type in DragonEvent::class.sealedSubclasses) api.on(type.java) { events += it }
                var shown = ""
                DragonReplay(system.feed, clock) { tick ->
                    for (event in events) text.appendLine("%6d   %s".format(event.tick.whole, said(event)))
                    events.clear()
                    val now = said(api.cycle)
                    if (now != shown) text.appendLine("%6d %s".format(tick, now))
                    shown = now
                }.play(fixture)
                api.diagnostics.counts.toSortedMap().forEach { (name, count) -> text.appendLine("$name = $count") }
                val transcript = text.toString()
                val expected = fixture.resolveSibling(fixture.nameWithoutExtension + ".txt")
                if (!expected.exists()) {
                    expected.writeText(transcript)
                    fail("Wrote $expected. Read it as a player would, and run again if it is right.")
                }
                assertEquals(expected.readText(), transcript, "The dragon API no longer reads $fixture as $expected says. If the new reading is right, delete that file and run again.")
            }
        }
}
