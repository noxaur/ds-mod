package io.github.noxaur.dragen.core.feature.hud.performancestats

import io.github.noxaur.dragen.harness.Harness
import io.github.noxaur.dragen.core.game.PingMeasured
import io.github.noxaur.dragen.core.game.ServerTime
import io.github.noxaur.dragen.core.game.WorldChanged
import io.github.noxaur.dragen.core.text.Role
import io.github.noxaur.dragen.core.text.Text
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class PerformanceStatsTest : Harness(alone = PerformanceStats::class) {
    private val feature = tree.feature<PerformanceStats>().also { it.feature.enabled = true }

    private fun styled(): List<Text> = hud()?.rows?.map { it.text }.orEmpty()

    private fun shown() = styled().joinToString(" / ") { it.plain }

    private fun role(label: String): Role {
        val spans = styled().single().spans
        val value = spans[spans.indexOfFirst { it.text.trim() == "$label:" } + 1]
        return Role.entries.first { it.rgb == value.rgb }
    }

    private fun clock(seconds: Int, ticks: Long, from: Long = 0) {
        for (i in 0..seconds) {
            if (i > 0) time += 1_000
            post(ServerTime(from + i * ticks))
        }
    }

    @Test
    fun `ping is the mean of the last five answers, and warns past 150 and 300 ms`() {
        feature.values.value = listOf(Stat.PING)
        post(PingMeasured(1_000))
        listOf(40L, 50, 60, 70, 80).forEach { post(PingMeasured(it)) }
        assertEquals("Ping: 60 ms", shown())
        assertEquals(Role.VALUE, role("Ping"))
        listOf(200L, 200, 200, 200, 200).forEach { post(PingMeasured(it)) }
        assertEquals(Role.WARNING, role("Ping"))
        listOf(1_200L, 1_200, 1_200, 1_200, 1_200).forEach { post(PingMeasured(it)) }
        assertEquals("Ping: 1,200 ms", shown())
        assertEquals(Role.BAD, role("Ping"))
    }

    @Test
    fun `a slow server warns below 18 and is bad below 12`() {
        feature.values.value = listOf(Stat.TPS)
        clock(5, 20)
        assertEquals("TPS: 20.0", shown())
        assertEquals(Role.VALUE, role("TPS"))
        post(WorldChanged)
        clock(5, 16)
        assertEquals("TPS: 16.0", shown())
        assertEquals(Role.WARNING, role("TPS"))
        post(WorldChanged)
        clock(5, 8)
        assertEquals(Role.BAD, role("TPS"))
    }

    @Test
    fun `a server that stops sending its clock falls, and catching up never shows more than 20`() {
        feature.values.value = listOf(Stat.TPS)
        clock(4, 20)
        pass(2_000)
        assertEquals("TPS: 20.0", shown())
        pass(2_000)
        assertEquals("TPS: 10.0", shown())
        post(ServerTime(200))
        assertEquals("TPS: 20.0", shown())
    }

    @Test
    fun `the window keeps the last ten seconds`() {
        feature.values.value = listOf(Stat.TPS)
        clock(10, 10)
        clock(10, 20, from = 100)
        assertEquals("TPS: 20.0", shown())
    }

    @Test
    fun `a clock that goes back starts the count again`() {
        feature.values.value = listOf(Stat.TPS)
        clock(3, 20, from = 50_000)
        post(ServerTime(10))
        assertEquals("TPS: -", shown())
    }

    @Test
    fun `nothing shows until there is something to show, and no values means no block`() {
        pass(0)
        assertEquals("FPS: -   Ping: -   TPS: -", shown())
        feature.values.value = listOf(Stat.TPS, Stat.FPS)
        feature.layout.value = StatsLayout.COLUMN
        pass(1_000)
        assertEquals("TPS: - / FPS: -", shown())
        feature.values.value = listOf()
        pass(1_000)
        assertNull(hud())
    }
}
