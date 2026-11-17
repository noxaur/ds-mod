package io.github.noxaur.dsmod.edge

import com.mojang.blaze3d.platform.InputConstants
import io.github.noxaur.dsmod.core.camera.KeyMode
import io.github.noxaur.dsmod.core.camera.Orbit
import io.github.noxaur.dsmod.core.camera.Slide
import io.github.noxaur.dsmod.core.camera.ThirdPerson
import io.github.noxaur.dsmod.core.camera.View
import io.github.noxaur.dsmod.core.camera.Views
import io.github.noxaur.dsmod.core.feature.general.Camera
import io.github.noxaur.dsmod.core.feature.general.FreeLook
import net.minecraft.client.CameraType
import net.minecraft.client.Minecraft
import net.minecraft.client.player.LocalPlayer
import net.minecraft.world.entity.Entity
import net.minecraft.world.level.ClipContext
import net.minecraft.world.level.Level
import net.minecraft.world.phys.HitResult
import net.minecraft.world.phys.Vec3

/**
 * Camera and Free Look. The mixins in `edge/mixin` ask this object for each number, and it answers with the
 * game's own whenever the feature is off, so every hook is a no-op then.
 *
 * Free Look takes the mouse's turn before it reaches the player and spends it on an angle of its own. The
 * player's rotation is what the game aims with and what it sends, so the server sees a head that stays still.
 */
object Cameras {
    /** Kept between the lifted camera and a ceiling or floor, so the view never starts inside a block. */
    private const val CLEAR = 0.2

    private val camera: Camera get() = DsModClient.feature(Camera::class.java)
    private val freeLook: FreeLook get() = DsModClient.feature(FreeLook::class.java)
    private val shaping: Boolean get() = DsModClient.runs<Camera>()
    private val views: Views get() = if (shaping) camera.views.value else Views.ALL

    private val slide = Slide()
    private var orbit: Orbit? = null

    /** Whose head the orbit left alone. A new world gives a new player, and Free Look ends with the old one. */
    private var looker: LocalPlayer? = null
    private var before = CameraType.FIRST_PERSON

    /** A press since the last frame. A tap can start and end inside one frame, so the press comes from the game's key event. */
    private var tapped = false

    /**
     * [turn] already built the orbit for this press. The mouse is applied before [frame], and Toggle reads a press
     * while an orbit exists as the press that stops, so this one must not.
     */
    private var armed = false

    /** Whether the key was down a frame ago. A key held down repeats its event, and a repeat is not a new press. */
    private var wasDown = false

    /** Whether Free Look has the camera now. */
    val looking: Boolean get() = orbit != null

    private fun type(view: View) = CameraType.entries[view.ordinal]

    private fun view(type: CameraType) = View.entries[type.ordinal]

    /** The game's own key event, which it sends only with no screen open, so a key typed into chat is not a press. */
    fun pressed(key: InputConstants.Key) {
        if (Keys.matches(key, freeLook.key.value)) tapped = true
    }

    private fun show(client: Minecraft, type: CameraType) {
        val was = client.options.cameraType
        client.options.cameraType = type
        // What F5 itself does, so a spectated mob's shader follows the view.
        if (was.isFirstPerson != type.isFirstPerson) client.gameRenderer.checkEntityPostEffect(if (type.isFirstPerson) client.cameraEntity else null)
    }

    /** Runs before each frame's camera is placed, so a press or a release shows in that same frame. */
    fun frame(client: Minecraft) {
        val player = client.player
        val allowed = player != null && DsModClient.runs<FreeLook>()
        val down = allowed && Keys.held(client, freeLook.key.value)
        val pressed = allowed && tapped && !wasDown
        val spent = armed
        tapped = false
        armed = false
        wasDown = down
        if (orbit == null) {
            if (!pressed) return
            begin(client, player)
            return
        }
        // F5 round to first person ends it and keeps that view, because there the camera is the aim.
        val left = client.options.cameraType.isFirstPerson
        val over = if (freeLook.mode.value == KeyMode.HOLD) !down && !pressed else pressed && !spent
        if (!allowed || player !== looker || left || over) {
            orbit = null
            looker = null
            if (!left) show(client, before)
        }
    }

    /** Starts the orbit for a press that [frame] has not seen yet, and switches out of first person with it. */
    private fun begin(client: Minecraft, player: LocalPlayer): Orbit {
        before = client.options.cameraType
        val started = Orbit(player.yRot, player.xRot)
        orbit = started
        looker = player
        if (before.isFirstPerson) show(client, type(views.third))
        return started
    }

    /**
     * Takes the mouse's turn for Free Look, and says whether it did. The orbit has to exist here: this runs before
     * [frame], and a delta that misses it turns the head the server sees.
     */
    fun turn(x: Double, y: Double): Boolean {
        val current = orbit ?: run {
            if (!tapped || wasDown) return false
            val client = Minecraft.getInstance()
            val player = client.player
            if (player == null || !DsModClient.runs<FreeLook>()) return false
            armed = true
            begin(client, player)
        }
        current.turn(x, y)
        return true
    }

    /** The view F5 lands on when the game would go to [next]. */
    fun land(next: CameraType): CameraType = type(views.land(view(next)))

    /** In first person the next slide starts at the head. */
    fun firstPerson() = slide.reset()

    fun yaw(yaw: Float): Float = orbit?.yaw ?: yaw

    /** The pitch before the game mirrors it for the [front] view. */
    fun pitch(pitch: Float, front: Boolean): Float =
        ThirdPerson.pitch(orbit?.pitch ?: pitch, if (shaping) camera.tilt.value else 0f, front)

    /** Where the camera starts from before it backs out: [eyes], raised or lowered as far as the way is clear. */
    fun lift(level: Level, entity: Entity, eyes: Vec3): Vec3 {
        val height = if (shaping) camera.height.value.toDouble() else 0.0
        if (height == 0.0) return eyes
        val to = eyes.add(0.0, height, 0.0)
        val hit = level.clip(ClipContext(eyes, to, ClipContext.Block.VISUAL, ClipContext.Fluid.NONE, entity))
        if (hit.type == HitResult.Type.MISS) return to
        val reach = (hit.location.y - eyes.y).let { if (height > 0) (it - CLEAR).coerceAtLeast(0.0) else (it + CLEAR).coerceAtMost(0.0) }
        return eyes.add(0.0, reach, 0.0)
    }

    fun distance(vanilla: Float): Float = if (shaping) ThirdPerson.distance(vanilla, camera.distance.value) else vanilla

    /** The distance to draw this frame, once the game has shortened [target] for any wall. */
    fun slide(target: Float): Float = slide.at(target, if (shaping) camera.smoothing.value else 0f, System.nanoTime())
}
