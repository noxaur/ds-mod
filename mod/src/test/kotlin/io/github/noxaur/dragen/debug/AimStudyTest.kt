package io.github.noxaur.dragen.debug

import io.github.noxaur.dragen.api.ServerClock
import io.github.noxaur.dragen.api.DragonReplay
import io.github.noxaur.dragen.api.Vec
import io.github.noxaur.dragen.api.boss.dragon.DragonSystem
import io.github.noxaur.dragen.api.boss.dragon.Movement
import io.github.noxaur.dragen.core.world.Cuboid
import io.github.noxaur.dragen.core.world.Segment
import io.github.noxaur.dragen.debug.AimStudy.quantile
import io.github.noxaur.dragen.debug.dragon.Aim
import io.github.noxaur.dragen.debug.dragon.Arrow
import io.github.noxaur.dragen.debug.dragon.Basis
import io.github.noxaur.dragen.debug.dragon.DragonProbe
import io.github.noxaur.dragen.debug.dragon.DragonView
import io.github.noxaur.dragen.debug.dragon.ErrorTable
import io.github.noxaur.dragen.debug.dragon.Forecast
import io.github.noxaur.dragen.debug.dragon.Ramp
import io.github.noxaur.dragen.debug.dragon.Tuning
import org.junit.jupiter.api.Assumptions.assumeTrue
import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.io.path.writeText
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AimStudyTest {
    private val fixture = Path.of("src/test/resources/api/dragon/protector-dragon.jsonl")
    private val capture = Path.of("run/dragen/captures/2026-11-27T16-19-31.378Z.jsonl")
    private val frames by lazy { AimStudy.frames(fixture) }

    @Test
    fun `the error table is what the forecast gets wrong in the fixture`() {
        val samples = AimStudy.samples(frames, AimStudy.forecasts(frames, Tuning.TUNED), ErrorTable.AHEADS)
        val table = AimStudy.table(samples)
        assertEquals(table, ErrorTable.cells, "ErrorTable is stale. Put this in its `cells`:\n${AimStudy.literal(table)}")
    }

    @Test
    fun `an arrow flies as the closed form says and the solver's shot meets a moving target`() {
        val from = Vec(0.0, 10.0, 0.0)
        val velocity = Vec(1.2, 1.5, -2.4)
        var p = from
        var v = velocity
        for (n in 1..40) {
            p += v
            v = Vec(v.x * Arrow.DRAG, v.y * Arrow.DRAG - Arrow.GRAVITY, v.z * Arrow.DRAG)
            assertTrue((Arrow.position(from, velocity, n.toDouble()) - p).length < 1e-9, "Tick $n")
        }
        val target = { flight: Double -> Vec(30.0 - flight, 40.0 + flight * 0.2, -25.0 + flight * 0.8) }
        val shot = Aim.solve(from, target)
        assertTrue(shot.reachable)
        val arrived = Arrow.position(from, shot.direction * Arrow.SPEED, shot.flight)
        assertTrue((arrived - target(shot.flight)).length < 0.01, "The arrow arrives ${(arrived - target(shot.flight)).length} from the target")
        assertTrue(!Aim.solve(from) { Vec(500.0, 10.0, 0.0) }.reachable, "A target 500 blocks away is out of reach")
    }

    @Test
    fun `a hover is held until its attack ends and the dragon then follows the ramp`() {
        val held = frames.values.filter { it.movement is Movement.Hovering && it.endsIn != null && it.endsIn > 25 }
        assertTrue(held.size > 100)
        val forecasts = AimStudy.forecasts(frames, Tuning.TUNED)
        val worst = held.filter { frames.containsKey(it.tick + 20) }.maxOf { (forecasts.getValue(it.tick)[20] - frames.getValue(it.tick + 20).position).length }
        assertTrue(worst < 0.2, "A held hover is out by $worst blocks at 20 ticks")
        for (start in listOf(434L + 60, 794L + 60, 1155L + 160)) {
            val speeds = (0 until 9).map { (frames.getValue(start + it).position - frames.getValue(start + it - 1).position).length }
            speeds.forEachIndexed { i, s -> assertTrue(abs(s - Ramp.at(i)) < 0.01, "Tick $i of the ramp at $start is $s, not ${Ramp.at(i)}") }
        }
    }

    @Test
    fun `the forecast beats the API's while flying`() {
        val forecasts = AimStudy.forecasts(frames, Tuning.TUNED)
        val flying = frames.values.filter { it.mode == "flying" && frames.containsKey(it.tick + 10) }
        val api = quantile(flying.map { AimStudy.apiMiss(frames, it, 10) }, 0.5)
        val mine = quantile(flying.map { (forecasts.getValue(it.tick)[10] - frames.getValue(it.tick + 10).position).length }, 0.5)
        assertTrue(mine < api * 1.05, "The forecast's median error at 10 ticks is $mine, the API's $api")
        val recovering = frames.values.filter { it.mode == "recovering" && frames.containsKey(it.tick + 10) }
        val apiRecovering = recovering.map { AimStudy.apiMiss(frames, it, 10) }.average()
        val mineRecovering = recovering.map { (forecasts.getValue(it.tick)[10] - frames.getValue(it.tick + 10).position).length }.average()
        assertTrue(mineRecovering < apiRecovering, "While recovering the forecast is out by $mineRecovering at 10 ticks, the API by $apiRecovering")
    }

    @Test
    fun `the overlay draws the head ray and an aim for the body and the head once it knows the eye`() {
        val system = DragonSystem(ServerClock())
        val probe = DragonProbe { system.api }
        val view = DragonView({ system.api }, { Vec(-672.0, 11.0, -274.0) }) { probe.recent }
        probe.update(true)
        var marks = emptyList<io.github.noxaur.dragen.core.world.Mark>()
        var text = ""
        DragonReplay(system.feed) { tick ->
            view.observe()
            if (tick == 700L) {
                marks = view.marks()
                text = view.block().rows.joinToString("\n") { it.text.plain }
            }
        }.play(fixture)
        assertTrue(marks.count { it is Segment } > 12, "The head ray, the arcs and the aim lines are drawn: ${marks.size}")
        assertTrue(marks.count { it is Cuboid } >= 4, "Part boxes and the aim boxes are drawn")
        for (expected in listOf("aim body:", "aim head:", "flight", "miss", "mode", "conf")) assertTrue(expected in text, "The readout lacks `$expected`:\n$text")
    }

    @Test
    fun `the study report`() {
        val out = System.getenv("DRAGEN_STUDY_OUT") ?: return
        assumeTrue(capture.exists(), "No local capture")
        Path.of(out).writeText(Report(frames, fixture, capture).text())
    }
}

