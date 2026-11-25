package io.github.noxaur.dragen.gametest

import io.github.noxaur.dragen.core.game.HeadPlaced
import io.github.noxaur.dragen.core.game.ServerSpan
import io.github.noxaur.dragen.core.game.ServerText
import io.github.noxaur.dragen.core.game.TabList
import io.github.noxaur.dragen.core.text.Role
import io.github.noxaur.dragen.core.world.Point
import io.github.noxaur.dragen.core.world.Waypoint
import io.github.noxaur.dragen.edge.DragenClient
import io.github.noxaur.dragen.edge.Packets
import io.github.noxaur.dragen.edge.World
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext
import net.minecraft.client.Minecraft
import net.minecraft.core.BlockPos
import net.minecraft.world.item.component.ResolvableProfile
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.entity.SkullBlockEntity

class EndTrackerEdgeTest : FabricClientGameTest {
    override fun runTest(context: ClientGameTestContext) {
        context.worldBuilder().create().use { world ->
            context.runOnClient<Nothing> {
                World.show("stale", listOf(Waypoint(Point(1.0, 64.0, 1.0), "Stale", Role.GOOD)))
                check(World.marksQueued() == 1) { "The mark was not queued" }
            }
            world.server.runCommand("execute in minecraft:the_end run tp @a 0 80 0")
            context.waitTicks(10)
            context.runOnClient<Nothing> {
                check(World.marksQueued() == 0) { "The new world still had ${World.marksQueued()} marks queued" }
            }

            val posted = ArrayList<HeadPlaced>()
            context.runOnClient<Nothing> { client ->
                DragenClient.game.post(System.currentTimeMillis(), TabList(listOf(ServerText(ServerSpan("Area: The End")))))
                val pos = placeHead(client, posted, 1)
                Packets.tick(client)
                check(posted.isEmpty()) { "A head with no skin was posted: $posted" }
                giveSkin(client, pos)
                Packets.block(pos, checkNotNull(client.level).getBlockState(pos))
                Packets.tick(client)
                check(posted.size == 1) { "A skinned head was posted ${posted.size} times" }
                DragenClient.game.tap = null
            }

            context.runOnClient<Nothing> { client ->
                val pos = placeHead(client, posted, 2)
                repeat(2) { Packets.tick(client) }
                check(posted.isEmpty()) { "A head was posted before its skin arrived: $posted" }
                giveSkin(client, pos)
                Packets.tick(client)
                check(posted.size == 1) { "A skin that arrived after two ticks was posted ${posted.size} times" }
                DragenClient.game.tap = null
            }

            context.runOnClient<Nothing> { client ->
                placeHead(client, posted, 3)
                repeat(40) { Packets.tick(client) }
                check(posted.isEmpty()) { "A decorative head was posted: $posted" }
                DragenClient.game.tap = null
            }
        }
    }

    private fun placeHead(client: Minecraft, posted: MutableList<HeadPlaced>, above: Int): BlockPos {
        posted.clear()
        DragenClient.game.tap = { _, event -> if (event is HeadPlaced) posted += event }
        val level = checkNotNull(client.level)
        val pos = checkNotNull(client.player).blockPosition().above(above)
        level.setBlock(pos, Blocks.PLAYER_HEAD.defaultBlockState(), 3)
        Packets.block(pos, level.getBlockState(pos))
        return pos
    }

    private fun giveSkin(client: Minecraft, pos: BlockPos) {
        val skull = checkNotNull(client.level).getBlockEntity(pos) as SkullBlockEntity
        // @keep The skull has no public setter. The server sends the profile on the block entity, which is this field.
        SkullBlockEntity::class.java.getDeclaredField("owner").apply { isAccessible = true }
            .set(skull, ResolvableProfile.createUnresolved("DsHead"))
    }
}
