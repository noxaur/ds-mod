package io.github.noxaur.dsmod.gametest

import io.github.noxaur.dsmod.core.feature.general.EtherwarpOutline
import io.github.noxaur.dsmod.core.hand.Etherwarp
import io.github.noxaur.dsmod.core.hand.Etherwarp.Miss
import io.github.noxaur.dsmod.edge.DsModClient
import io.github.noxaur.dsmod.edge.Etherwarps
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext
import net.minecraft.core.component.DataComponents
import net.minecraft.network.chat.Component
import net.minecraft.world.InteractionHand
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.minecraft.world.item.component.ItemLore
import net.minecraft.world.phys.AABB
import java.nio.file.Path
import javax.imageio.ImageIO

/**
 * The player stands on a pillar high in the air and looks east along y 101, the height of a sneaking eye, with an
 * Aspect of the Void whose lore is written as dragsim.co writes it. A wall 10 blocks out is boxed green, then red
 * as No Room once a block sits above it, and a block past the 57-block range is red as Too Far. Water, grass and a
 * torch on the line do not stop it, a slab is boxed as half a block, and nothing is boxed without the item or the
 * sneak. Read the screenshots: a green box, a red box labeled No Room, a red box labeled Too Far, and the settings.
 */
class EtherwarpOutlineTest : FabricClientGameTest {
    private val aspect = listOf(
        "§7Teleport §a15 blocks §7ahead of you and",
        "",
        "§6Etherwarp §eSNEAK RIGHT CLICK",
        "§7Teleport to the block you are",
        "§7looking at, up to §a57 blocks §7away.",
        "§8Mana Cost: §3250",
    )

    private fun target(context: ClientGameTestContext) = context.computeOnClient<Etherwarp.Target?, Nothing> { Etherwarps.target }

    private fun bounds(context: ClientGameTestContext) = context.computeOnClient<AABB?, Nothing> { Etherwarps.box?.box }

    private fun hold(context: ClientGameTestContext, world: TestSingleplayerContext, lore: List<String>?) {
        world.server.runOnServer<Nothing> { server ->
            val stack = if (lore == null) ItemStack.EMPTY else ItemStack(Items.DIAMOND_SHOVEL).also {
                it.set(DataComponents.CUSTOM_NAME, Component.literal("Aspect of the Void"))
                it.set(DataComponents.LORE, ItemLore(lore.map(Component::literal)))
            }
            server.playerList.players.first().setItemInHand(InteractionHand.MAIN_HAND, stack)
        }
        context.waitTicks(3)
    }

    private fun set(context: ClientGameTestContext, world: TestSingleplayerContext, vararg commands: String) {
        commands.forEach(world.server::runCommand)
        context.waitTicks(3)
    }

    /** How many pixels in the middle of the screen are near [rgb], a box's edge color. */
    private fun near(screenshot: Path, rgb: Int): Int {
        val image = ImageIO.read(screenshot.toFile())
        var count = 0
        for (y in image.height / 4 until image.height * 3 / 4) for (x in image.width / 4 until image.width * 3 / 4) {
            val pixel = image.getRGB(x, y)
            val distance = Math.abs((pixel shr 16 and 0xFF) - (rgb shr 16 and 0xFF)) +
                Math.abs((pixel shr 8 and 0xFF) - (rgb shr 8 and 0xFF)) + Math.abs((pixel and 0xFF) - (rgb and 0xFF))
            if (distance < 60) count++
        }
        return count
    }

