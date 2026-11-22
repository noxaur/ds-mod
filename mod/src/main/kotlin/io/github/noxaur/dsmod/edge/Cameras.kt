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

object Cameras {
    private const val CLEAR = 0.2

    private lateinit var camera: Camera
    private lateinit var freeLook: FreeLook

    fun bind(camera: Camera, freeLook: FreeLook) {
        this.camera = camera
        this.freeLook = freeLook
    }

    private val shaping: Boolean get() = camera.feature.runs
    private val views: Views get() = if (shaping) camera.views.value else Views.ALL

    private val slide = Slide()

    internal class Look(val orbit: Orbit, val looker: LocalPlayer, val before: CameraType)

    internal var look: Look? = null

    private var tapped = false

    // @keep turn already built the orbit for this press. Toggle reads a press while an orbit exists as the press that stops it, so this one must not.
    private var armed = false

    private var wasDown = false

    private fun type(view: View) = CameraType.entries[view.ordinal]

    private fun view(type: CameraType) = View.entries[type.ordinal]

    /** @keep The game's own key event, which it sends only with no screen open, so a key typed into chat is not a press. */
    fun pressed(key: InputConstants.Key) {
        if (Keys.matches(key, freeLook.key.value)) tapped = true
    }

    private fun show(client: Minecraft, type: CameraType) {
        val was = client.options.cameraType
        client.options.cameraType = type
        // @keep What F5 itself does, so a spectated mob's shader follows the view.
        if (was.isFirstPerson != type.isFirstPerson) client.gameRenderer.checkEntityPostEffect(if (type.isFirstPerson) client.cameraEntity else null)
    }

    fun frame(client: Minecraft) {
        val player = client.player
        val allowed = player != null && freeLook.feature.runs
        val down = allowed && Keys.held(client, freeLook.key.value)
        val pressed = allowed && tapped && !wasDown
        val spent = armed
        tapped = false
        armed = false
        wasDown = down
        val current = look
        if (current == null) {
            if (!pressed) return
            begin(client, player)
            return
        }
        val left = client.options.cameraType.isFirstPerson
        val over = if (freeLook.mode.value == KeyMode.HOLD) !down && !pressed else pressed && !spent
        if (!allowed || player !== current.looker || left || over) {
            look = null
            if (!left) show(client, current.before)
        }
    }

    private fun begin(client: Minecraft, player: LocalPlayer): Orbit {
        val started = Look(Orbit(player.yRot, player.xRot), player, client.options.cameraType)
        look = started
        if (started.before.isFirstPerson) show(client, type(views.third))
        return started.orbit
    }

    /** @keep The orbit has to exist here: the mouse is applied before [frame], and a delta that misses it turns the head the server sees. */
    fun turn(x: Double, y: Double): Boolean {
        val current = look?.orbit ?: run {
            if (!tapped || wasDown) return false
            val client = Minecraft.getInstance()
            val player = client.player
            if (player == null || !freeLook.feature.runs) return false
            armed = true
            begin(client, player)
        }
        current.turn(x, y)
        return true
    }

    fun land(next: CameraType): CameraType = type(views.land(view(next)))

    fun firstPerson() = slide.reset()

    fun yaw(yaw: Float): Float = look?.orbit?.yaw ?: yaw

    fun pitch(pitch: Float, front: Boolean): Float =
        ThirdPerson.pitch(look?.orbit?.pitch ?: pitch, if (shaping) camera.tilt.value else 0f, front)

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

    fun slide(target: Float): Float = slide.at(target, if (shaping) camera.smoothing.value else 0f, System.nanoTime())
}
