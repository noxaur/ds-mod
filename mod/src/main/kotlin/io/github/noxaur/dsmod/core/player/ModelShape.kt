package io.github.noxaur.dsmod.core.player

/** Whose model Player Model changes. */
enum class ModelTarget(private val label: String) {
    SELF("Only Me"),
    EVERYONE("Everyone"),
    ;

    override fun toString() = label
}

/** A limb's turn about the model's x, y and z, in degrees, as Minecraft's model parts take them in radians. */
class Turn(val x: Float, val y: Float, val z: Float)

/** The turns of the four limbs, each held still in place of the game's walk and swing. */
class Limbs(val rightArm: Turn, val leftArm: Turn, val rightLeg: Turn, val leftLeg: Turn)

private val STRAIGHT = Turn(0f, 0f, 0f)

/**
 * A pose the arms and legs hold, which spin and tilt turn as one. The head still follows where the player looks. A
 * part's z turn swings it outward for the right side, so the left side takes it negated.
 */
enum class ModelPose(private val label: String, val limbs: Limbs?) {
    GAME("Game's Own", null),
    T_POSE("T-Pose", Limbs(Turn(0f, 0f, 90f), Turn(0f, 0f, -90f), STRAIGHT, STRAIGHT)),
    STAR("Star", Limbs(Turn(0f, 0f, 135f), Turn(0f, 0f, -135f), Turn(0f, 0f, 25f), Turn(0f, 0f, -25f))),
    ARMS_UP("Arms Up", Limbs(Turn(-180f, 0f, 0f), Turn(-180f, 0f, 0f), STRAIGHT, STRAIGHT)),
    ZOMBIE("Zombie", Limbs(Turn(-90f, 0f, 0f), Turn(-90f, 0f, 0f), STRAIGHT, STRAIGHT)),
    DAB("Dab", Limbs(Turn(-20f, 0f, 125f), Turn(-100f, 50f, 0f), STRAIGHT, STRAIGHT)),

    /** The game's own seat on a horse, held in the air. */
    SIT("Sit", Limbs(Turn(-36f, 0f, 0f), Turn(-36f, 0f, 0f), Turn(-81f, 18f, 0f), Turn(-81f, -18f, 0f))),
    ;

    override fun toString() = label
}

object ModelShape {
    /** How far round a model that spins [turns] times a second is at [nanos], in degrees from 0 up to 360. */
    fun spin(nanos: Long, turns: Float): Float {
        if (turns == 0f) return 0f
        val round = (nanos / 1e9 * turns).mod(1.0)
        return (round * 360).toFloat()
    }
}
