package io.github.noxaur.dragen.api.boss.dragon

import io.github.noxaur.dragen.api.Known
import io.github.noxaur.dragen.api.Tick
import io.github.noxaur.dragen.api.Ticks
import io.github.noxaur.dragen.api.Vec
import io.github.noxaur.dragen.api.boss.Hazard
import io.github.noxaur.dragen.api.boss.HazardKind
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

internal enum class HealthSource { SIDEBAR, TAB, NAMETAG, BAR }

private fun facing(yaw: Double, pitch: Double): Vec {
    val y = Math.toRadians(yaw)
    val p = Math.toRadians(pitch)
    return Vec(sin(y) * cos(p), -sin(p), -cos(y) * cos(p))
}

/** @keep The client never learns a dragon's velocity, which stays at what the last velocity packet set, so velocity is
 * the change in position over the last tick. Its yaw faces away from where it flies, so its facing is turned half a turn. */
internal class Tracked(override val id: DragonId, val entity: Int, val traits: List<Trait>, val born: Tick) : Dragon {
    override var type: Known<DragonType> = Known.Unknown
    override var health: Health = Health(0, 0)
    override var body: Body = Body(Vec.ZERO, Vec.ZERO, Vec.ZERO, emptyList())
    override var movement: Movement = Movement.Flying
    override var attack: Attack? = null
    val hazardList: MutableList<Hazard> = ArrayList()
    val debuffList: MutableList<Debuff> = ArrayList()
    override val hazards: List<Hazard> get() = hazardList.toList()
    override val debuffs: List<Debuff> get() = debuffList.toList()

    var seen: Tick = born
    var missingSince: Tick? = null
    var healthAt: Tick? = null
    var killed = false
    var bolt: Pair<Tick, HazardKind>? = null
    val readings = HashMap<HealthSource, Pair<Long, Tick>>()
    var dove: Vec? = null
    private var falling = 0
    private var hoverEnded: Tick? = null
    private val path = ArrayDeque<Vec>()

    fun moved(now: Tick, position: Vec, yaw: Double, pitch: Double, parts: List<Part>) {
        val velocity = path.lastOrNull()?.let { position - it } ?: Vec.ZERO
        path.addLast(position)
        if (path.size > PATH) path.removeFirst()
        body = Body(position, velocity, facing(yaw, pitch), parts)
        seen = now
        missingSince = null
        val speed = velocity.length
        val current = movement
        falling = if (velocity.y <= DIVE_SPEED) falling + 1 else 0
        movement = when {
            path.size < 2 -> current
            current is Movement.Diving && velocity.y < DIVE_BOTTOM -> current
            falling >= DIVE_TICKS -> if (current is Movement.Diving) current else Movement.Diving(now)
            speed < HOVER_SPEED -> if (current is Movement.Hovering) current else Movement.Hovering(now)
            current is Movement.Hovering -> Movement.Recovering(now).also { hoverEnded = now }
            current is Movement.Recovering && speed < CRUISE_SPEED && now - (hoverEnded ?: now) < RECOVERY -> current
            else -> Movement.Flying
        }
    }

    override fun predict(ahead: Ticks): Vec {
        val last = path.lastOrNull() ?: return body.position
        if (movement is Movement.Hovering || path.size < PATH) return last + body.velocity * ahead.count
        val (a, b, c) = path.toList().takeLast(PATH)
        val before = atan2(b.z - a.z, b.x - a.x)
        val after = atan2(c.z - b.z, c.x - b.x)
        var turn = after - before
        while (turn > Math.PI) turn -= 2 * Math.PI
        while (turn < -Math.PI) turn += 2 * Math.PI
        val speed = hypot(c.x - b.x, c.z - b.z)
        val climb = c.y - b.y
        var x = c.x
        var z = c.z
        var heading = after
        var left = ahead.count
        while (left > 0) {
            val step = minOf(1.0, left)
            heading += turn * step
            x += speed * step * cos(heading)
            z += speed * step * sin(heading)
            left -= step
        }
        return Vec(x, c.y + climb * ahead.count, z)
    }

    private companion object {
        const val PATH = 3

        /** @keep In both captures the dragon's speed drops to 0 on the tick of a callout, cruises at about 1.05 blocks a tick,
         * and takes about 30 ticks to reach it again. */
        const val HOVER_SPEED = 0.02
        const val CRUISE_SPEED = 0.95

        /** @keep Across 28 fights the dragon dove 14 times, falling at least 20 blocks to about the player's height at 1.2 to
         * 2.6 blocks a tick, and climbed back at about 1.9. A turn never drops it this fast for three ticks. */
        const val DIVE_SPEED = -0.8
        const val DIVE_TICKS = 3
        const val DIVE_BOTTOM = -0.1
        val RECOVERY = Ticks(40.0)
    }
}
