package io.github.noxaur.dsmod.edge

import io.github.noxaur.dsmod.core.feature.general.EtherwarpOutline
import io.github.noxaur.dsmod.core.hand.Etherwarp
import io.github.noxaur.dsmod.core.text.Role
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelExtractionEvents
import net.minecraft.client.Minecraft
import net.minecraft.client.multiplayer.ClientLevel
import net.minecraft.core.BlockPos
import net.minecraft.core.component.DataComponents
import net.minecraft.world.entity.Pose
import net.minecraft.world.item.ItemStack
import net.minecraft.world.phys.AABB

/**
 * Etherwarp Outline: boxes the block the held item's Etherwarp would land on, while the player sneaks. The box
 * follows the crosshair, so it is drawn on each frame, and the line is followed again only when the view or the
 * tick has changed since, which picks up a block that changed under a still view. It only draws, and sends nothing.
 */
object Etherwarps {
    private val settings: EtherwarpOutline get() = DsModClient.feature(EtherwarpOutline::class.java)

    private val at = BlockPos.MutableBlockPos()

    // The world the line is followed through, held only while it is, so a world the player left is not kept.
    private var level: ClientLevel? = null
    private val blocks = Etherwarp.Blocks { x, y, z ->
        val level = level!!
        !level.getBlockState(at.set(x, y, z)).getCollisionShape(level, at).isEmpty
    }

    // The game hands back the same stack every frame until the slot changes, so its lore is read once.
    private var held: ItemStack? = null
    private var range = 0

    // The view the target was last found for.
    private var tick = -1L
    private var eyeX = 0.0
    private var eyeY = 0.0
    private var eyeZ = 0.0
    private var yaw = 0f
    private var pitch = 0f
    private var foundRange = 0
    private var world = 0

    /** The block boxed on this frame, and why a warp there fails, or null when nothing is boxed. The game test reads it. */
    var target: Etherwarp.Target? = null
        private set

    /** The box drawn on this frame, or null. The game test reads it. */
    var box: World.FrameBox? = null
        private set

    fun register() {
        LevelExtractionEvents.AFTER_BLOCK_OUTLINE_EXTRACTION.register { context, _ -> frame(context.level()) }
    }

    private fun frame(level: ClientLevel) {
        val client = Minecraft.getInstance()
        val player = client.player
        val s = settings
        if (!DsModClient.runs<EtherwarpOutline>() || player == null || client.gui.screen() != null || client.gui.hud.isHidden ||
            !player.isShiftKeyDown && !s.withoutSneak.value
        ) {
            return clear()
        }
        val stack = player.mainHandItem
        if (stack !== held) {
            held = stack
            range = Etherwarp.range(stack.get(DataComponents.LORE)?.lines()?.map { it.toText().plain }.orEmpty())
        }
        if (range == 0) return clear()
        DsModClient.game.metrics.time("etherwarp.frame") {
            // The server warps from where it last saw the player, which is where the last tick left them and not
            // where this frame draws them, and from a sneaking eye, which is lower than a standing one.
            val x = player.x
            val y = player.y + player.getEyeHeight(Pose.CROUCHING)
            val z = player.z
            val now = level.gameTime
            if (now != tick || x != eyeX || y != eyeY || z != eyeZ || player.yRot != yaw || player.xRot != pitch ||
                range != foundRange || System.identityHashCode(level) != world
            ) {
                tick = now
                eyeX = x
                eyeY = y
                eyeZ = z
                yaw = player.yRot
                pitch = player.xRot
                foundRange = range
                world = System.identityHashCode(level)
                val look = player.getViewVector(1f)
                this.level = level
                val found = try {
                    Etherwarp.target(x, y, z, look.x, look.y, look.z, range, if (s.failed.value) Etherwarp.BEYOND else 0, blocks)
                } finally {
                    this.level = null
                }
                target = found?.takeIf { it.miss == null || s.failed.value }
                box = target?.let { target ->
                    val pos = BlockPos(target.x, target.y, target.z)
                    val shape = level.getBlockState(pos).getShape(level, pos)
                    val bounds = if (shape.isEmpty) AABB(pos) else shape.bounds().move(pos)
                    val role = if (target.miss == null) Role.GOOD else Role.BAD
                    World.FrameBox(bounds, role, s.style.value, s.width.value, s.throughWalls.value, target.miss?.label)
                }
            }
            box?.let(World::draw)
        }
    }

    /** Draws nothing, and follows the line again the next time the box shows. */
    private fun clear() {
        target = null
        box = null
        tick = -1
    }
}
