package io.github.noxaur.dragen.core.world

import io.github.noxaur.dragen.core.text.Role

data class Point(val x: Double, val y: Double, val z: Double)

sealed interface Mark {
    val role: Role
}

data class Waypoint(
    val at: Point,
    val label: String,
    override val role: Role = Role.VALUE,
    val color: Int = role.argb(FILL_ALPHA),
    val look: WaypointLook = WaypointLook.BEAM,
    val distance: Boolean = true,
    val throughWalls: Boolean = true,
) : Mark

enum class WaypointLook(private val label: String, val box: BoxStyle?, val beam: Boolean) {
    BEAM("Beam", BoxStyle.BOTH, true),
    BOX("Box", BoxStyle.BOTH, false),
    OUTLINE("Outline", BoxStyle.OUTLINE, false),
    FILLED("Filled", BoxStyle.FILLED, false),
    LABEL("Label", null, false),
    ;

    override fun toString() = label
}

const val FILL_ALPHA = 70

data class Cuboid(val min: Point, val max: Point, override val role: Role = Role.VALUE) : Mark

data class Segment(val from: Point, val to: Point, override val role: Role = Role.VALUE) : Mark

data class EntityBeam(val entity: Int, val label: String, override val role: Role = Role.VALUE) : Mark

data class Tracer(val entity: Int, override val role: Role = Role.VALUE) : Mark

enum class BoxStyle(private val label: String) {
    OUTLINE("Outline"),
    FILLED("Filled"),
    BOTH("Both"),
    ;

    override fun toString() = label
}
