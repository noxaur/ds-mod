package io.github.noxaur.dragen.debug.zone

import io.github.noxaur.dragen.api.world.BlockArea
import kotlin.math.abs
import kotlin.math.sign

data class Corner(val x: Int, val y: Int, val z: Int) {
    override fun toString() = "$x, $y, $z"
}

class ZonePick {
    var first: Corner? = null
        private set
    var second: Corner? = null
        private set
    var selected = 1
        private set

    fun set(which: Int, at: Corner) {
        if (which == 1) first = at else second = at
        selected = which
    }

    // @keep Like Litematica, a scroll moves the selected corner one block along the axis the player looks down most.
    fun nudge(lookX: Double, lookY: Double, lookZ: Double, steps: Int): Boolean {
        val at = (if (selected == 1) first else second) ?: return false
        val ax = abs(lookX)
        val ay = abs(lookY)
        val az = abs(lookZ)
        val moved = when {
            ay >= ax && ay >= az -> at.copy(y = at.y + sign(lookY).toInt() * steps)
            ax >= az -> at.copy(x = at.x + sign(lookX).toInt() * steps)
            else -> at.copy(z = at.z + sign(lookZ).toInt() * steps)
        }
        set(selected, moved)
        return true
    }

    fun clear() {
        first = null
        second = null
        selected = 1
    }

    val area: BlockArea?
        get() {
            val a = first ?: return null
            val b = second ?: return null
            return BlockArea(minOf(a.x, b.x), minOf(a.y, b.y), minOf(a.z, b.z), maxOf(a.x, b.x), maxOf(a.y, b.y), maxOf(a.z, b.z))
        }

    companion object {
        fun size(area: BlockArea) = Triple(area.maxX - area.minX + 1, area.maxY - area.minY + 1, area.maxZ - area.minZ + 1)

        fun constant(name: String, area: BlockArea, place: String?): String {
            val title = name.trim()
            val id = title.replace("'", "").uppercase().replace(Regex("[^A-Z0-9]+"), "_").trim('_')
            val where = place?.let { " // $it" }.orEmpty()
            return "public val $id: Zone = Zone(\"$title\", BlockArea(${area.minX}, ${area.minY}, ${area.minZ}, ${area.maxX}, ${area.maxY}, ${area.maxZ}))$where"
        }
    }
}
