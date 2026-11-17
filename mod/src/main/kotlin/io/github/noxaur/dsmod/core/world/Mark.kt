package io.github.noxaur.dsmod.core.world

import io.github.noxaur.dsmod.core.text.Role

data class Point(val x: Double, val y: Double, val z: Double)

/** Something drawn in the world. Marks compare by value, so a feature that returns equal marks changes nothing on screen. */
sealed interface Mark {
    val role: Role
}

/** A beam with a label and the distance to it, seen through walls. */
data class Waypoint(val at: Point, val label: String, override val role: Role = Role.VALUE) : Mark

/** A beam over the [entity] with this id on the client, with a label and the distance to it, seen through walls. It follows the entity. */
data class EntityBeam(val entity: Int, val label: String, override val role: Role = Role.VALUE) : Mark

/** A line from the middle of the screen to the [entity] with this id on the client, seen through walls. */
data class Tracer(val entity: Int, override val role: Role = Role.VALUE) : Mark

/** An outlined, tinted block. */
data class BlockBox(val x: Int, val y: Int, val z: Int, override val role: Role = Role.VALUE) : Mark

/** A line through the points in order: a connector, a trail or a route. A line to an entity is a [Tracer]. */
data class Path(val points: List<Point>, override val role: Role = Role.VALUE) : Mark

/** A flat circle around a point. */
data class Ring(val center: Point, val radius: Float, override val role: Role = Role.VALUE) : Mark

/** How a box is drawn: its edges, a tinted fill, or both. */
enum class BoxStyle(private val label: String) {
    OUTLINE("Outline"),
    FILLED("Filled"),
    BOTH("Both"),
    ;

    override fun toString() = label
}
