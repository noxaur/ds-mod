package io.github.noxaur.dsmod.edge

import io.github.noxaur.dsmod.core.feature.Game
import io.github.noxaur.dsmod.core.feature.general.EtherwarpOutline
import io.github.noxaur.dsmod.core.game.Metrics
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

object Etherwarps {
    private lateinit var game: Game
    private lateinit var settings: EtherwarpOutline

    fun bind(game: Game, settings: EtherwarpOutline) {
        this.game = game
        this.settings = settings
    }

    private val at = BlockPos.MutableBlockPos()

    private var level: ClientLevel? = null
    private val blocks = Etherwarp.Blocks { x, y, z ->
        val level = checkNotNull(level) { "the line is followed only while its world is held" }
        !level.getBlockState(at.set(x, y, z)).getCollisionShape(level, at).isEmpty
    }

    private var held: ItemStack? = null
    private var range = 0

    private var tick = -1L
    private var eyeX = 0.0
    private var eyeY = 0.0
    private var eyeZ = 0.0
    private var yaw = 0f
    private var pitch = 0f
    private var foundRange = 0
    private var world = 0

    var target: Etherwarp.Target? = null
        private set

    var box: World.FrameBox? = null
        private set

    fun register() {
        LevelExtractionEvents.AFTER_BLOCK_OUTLINE_EXTRACTION.register { context, _ -> frame(context.level()) }
    }

    private fun frame(level: ClientLevel) {
        val client = Minecraft.getInstance()
        val player = client.player
        val s = settings
        if (!s.feature.runs || player == null || client.gui.screen() != null || client.gui.hud.isHidden ||
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
        game.metrics.time("etherwarp.frame", Metrics.FRAME) {
            /* @keep The server warps from where it last saw the player, which is where the last tick left them and not where
             * this frame draws them, and from a sneaking eye, which is lower than a standing one. */
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

    private fun clear() {
        target = null
        box = null
        tick = -1
    }
}
