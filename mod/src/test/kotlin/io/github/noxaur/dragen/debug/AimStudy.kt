package io.github.noxaur.dragen.debug

import io.github.noxaur.dragen.api.ServerClock
import io.github.noxaur.dragen.api.DragonReplay
import io.github.noxaur.dragen.api.Known
import io.github.noxaur.dragen.api.Tick
import io.github.noxaur.dragen.api.Ticks
import io.github.noxaur.dragen.api.Vec
import io.github.noxaur.dragen.api.boss.dragon.Cycle
import io.github.noxaur.dragen.api.boss.dragon.DragonSystem
import io.github.noxaur.dragen.api.boss.dragon.Movement
import io.github.noxaur.dragen.api.boss.dragon.Part
import io.github.noxaur.dragen.debug.dragon.Aimed
import io.github.noxaur.dragen.debug.dragon.AimTracker
import io.github.noxaur.dragen.debug.dragon.Arrow
import io.github.noxaur.dragen.debug.dragon.Basis
import io.github.noxaur.dragen.debug.dragon.Cell
import io.github.noxaur.dragen.debug.dragon.ErrorTable
import io.github.noxaur.dragen.debug.dragon.Forecast
import io.github.noxaur.dragen.debug.dragon.Tuning
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.double
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.nio.file.Path
import kotlin.io.path.useLines
import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.atan2
import kotlin.math.hypot
import kotlin.math.roundToInt

class Frame(
    val tick: Long,
    val position: Vec,
    val parts: List<Part>,
    val movement: Movement,
    val endsIn: Int?,
    val since: Int,
    val api: Map<Int, Vec>,
    val eye: Vec?,
    val aimed: List<Aimed>,
    val facing: Vec = Vec.ZERO,
    val attack: String? = null,
    val attackSince: Long? = null,
    val attackEnds: Long? = null,
) {
    val mode: String get() = when (movement) {
        Movement.Flying -> "flying"
        is Movement.Hovering -> "hovering"
        is Movement.Recovering -> "recovering"
        is Movement.Diving -> "diving"
    }

    fun part(name: String) = parts.firstOrNull { it.name == name }
}

class Launch(val id: String, val tick: Long, val origin: Vec, val velocity: Vec, val track: List<Pair<Long, Vec>>)

/** @keep The measurement behind `AimStudyTest`: it replays a dragon capture through the real API, the real `Forecast` and the real `AimTracker`, then scores them against what the dragon did next. */
object AimStudy {
    val HORIZONS = intArrayOf(5, 10, 20, 30)

    /** @keep With no slack the body's confidence ran 0.15 under the share of solver shots that touched it, and with two ticks either side each confidence bucket matched its touch rate within 10 points. */
    const val SLACK = 2

    fun quantile(values: List<Double>, q: Double): Double = if (values.isEmpty()) Double.NaN else values.sorted().let { it[((it.size - 1) * q).roundToInt()] }

    fun frames(fixture: Path, eyes: Map<Long, Vec> = emptyMap()): Map<Long, Frame> {
        val system = DragonSystem(ServerClock())
        val tracker = AimTracker()
        val frames = LinkedHashMap<Long, Frame>()
        system.api.watch()
        DragonReplay(system.feed) { tick ->
            val dragon = (system.api.cycle as? Cycle.Fighting)?.dragons?.firstOrNull() ?: return@DragonReplay
            val now = Tick.of(tick)
            tracker.observe(listOf(dragon), now, eyes[tick])
            val ends = ((dragon.attack?.ends as? Known.Value)?.value)
            val since = (dragon.movement as? Movement.Recovering)?.let { (now - it.since).count.toInt() } ?: 0
            frames[tick] = Frame(
                tick, dragon.body.position, dragon.body.parts, dragon.movement, Forecast.endsIn(now, ends), since,
                (1..60).associateWith { dragon.predict(Ticks(it.toDouble())) }, eyes[tick], tracker.solutions[dragon.id.value].orEmpty(),
                dragon.body.facing, dragon.attack?.kind?.name, dragon.attack?.since?.whole, ends?.whole,
            )
        }.play(fixture)
        return frames
    }

    fun forecasts(frames: Map<Long, Frame>, tuning: Tuning): Map<Long, List<Vec>> {
        val forecast = Forecast(tuning)
        return frames.values.sortedBy { it.tick }.associate { f ->
            forecast.observe(f.position)
            f.tick to forecast.positions(f.movement, f.endsIn, f.since, 60)
        }
    }

    class Sample(val frame: Frame, val ahead: Int, val basis: Basis, val error: Double, val body: Boolean, val head: Boolean, val dive: Boolean)

    private fun dives(frames: Map<Long, Frame>, from: Long, to: Long) = (from..to).any { t ->
        val a = frames[t - 1]?.position
        val b = frames[t]?.position
        a != null && b != null && abs(b.y - a.y) > 0.8
    }