    override fun runTest(context: ClientGameTestContext) {
        context.worldBuilder().create().use { world ->
            set(context, world, "fill -1 99 -1 1 99 1 stone", "tp @a 0.5 100 0.5 -90 0", "setblock 10 101 0 stone")
            context.input.lookAt(-90f, 0f)
            context.input.holdKey { it.keyShift }
            context.waitTicks(10)
            try {
                check(target(context) == null) { "An empty hand is boxed: ${target(context)}" }
                hold(context, world, aspect.take(2) + listOf("", "§6Etherwarp Conduit", "§7to unlock §bEtherwarp§7."))
                check(target(context) == null) { "An item that names Etherwarp with no range is boxed: ${target(context)}" }

                hold(context, world, aspect)
                check(target(context) == Etherwarp.Target(10, 101, 0, null)) { "The wall is not boxed as a warp: ${target(context)}" }
                val valid = context.screenshot("etherwarp-valid")
                check(near(valid, 0x55FF55) > 50) { "The screenshot shows no green box" }

                set(context, world, "setblock 10 102 0 stone")
                check(target(context) == Etherwarp.Target(10, 101, 0, Miss.NO_ROOM)) { "A block above the wall is not No Room: ${target(context)}" }
                val noRoom = context.screenshot("etherwarp-no-room")
                check(near(noRoom, 0xFF5555) > 50) { "The screenshot shows no red box" }
                DsModClient.feature<EtherwarpOutline>().failed.value = false
                context.waitTicks(2)
                check(target(context) == null) { "A failed warp is boxed with Failed Warps off: ${target(context)}" }
                DsModClient.feature<EtherwarpOutline>().failed.value = true

                // The eye is half a block into its own, so x 58's near side is 57.5 blocks out, past the range.
                set(context, world, "fill 10 101 0 10 102 0 air", "setblock 58 101 0 stone")
                check(target(context) == Etherwarp.Target(58, 101, 0, Miss.TOO_FAR)) { "A block past the range is not Too Far: ${target(context)}" }
                context.screenshot("etherwarp-too-far")
                set(context, world, "setblock 57 101 0 stone")
                check(target(context) == Etherwarp.Target(57, 101, 0, null)) { "The last block in range is not boxed as a warp: ${target(context)}" }

                set(context, world, "setblock 57 101 0 air", "setblock 58 101 0 air", "setblock 12 101 0 stone_slab")
                check(target(context) == Etherwarp.Target(12, 101, 0, null)) { "A slab is not boxed: ${target(context)}" }
                check(bounds(context)?.maxY == 101.5) { "A slab is boxed as ${bounds(context)}" }
                DsModClient.feature<EtherwarpOutline>().throughWalls.value = false
                context.waitTicks(2)
                // A box the blocks may hide stands just off the slab's faces, so the two do not flicker.
                check(bounds(context)!!.maxY > 101.5) { "A box behind walls lies on the slab: ${bounds(context)}" }
                DsModClient.feature<EtherwarpOutline>().throughWalls.value = true

                // Fluids, plants and torches have no collision box, so the line passes through them.
                set(context, world, "setblock 4 101 0 water", "setblock 6 100 0 grass_block", "setblock 6 101 0 short_grass",
                    "setblock 7 100 0 stone", "setblock 7 101 0 torch")
                check(target(context) == Etherwarp.Target(12, 101, 0, null)) { "Water, grass or a torch stopped the line: ${target(context)}" }

                context.input.releaseKey { it.keyShift }
                context.waitTicks(3)
                check(target(context) == null) { "The box shows without a sneak: ${target(context)}" }
                DsModClient.feature<EtherwarpOutline>().withoutSneak.value = true
                context.waitTicks(2)
                check(target(context) == Etherwarp.Target(12, 101, 0, null)) { "Box Without Sneak shows no box: ${target(context)}" }

                context.runOnClient<Nothing> { DsModClient.openSettings("etherwarp-outline") }
                context.waitTicks(20)
                context.takeScreenshot("etherwarp-settings")
                context.setScreen { null }
            } finally {
                context.input.releaseKey { it.keyShift }
                DsModClient.feature<EtherwarpOutline>().failed.value = true
                DsModClient.feature<EtherwarpOutline>().throughWalls.value = true
                DsModClient.feature<EtherwarpOutline>().withoutSneak.value = false
            }
        }
    }
}
