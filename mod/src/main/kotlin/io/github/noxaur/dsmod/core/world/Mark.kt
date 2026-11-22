package io.github.noxaur.dsmod.core.world

import io.github.noxaur.dsmod.core.text.Role

data class Point(val x: Double, val y: Double, val z: Double)

// @keep Marks compare by value, so a feature that returns equal marks changes nothing on screen
sealed interface Mark {
    val role: Role
}

data class Waypoint(val at: Point, val label: String, override val role: Role = Role.VALUE) : Mark

data class EntityBeam(val entity: Int, val label: String, override val role: Role = Role.VALUE) : Mark

data class Tracer(val entity: Int, override val role: Role = Role.VALUE) : Mark

data class BlockBox(val x: Int, val y: Int, val z: Int, override val role: Role = Role.VALUE) : Mark

data class Path(val points: List<Point>, override val role: Role = Role.VALUE) : Mark

data class Ring(val center: Point, val radius: Float, override val role: Role = Role.VALUE) : Mark

enum class BoxStyle(private val label: String) {
    OUTLINE("Outline"),
    FILLED("Filled"),
    BOTH("Both"),
    ;

    override fun toString() = label
}