private class Report(private val fixtureFrames: Map<Long, Frame>, private val fixture: Path, private val capture: Path) {
    private val out = StringBuilder()
    private fun line(s: String = "") = out.appendLine(s)
    private fun f(v: Double) = if (v.isNaN()) "-" else "%.2f".format(v)
    private fun pct(n: Int, d: Int) = if (d == 0) "-" else "%.0f%% (%d/%d)".format(100.0 * n / d, n, d)

    fun text(): String {
        errors()
        grid()
        behaviour()
        arrows()
        return out.toString()
    }

    private fun errors() {
        val tuned = AimStudy.forecasts(fixtureFrames, Tuning.TUNED)
        val samples = AimStudy.samples(fixtureFrames, tuned, AimStudy.HORIZONS + ErrorTable.AHEADS)
        line("== error by mode and horizon: median / p90 blocks, API predict vs Forecast vs constant velocity (n)")
        for (mode in listOf("flying", "hovering", "recovering")) for (h in AimStudy.HORIZONS) {
            val at = fixtureFrames.values.filter { it.mode == mode && fixtureFrames.containsKey(it.tick + h) }
            val api = at.map { AimStudy.apiMiss(fixtureFrames, it, h) }
            val mine = samples.filter { it.frame.mode == mode && it.ahead == h }.map { it.error }
            val straight = at.map { frame -> (frame.position + (frame.position - (fixtureFrames[frame.tick - 1]?.position ?: frame.position)) * h.toDouble() - fixtureFrames.getValue(frame.tick + h).position).length }
            line("$mode +$h  n=${at.size}  api ${f(quantile(api, .5))}/${f(quantile(api, .9))}  forecast ${f(quantile(mine, .5))}/${f(quantile(mine, .9))}  straight ${f(quantile(straight, .5))}/${f(quantile(straight, .9))}")
        }
        line("== mean error at +10 while flying: api ${f(fixtureFrames.values.filter { it.mode == "flying" && fixtureFrames.containsKey(it.tick + 10) }.map { AimStudy.apiMiss(fixtureFrames, it, 10) }.average())}, forecast ${f(samples.filter { it.frame.mode == "flying" && it.ahead == 10 }.map { it.error }.average())}")
        line("== head ray: angle between the dragon's facing and where it is ten ticks on, flying (median, p90), against the velocity it had")
        val facing = fixtureFrames.values.filter { it.mode == "flying" && fixtureFrames.containsKey(it.tick + 10) && fixtureFrames.containsKey(it.tick - 1) }
        for ((name, direction) in listOf<Pair<String, (Frame) -> Vec>>("facing" to { it.facing }, "velocity" to { it.position - fixtureFrames.getValue(it.tick - 1).position })) {
            val angles = facing.map { AimStudy.angle(direction(it), fixtureFrames.getValue(it.tick + 10).position - it.position) }
            line("$name vs where the dragon is ten ticks on: median ${f(quantile(angles, .5))} p90 ${f(quantile(angles, .9))}")
        }
        line("facing vs this tick's step, median ${f(quantile(facing.map { AimStudy.angle(it.facing, it.position - fixtureFrames.getValue(it.tick - 1).position) }, .5))}")
        line("== flying, split by a dive (|dy| > 0.8 a tick) inside the horizon")
        for (h in listOf(10, 20)) for (dive in listOf(false, true)) {
            val g = samples.filter { it.frame.mode == "flying" && it.ahead == h && it.dive == dive }
            val api = g.map { AimStudy.apiMiss(fixtureFrames, it.frame, h) }
            line("+$h dive=$dive n=${g.size} api ${f(quantile(api, .5))} forecast ${f(quantile(g.map { it.error }, .5))}")
        }
        line("== ErrorTable (basis, ahead, n, median, body hit, head hit)")
        for ((basis, cells) in ErrorTable.cells) cells.forEach { line("$basis +${it.ahead} n=${it.samples} median=${it.median} body=${it.body} head=${it.head}") }
    }

