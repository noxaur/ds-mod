package io.github.noxaur.dsmod.core.world

import io.github.noxaur.dsmod.core.text.Role

class Point(val x: Double, val y: Double, val z: Double)

/** Something drawn in the world. */
sealed interface Mark {
    val role: Role
}

/** A beam with a label and the distance to it, seen through walls. */
class Waypoint(val at: Point, val label: String, override val role: Role = Role.VALUE) : Mark

/** An outlined, tinted block. */
class BlockBox(val x: Int, val y: Int, val z: Int, override val role: Role = Role.VALUE) : Mark

/** A line through the points in order: a tracer, a connector, a trail or a route. */
class Path(val points: List<Point>, override val role: Role = Role.VALUE) : Mark

/** A flat circle around a point. */
class Ring(val center: Point, val radius: Float, override val role: Role = Role.VALUE) : Mark
