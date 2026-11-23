package io.github.noxaur.dsmod.core.world

import io.github.noxaur.dsmod.core.text.Role

data class Point(val x: Double, val y: Double, val z: Double)

sealed interface Mark {
    val role: Role
}

data class Waypoint(val at: Point, val label: String, override val role: Role = Role.VALUE) : Mark

data class EntityBeam(val entity: Int, val label: String, override val role: Role = Role.VALUE) : Mark

data class Tracer(val entity: Int, override val role: Role = Role.VALUE) : Mark

enum class BoxStyle(private val label: String) {
    OUTLINE("Outline"),
    FILLED("Filled"),
    BOTH("Both"),
    ;

    override fun toString() = label
}
