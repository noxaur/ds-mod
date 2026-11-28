package io.github.noxaur.dragen.api.boss.dragon

import io.github.noxaur.dragen.api.world.BlockArea
import io.github.noxaur.dragen.api.Progress
import kotlin.math.abs

private fun key(x: Int, y: Int, z: Int): Long = (x.toLong() and 0x3FFFFFF shl 38) or (z.toLong() and 0x3FFFFFF shl 12) or (y.toLong() and 0xFFF)

private fun inside(area: BlockArea, key: Long): Boolean {
    val x = ((key shr 38).toInt() shl 6) shr 6
    val z = (((key shr 12) and 0x3FFFFFF).toInt() shl 6) shr 6
    val y = ((key and 0xFFF).toInt() shl 20) shr 20
    return area.contains(x, y, z)
}

private fun air(state: String) = state.startsWith("minecraft:air") || state.startsWith("minecraft:cave_air") || state.startsWith("minecraft:void_air")

/** @keep The altar is a ring of eight end portal frames at one height within a 5 by 5 square. The End holds other frames,
 * such as 13 at several heights some 75 blocks east, which are no altar. Above its centre a column of 38 glass blocks climbs
 * from the frames' level to the egg, a build of 2,002 blocks some 38 to 67 blocks over them. Across 28 summons every frame
 * emptied within two ticks, the column drained from the bottom one block every 2 ticks from 95 ticks later, and the dragon
 * spawned on the tick its last block drained, 168 to 170 ticks after the summon, as the whole egg vanished. After a dragon
 * dies the column rebuilds from 133 ticks later, one block a tick, then the egg, whose last block lands on the tick the
 * server says the egg spawned, 198 or 199 ticks after the dragon de-spawned. The server places a beacon on a pad below
 * where it died. */
internal class Altar {
    private class Frame(val x: Int, val y: Int, val z: Int, var eye: Boolean)

    private val frames = HashMap<Long, Frame>()
    private var ring = emptyList<Frame>()
    private var frameY = 0
    private var centerX = 0
    private var centerZ = 0

    var area: BlockArea? = null
        private set

    private val tower = HashSet<Long>()
    private val egg = HashSet<Long>()
    private var towerFull = TOWER_BLOCKS
    private var eggFull = EGG_BLOCKS

    var beacon: Triple<Int, Int, Int>? = null
        private set

    private var cleared = 0
    private var clearedBefore = 0

    var placed = 0
        private set

    var removed = 0
        private set

    val known: Boolean get() = area != null

    val eyes: Int get() = ring.count { it.eye }

    val needed: Int get() = ring.size

    val towerCount: Int get() = tower.size

    val eggComplete: Boolean get() = egg.size >= eggFull

    val towerStanding: Boolean get() = tower.size >= towerFull

    val drained: Progress get() = Progress((1 - tower.size.toDouble() / towerFull).coerceIn(0.0, 1.0))

    val rebuilt: Progress get() = Progress(((tower.size + egg.size).toDouble() / (towerFull + eggFull)).coerceIn(0.0, 1.0))

    fun settle(): Boolean {
        val summoned = cleared > 0 && cleared + clearedBefore >= SUMMON_CLEARS && eyes == 0
        clearedBefore = if (summoned) 0 else cleared
        cleared = 0
        placed = 0
        removed = 0
        return summoned
    }

    fun forget(gone: BlockArea) {
        frames.values.removeIf { gone.contains(it.x, it.y, it.z) }
        if (ring.any { gone.contains(it.x, it.y, it.z) }) {
            ring = emptyList()
            area = null
            tower.clear()
            egg.clear()
        }
        tower.removeIf { inside(gone, it) }
        egg.removeIf { inside(gone, it) }
        beacon?.let { (x, y, z) -> if (gone.contains(x, y, z)) beacon = null }
    }

    fun eggSpawned() {
        if (known) eggFull = egg.size
    }

    fun block(x: Int, y: Int, z: Int, state: String) {
        if (state.startsWith(FRAME)) frame(x, y, z, "eye=true" in state)
        if (state.startsWith(BEACON) && area?.contains(x, y, z) != true) beacon = Triple(x, y, z)
        else if (beacon == Triple(x, y, z)) beacon = null
        val area = area ?: return
        if (!area.contains(x, y, z) || state.startsWith(FRAME)) return
        val at = key(x, y, z)
        val column = x == centerX && z == centerZ && y < frameY + EGG_ABOVE
        val set = when {
            column -> tower
            y >= frameY + EGG_ABOVE -> egg
            else -> return
        }
        if (air(state)) set.remove(at) else set.add(at)
        if (tower.size > towerFull) towerFull = tower.size
        if (egg.size > eggFull) eggFull = egg.size
    }

    private fun frame(x: Int, y: Int, z: Int, eye: Boolean) {
        val known = frames[key(x, y, z)]
        if (known == null) {
            frames[key(x, y, z)] = Frame(x, y, z, eye)
            if (area == null) find(y)
            return
        }
        val was = known.eye
        known.eye = eye
        if (known !in ring) return
        if (was && !eye) {
            cleared++
            removed++
        }
        if (!was && eye) placed++
    }

    private fun find(y: Int) {
        val level = frames.values.filter { it.y == y }
        for (minX in level.map { it.x }.distinct()) for (minZ in level.map { it.z }.distinct()) {
            val group = level.filter { it.x - minX in 0..SPAN && it.z - minZ in 0..SPAN }
            if (group.size != RING || group.maxOf { it.x } - minX != SPAN || group.maxOf { it.z } - minZ != SPAN) continue
            ring = group
            frameY = y
            centerX = minX + SPAN / 2
            centerZ = minZ + SPAN / 2
            area = BlockArea(centerX - REACH, frameY, centerZ - REACH, centerX + REACH, frameY + HEIGHT, centerZ + REACH)
            return
        }
    }

    private companion object {
        const val FRAME = "minecraft:end_portal_frame"
        const val BEACON = "minecraft:beacon"
        const val RING = 8
        const val SPAN = 4
        const val SUMMON_CLEARS = 2
        const val REACH = 7
        const val HEIGHT = 72
        const val EGG_ABOVE = 38
        const val TOWER_BLOCKS = 38
        const val EGG_BLOCKS = 2_002
    }
}