    private fun grid() {
        line("== tuning grid: mean of median errors at +5,+10,+20 while flying")
        val rows = ArrayList<Pair<Double, Tuning>>()
        for (w in listOf(2, 3, 4, 6, 8)) for (tk in listOf(1.0, 0.98, 0.95, 0.9, 0.8)) for (ck in listOf(1.0, 0.95, 0.9, 0.8, 0.0)) {
            val t = Tuning(w, tk, ck)
            val s = AimStudy.samples(fixtureFrames, AimStudy.forecasts(fixtureFrames, t), intArrayOf(5, 10, 20)).filter { it.frame.mode == "flying" }
            rows += listOf(5, 10, 20).map { h -> quantile(s.filter { it.ahead == h }.map { it.error }, .5) }.average() to t
        }
        rows.sortedBy { it.first }.take(6).forEach { line("${f(it.first)}  ${it.second}") }
        line("API-equivalent three-point turn, window 2: ${f(rows.first { it.second == Tuning(2, 1.0, 1.0) }.first)}")
    }

    private fun behaviour() {
        val all = fixtureFrames.values.sortedBy { it.tick }
        line("== attacks and hover windows")
        var previous: Frame? = null
        for (frame in all) {
            if (frame.attackSince != null && frame.attackSince != previous?.attackSince) {
                val hover = all.first { it.tick >= frame.tick && it.movement is Movement.Hovering }
                val end = all.firstOrNull { it.tick > hover.tick && it.movement !is Movement.Hovering }
                line("${frame.attack} callout ${frame.attackSince} ends ${frame.attackEnds}; hovering from ${hover.tick} to ${end?.tick} at ${hover.position.x.toInt()},${hover.position.y.toInt()},${hover.position.z.toInt()}")
            }
            previous = frame
        }
        line("== speed by turn rate (deg a tick), flying")
        val bins = HashMap<Int, MutableList<Double>>()
        for (t in all.drop(2)) {
            val a = fixtureFrames[t.tick - 2]?.position ?: continue
            val b = fixtureFrames[t.tick - 1]?.position ?: continue
            val c = t.position
            if (AimStudy.flat(b, c) < 0.3 || AimStudy.flat(a, b) < 0.3) continue
            var d = Math.toDegrees(AimStudy.heading(b, c) - AimStudy.heading(a, b))
            while (d > 180) d -= 360
            while (d < -180) d += 360
            bins.getOrPut(minOf(abs(d).toInt(), 8)) { ArrayList() } += AimStudy.flat(b, c)
        }
        bins.toSortedMap().forEach { (k, v) -> line("turn>=$k n=${v.size} speed ${f(v.average())}") }
        val speeds = all.filter { it.movement == Movement.Flying }.mapNotNull { frame -> fixtureFrames[frame.tick - 1]?.let { (frame.position - it.position).length } }
        line("flying speed median ${f(quantile(speeds, .5))} p10 ${f(quantile(speeds, .1))} p90 ${f(quantile(speeds, .9))} max ${f(speeds.max())}")
    }

