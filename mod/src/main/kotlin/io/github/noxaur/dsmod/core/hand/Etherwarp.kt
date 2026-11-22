package io.github.noxaur.dsmod.core.hand

import io.github.noxaur.dsmod.core.text.count
import kotlin.math.abs
import kotlin.math.floor

/**
 * Etherwarp, the sneak-click teleport an Aspect of the End or of the Void gains from an Etherwarp Conduit. The server
 * follows the look line from the eye one block boundary at a time to the first block with a collision box, and warps
 * onto it if the two blocks above it are clear. Fluids, grass and torches have no collision box, so the line passes
 * through them.
 */
object Etherwarp {
    /** How many blocks past the range the line is followed, to show a target that is too far. */
    const val BEYOND = 64

    private val RANGE = Regex("""up to (\d+) blocks""")

    /** Why a warp onto a [Target] fails. */
    enum class Miss(val label: String) {
        TOO_FAR("Too Far"),
        NO_ROOM("No Room"),
    }

    /** The block the line stops at, and why the warp fails there, or null [miss] when it lands. */
    data class Target(val x: Int, val y: Int, val z: Int, val miss: Miss?)

    /** Whether a block has a collision box. */
    fun interface Blocks {
        fun solid(x: Int, y: Int, z: Int): Boolean
    }

    /**
     * The range in blocks of the Etherwarp in [lore], the item's plain lore lines, or 0 when it has none. The server
     * writes it in the ability's own section, "looking at, up to 57 blocks away.", which ends at a blank line. So the
     * "Teleport 8 blocks ahead" of Instant Transmission above it is never read, and an Etherwarp Conduit, which names
     * Etherwarp in its description and has no range, has none.
     */
    fun range(lore: List<String>): Int {
        val header = lore.indexOfFirst { "Etherwarp" in it }
        if (header < 0) return 0
        for (i in header until lore.size) {
            if (lore[i].isBlank()) break
            RANGE.find(lore[i])?.let { return count(it.groupValues[1]) ?: 0 }
        }
        return 0
    }

    /**
     * Where the line from the eye along the look direction stops: the first block with a collision box whose near
     * side is within [range] blocks, or within [range] plus [beyond] as a target that is too far. Null when the
     * line meets nothing that close.
     */
    fun target(
        eyeX: Double,
        eyeY: Double,
        eyeZ: Double,
        lookX: Double,
        lookY: Double,
        lookZ: Double,
        range: Int,
        beyond: Int,
        blocks: Blocks,
    ): Target? {
        var x = floor(eyeX).toInt()
        var y = floor(eyeY).toInt()
        var z = floor(eyeZ).toInt()
        val stepX = if (lookX > 0) 1 else -1
        val stepY = if (lookY > 0) 1 else -1
        val stepZ = if (lookZ > 0) 1 else -1
        // How far along the line each axis's next block boundary is, and how far apart its boundaries are.
        val spanX = span(lookX)
        val spanY = span(lookY)
        val spanZ = span(lookZ)
        var nextX = next(eyeX, lookX)
        var nextY = next(eyeY, lookY)
        var nextZ = next(eyeZ, lookZ)
        var along = 0.0
        val limit = range + beyond
        while (along <= limit) {
            if (blocks.solid(x, y, z)) {
                val miss = when {
                    along > range -> Miss.TOO_FAR
                    blocks.solid(x, y + 1, z) || blocks.solid(x, y + 2, z) -> Miss.NO_ROOM
                    else -> null
                }
                return Target(x, y, z, miss)
            }
            if (nextX < nextY && nextX < nextZ) {
                x += stepX
                along = nextX
                nextX += spanX
            } else if (nextY < nextZ) {
                y += stepY
                along = nextY
                nextY += spanY
            } else {
                z += stepZ
                along = nextZ
                nextZ += spanZ
            }
        }
        return null
    }

    /** How far along the line one axis's block boundaries are apart. An axis the line does not move along has none. */
    private fun span(look: Double) = if (look == 0.0) Double.POSITIVE_INFINITY else 1 / abs(look)

    /** How far along the line one axis's first block boundary from [at] is. */
    private fun next(at: Double, look: Double): Double {
        if (look == 0.0) return Double.POSITIVE_INFINITY
        val into = at - floor(at)
        return (if (look > 0) 1 - into else into) / abs(look)
    }
}
