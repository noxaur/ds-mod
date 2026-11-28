package io.github.noxaur.dragen.api.world

import io.github.noxaur.dragen.api.Vec
import kotlin.math.hypot

public data class BlockArea(
    public val minX: Int,
    public val minY: Int,
    public val minZ: Int,
    public val maxX: Int,
    public val maxY: Int,
    public val maxZ: Int,
) {
    public fun contains(x: Int, y: Int, z: Int): Boolean = x in minX..maxX && y in minY..maxY && z in minZ..maxZ

    public operator fun contains(at: Vec): Boolean = at.x >= minX && at.x < maxX + 1 && at.y >= minY && at.y < maxY + 1 && at.z >= minZ && at.z < maxZ + 1

    internal fun across(at: Vec): Double = hypot(gap(at.x, minX, maxX), gap(at.z, minZ, maxZ))

    private fun gap(at: Double, min: Int, max: Int) = maxOf(min - at, at - (max + 1), 0.0)
}

public data class Zone(public val name: String, public val area: BlockArea)

/** @keep The server lists no name for these places, so they were picked in the game with the Zone Picker. */
public object Zones {
    public val DRAGON_ALTAR: Zone = Zone("Dragon Altar", BlockArea(-683, 3, -289, -659, 21, -263))
    public val DRACONIC_ALTAR: Zone = Zone("Draconic Altar", BlockArea(-605, 4, -284, -584, 15, -267))
    public val DRAGONS_DEN_GATE: Zone = Zone("Dragon's Den Gate", BlockArea(-609, 21, -297, -597, 42, -257))
    public val DRAGONS_DEN_SPAWN: Zone = Zone("Dragon's Den Spawn", BlockArea(-631, 14, -279, -624, 18, -273))
    public val BRUISER_HIDEOUT: Zone = Zone("Bruiser Hideout", BlockArea(-735, -19, -328, -647, 3, -218))
    public val THE_FORGE: Zone = Zone("The Forge", BlockArea(-700, 10, -305, -691, 16, -299))

    public val all: List<Zone> = listOf(DRAGON_ALTAR, DRACONIC_ALTAR, DRAGONS_DEN_GATE, DRAGONS_DEN_SPAWN, BRUISER_HIDEOUT, THE_FORGE)

    public fun at(at: Vec): Zone? = all.firstOrNull { at in it.area }
}
