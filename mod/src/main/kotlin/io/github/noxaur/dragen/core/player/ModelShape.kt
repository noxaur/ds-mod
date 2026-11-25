package io.github.noxaur.dragen.core.player

enum class ModelTarget(private val label: String) {
    SELF("Only Me"),
    EVERYONE("Everyone"),
    ;

    override fun toString() = label
}

// @keep In degrees, as Minecraft's model parts take them in radians
class Turn(val x: Float, val y: Float, val z: Float)

class Limbs(val rightArm: Turn, val leftArm: Turn, val rightLeg: Turn, val leftLeg: Turn)

private val STRAIGHT = Turn(0f, 0f, 0f)

private fun armsForward(x: Float) = Turn(x, 0f, 0f).let { Limbs(it, it, STRAIGHT, STRAIGHT) }

// @keep A part's z turn swings it outward for the right side, so the left side takes it negated
enum class ModelPose(private val label: String, val limbs: Limbs?) {
    GAME("Game's Own", null),
    T_POSE("T-Pose", Limbs(Turn(0f, 0f, 90f), Turn(0f, 0f, -90f), STRAIGHT, STRAIGHT)),
    STAR("Star", Limbs(Turn(0f, 0f, 135f), Turn(0f, 0f, -135f), Turn(0f, 0f, 25f), Turn(0f, 0f, -25f))),
    ARMS_UP("Arms Up", armsForward(-180f)),
    ZOMBIE("Zombie", armsForward(-90f)),
    DAB("Dab", Limbs(Turn(-20f, 0f, 125f), Turn(-100f, 50f, 0f), STRAIGHT, STRAIGHT)),

    SIT("Sit", Limbs(Turn(-36f, 0f, 0f), Turn(-36f, 0f, 0f), Turn(-81f, 18f, 0f), Turn(-81f, -18f, 0f))),
    ;

    override fun toString() = label
}

object ModelShape {
    fun spin(nanos: Long, turns: Float): Float {
        if (turns == 0f) return 0f
        val round = (nanos / 1e9 * turns).mod(1.0)
        return (round * 360).toFloat()
    }
}
