package io.github.noxaur.dsmod.core.world

import io.github.noxaur.dsmod.core.text.Role

data class Point(val x: Double, val y: Double, val z: Double)

// @keep Marks compare by value, so a feature that returns equal marks changes nothing on screen
sealed interface Mark {
    val role: Role
}

/** @keep The color's opacity is the box's fill, and its outline, beam and label are drawn solid in the same color, so a
 * see-through fill never hides the label. A waypoint drawn behind the world is hidden by the blocks in front of it, as the
 * game draws everything else */
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

// @keep A fill this faint shows the block it marks through it.
const val FILL_ALPHA = 70

data class EntityBeam(val entity: Int, val label: String, override val role: Role = Role.VALUE) : Mark

data class Tracer(val entity: Int, override val role: Role = Role.VALUE) : Mark

enum class BoxStyle(private val label: String) {
    OUTLINE("Outline"),
    FILLED("Filled"),
    BOTH("Both"),
    ;

    override fun toString() = label
}