    fun samples(frames: Map<Long, Frame>, forecasts: Map<Long, List<Vec>>, aheads: IntArray): List<Sample> = buildList {
        for (frame in frames.values) {
            val path = forecasts.getValue(frame.tick)
            for (ahead in aheads) {
                val truth = frames[frame.tick + ahead] ?: continue
                val predicted = path[ahead]
                fun hit(name: String): Boolean {
                    val now = frame.part(name) ?: return false
                    val centre = now.box.center + (predicted - frame.position)
                    return (-SLACK..SLACK).any { s -> frames[frame.tick + ahead + s]?.part(name)?.let { Arrow.reaches(it.box, centre) } == true }
                }
                add(Sample(frame, ahead, Forecast.basis(frame.movement, frame.endsIn, ahead), (predicted - truth.position).length, hit("body"), hit("head"), dives(frames, frame.tick, frame.tick + ahead)))
            }
        }
    }

    fun table(samples: List<Sample>): Map<Basis, List<Cell>> = Basis.entries.associateWith { basis ->
        ErrorTable.AHEADS.toList().mapNotNull { ahead ->
            val group = samples.filter { it.basis == basis && it.ahead == ahead }
            if (group.isEmpty()) null else Cell(ahead, group.size, round2(quantile(group.map { it.error }, 0.5)), round2((group.count { it.body } + 1.0) / (group.size + 2)), round2((group.count { it.head } + 1.0) / (group.size + 2)))
        }
    }

    private fun round2(v: Double) = (v * 100).roundToInt() / 100.0

    fun literal(table: Map<Basis, List<Cell>>) = table.entries.joinToString("\n") { (basis, cells) ->
        "        Basis.$basis to listOf(" + cells.joinToString(", ") { "Cell(${it.ahead}, ${it.samples}, ${it.median}, ${it.body}, ${it.head})" } + "),"
    }

    fun apiMiss(frames: Map<Long, Frame>, frame: Frame, ahead: Int) = (frame.api.getValue(ahead) - frames.getValue(frame.tick + ahead).position).length

    fun selfEyes(capture: Path): Map<Long, Vec> {
        val raw = HashMap<Long, Vec>()
        forEachMotion(capture) { tick, entries ->
            for (e in entries.values) if (e is JsonObject && e.s("entity") == "self") raw[tick] = Vec(e.d("x"), e.d("y") + e.d("eye"), e.d("z"))
        }
        val out = HashMap<Long, Vec>()
        var last: Vec? = null
        for (t in 0..(raw.keys.maxOrNull() ?: 0)) {
            last = raw[t] ?: last
            last?.let { out[t] = it }
        }
        return out
    }

    fun launches(capture: Path): List<Launch> {
        val tracks = LinkedHashMap<String, MutableList<Pair<Long, Vec>>>()
        val first = HashMap<String, JsonObject>()
        forEachMotion(capture) { tick, entries ->
            for ((id, e) in entries) if (e is JsonObject && e.s("entity") == "minecraft:arrow") {
                tracks.getOrPut(id) { ArrayList() } += tick to Vec(e.d("x"), e.d("y"), e.d("z"))
                first.putIfAbsent(id, e)
            }
        }
        return tracks.mapNotNull { (id, track) ->
            val e = first.getValue(id)
            var p = Vec(e.d("x"), e.d("y"), e.d("z"))
            var v = Vec(e.d("vx"), e.d("vy"), e.d("vz"))
            repeat(2) {
                v = Vec(v.x / Arrow.DRAG, (v.y + Arrow.GRAVITY) / Arrow.DRAG, v.z / Arrow.DRAG)
                p -= v
            }
            Launch(id, track.first().first - 1, p, v, track)
        }
    }

    private fun forEachMotion(capture: Path, visit: (Long, JsonObject) -> Unit) = capture.useLines { lines ->
        for (line in lines) {
            if (!line.contains("\"type\":\"motion\"")) continue
            val json = Json.parseToJsonElement(line).jsonObject
            visit(json.getValue("tick").jsonPrimitive.content.toLong(), json.getValue("data").jsonObject)
        }
    }

    private fun JsonObject.s(key: String) = (this[key] as? kotlinx.serialization.json.JsonPrimitive)?.takeUnless { it is JsonNull }?.content

    private fun JsonObject.d(key: String) = getValue(key).jsonPrimitive.double

    class Contact(val part: String, val step: Int)

    fun contact(frames: Map<Long, Frame>, origin: Vec, velocity: Vec, launch: Long, steps: Int, only: String? = null): Contact? {
        var before = origin
        for (n in 1..steps) {
            val at = Arrow.position(origin, velocity, n.toDouble())
            val frame = frames[launch + n - 1]
            if (frame != null) {
                for (sub in 1..4) {
                    val point = before + (at - before) * (sub / 4.0)
                    frame.parts.firstOrNull { (only == null || it.name == only) && Arrow.reaches(it.box, point) }?.let { return Contact(it.name, n) }
                }
            }
            before = at
        }
        return null
    }

    fun angle(a: Vec, b: Vec) = Math.toDegrees(acos(((a.x * b.x + a.y * b.y + a.z * b.z) / (a.length * b.length)).coerceIn(-1.0, 1.0)))

    fun heading(a: Vec, b: Vec) = atan2(b.z - a.z, b.x - a.x)

    fun flat(a: Vec, b: Vec) = hypot(a.x - b.x, a.z - b.z)
}