    private fun arrows() {
        val eyes = AimStudy.selfEyes(capture)
        val frames = AimStudy.frames(fixture, eyes)
        val launches = AimStudy.launches(capture)
        line("== arrows in the capture: ${launches.size}")
        val speeds = launches.map { it.velocity.length }
        line("launch speed back-extrapolated two ticks: median ${f(quantile(speeds, .5))} p10 ${f(quantile(speeds, .1))} max ${f(speeds.max())}; share within 0.03 of 3.0: ${pct(speeds.count { abs(it - 3.0) < 0.03 }, speeds.size)}")
        val live = launches.filter { frames.containsKey(it.tick) && frames.getValue(it.tick).eye != null }
        line("arrows launched while a dragon was tracked: ${live.size}")
        val real = live.map { l -> l to AimStudy.contact(frames, l.origin, l.velocity, l.tick, 70) }
        line("real arrows touching any dragon part: ${pct(real.count { it.second != null }, real.size)}; body ${pct(real.count { it.second?.part == "body" }, real.size)}; head ${pct(real.count { it.second?.part == "head" }, real.size)}")
        line("parts hit: " + real.mapNotNull { it.second?.part }.groupingBy { it }.eachCount())
        var solverHits = 0
        var solverBody = 0
        val gaps = ArrayList<Double>()
        val gapHit = ArrayList<Double>()
        val gapMiss = ArrayList<Double>()
        for ((l, contact) in real) {
            val aimed = frames.getValue(l.tick).aimed.firstOrNull { it.part == "body" } ?: continue
            val shot = AimStudy.contact(frames, frames.getValue(l.tick).eye!!, aimed.shot.direction * Arrow.SPEED, l.tick, 70)
            if (shot != null) solverHits++
            if (shot?.part == "body") solverBody++
            val gap = AimStudy.angle(l.velocity, aimed.shot.direction)
            gaps += gap
            (if (contact != null) gapHit else gapMiss) += gap
        }
        line("solver aimed at the body from the same eye and tick: touches any part ${pct(solverHits, real.size)}, body ${pct(solverBody, real.size)}")
        val intended = real.filter { (l, _) -> frames.getValue(l.tick).aimed.firstOrNull { it.part == "body" }?.let { AimStudy.angle(l.velocity, it.shot.direction) < 15 } == true }
        val intendedSolver = intended.count { (l, _) -> AimStudy.contact(frames, frames.getValue(l.tick).eye!!, frames.getValue(l.tick).aimed.first { it.part == "body" }.shot.direction * Arrow.SPEED, l.tick, 70) != null }
        line("arrows within 15 degrees of the solver's aim (the player was shooting at the dragon): ${intended.size}; real touched ${pct(intended.count { it.second != null }, intended.size)}, solver would have touched ${pct(intendedSolver, intended.size)}")
        line("angle between the real arrow and the solver's aim, degrees: median ${f(quantile(gaps, .5))} p90 ${f(quantile(gaps, .9))}; real hits ${f(quantile(gapHit, .5))}, real misses ${f(quantile(gapMiss, .5))}")

        line("== solver counterfactual over every tick with an eye: arrow along the solver's aim, any dragon part touched")
        class Row(val basis: Basis, val flight: Double, val conf: Double?, val any: Boolean, val first: Boolean, val target: Boolean, val partName: String)
        val rows = ArrayList<Row>()
        for (frame in frames.values) for (aimed in frame.aimed) {
            if (!aimed.shot.reachable) continue
            fun touch(only: String?) = AimStudy.contact(frames, frame.eye!!, aimed.shot.direction * Arrow.SPEED, frame.tick, (aimed.shot.flight + 4).toInt(), only)
            val hit = touch(null)
            val only = touch(aimed.part)
            rows += Row(aimed.basis, aimed.shot.flight, aimed.confidence, hit != null, hit?.part == aimed.part, only != null, aimed.part)
        }
        for (part in listOf("body", "head")) {
            line("aim at $part: n=${rows.count { it.partName == part }}")
            for (basis in Basis.entries) for ((lo, hi) in listOf(0.0 to 10.0, 10.0 to 20.0, 20.0 to 40.0, 40.0 to 99.0)) {
                val g = rows.filter { it.partName == part && it.basis == basis && it.flight >= lo && it.flight < hi }
                if (g.size < 5) continue
                line("  $basis flight $lo-$hi n=${g.size} first thing touched ${pct(g.count { it.any }, g.size)}, that part first ${pct(g.count { it.first }, g.size)}, that part at all ${pct(g.count { it.target }, g.size)} mean conf ${f(g.mapNotNull { it.conf }.average())}")
            }
            line("  calibration, confidence bucket vs that part touched:")
            for ((lo, hi) in listOf(0.0 to .3, .3 to .6, .6 to .85, .85 to 1.01)) {
                val g = rows.filter { it.partName == part && it.conf != null && it.conf >= lo && it.conf < hi }
                line("    conf $lo-$hi n=${g.size} that part at all ${pct(g.count { it.target }, g.size)}, first ${pct(g.count { it.first }, g.size)}")
            }
        }
        line("== distance from the eye to the dragon, median ${f(quantile(frames.values.filter { it.eye != null }.map { (it.position - it.eye!!).length }, .5))}")
        line("== dives: ticks with |dy| > 0.8")
        val all = frames.values.sortedBy { it.tick }
        var from = -1L
        var last = -1L
        for (frame in all) {
            val dy = frames[frame.tick - 1]?.let { abs(frame.position.y - it.position.y) } ?: 0.0
            if (dy > 0.8) {
                if (from < 0) from = frame.tick
                last = frame.tick
            } else if (from >= 0 && frame.tick > last + 3) {
                val low = all.filter { it.tick in from..last }.minBy { it.position.y }
                val eye = low.eye
                line("dive $from..$last lowest y ${f(low.position.y)} at ${low.tick}, ${eye?.let { "${f(AimStudy.flat(low.position, it))} blocks from the player across, ${f(low.position.y - it.y)} above the eye" }}")
                from = -1
            }
        }
    }
}
