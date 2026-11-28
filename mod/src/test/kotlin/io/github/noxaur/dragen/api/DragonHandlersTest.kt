package io.github.noxaur.dragen.api

import io.github.noxaur.dragen.api.ServerClock
import io.github.noxaur.dragen.api.boss.dragon.DragonEvent
import io.github.noxaur.dragen.api.boss.dragon.DragonSystem
import io.github.noxaur.dragen.api.boss.dragon.Watch
import io.github.noxaur.dragen.api.boss.dragon.on
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class DragonHandlersTest {
    private val fixture = Path.of("src/test/resources/api/dragon/protector-dragon.jsonl")

    private fun play(system: DragonSystem) = DragonReplay(system.feed).play(fixture)

    @Test
    fun `a handler hears events until it is closed, and then stops the API watching`() {
        val open = DragonSystem(ServerClock())
        var heard = 0
        open.api.on<DragonEvent.EyePlaced> { heard++ }
        open.api.on(DragonEvent.Spawned::class.java) { heard++ }.close()
        play(open)
        assertTrue(heard > 0, "An open handler hears the fixture's events")
        assertNotEquals(Watch.NOTHING, open.feed.watch, "An open handler keeps the API watching")

        val closed = DragonSystem(ServerClock())
        var missed = 0
        closed.api.on(DragonEvent.Spawned::class.java) { missed++ }.close()
        play(closed)
        assertEquals(0, missed, "A closed handler hears nothing")
        assertEquals(Watch.NOTHING, closed.feed.watch, "With every handler closed, nothing watches")
    }
}
