package io.github.noxaur.dsmod.gametest

import io.github.noxaur.dsmod.core.feature.menus.look.menutheme.MenuTheme
import io.github.noxaur.dsmod.core.feature.menus.helpers.profilecooldown.ProfileCooldown
import io.github.noxaur.dsmod.core.player.PlayerData
import io.github.noxaur.dsmod.edge.DsModClient
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext
import net.minecraft.client.Minecraft
import net.minecraft.network.chat.Component
import net.minecraft.world.item.Items

class ProfileCooldownTest : FabricClientGameTest {
    private val feature get() = DsModClient.feature<ProfileCooldown>()

    private fun profiles(context: ClientGameTestContext, world: TestSingleplayerContext) =
        ServerMenus.open(context, world, 4, PlayerData.PROFILES) { it.setItem(10, ServerMenus.lored(Items.GRASS_BLOCK, "Young", listOf("In use", ""))) }

    private fun askToSwitch(context: ClientGameTestContext, world: TestSingleplayerContext) {
        profiles(context, world)
        context.clickSlot(10)
        ServerMenus.open(context, world, 4, PlayerData.PROFILES) {
            it.setItem(11, ServerMenus.lored(Items.GRASS_BLOCK, "Switch to Profile", listOf("Loads your inventory, skills,")))
        }
        context.clickSlot(11)
    }

    private fun close(context: ClientGameTestContext, world: TestSingleplayerContext) {
        world.server.runOnServer<Nothing> { server -> server.player.closeContainer() }
        context.waitFor { it.gui.screen() == null }
        context.waitTicks(2)
    }

    private fun left(client: Minecraft) = feature.left(client.player!!.containerMenu.containerId, System.currentTimeMillis())

    private fun left(context: ClientGameTestContext) = context.computeOnClient<Long?, Nothing>(::left)

    override fun runTest(context: ClientGameTestContext) {
        context.worldBuilder().create().use { world ->
            profiles(context, world)
            check(left(context) == null) { "A countdown showed before any switch" }

            askToSwitch(context, world)
            close(context, world)
            profiles(context, world)
            val counting = left(context)
            check(counting != null && counting in PlayerData.COOLDOWN_MILLIS - 5000..PlayerData.COOLDOWN_MILLIS) { "The switch did not start the 30 seconds: $counting" }
            context.shotInBothThemes("profile-cooldown-waiting")

            askToSwitch(context, world)
            world.server.runOnServer<Nothing> { server -> server.player.sendSystemMessage(Component.literal("You can switch profiles in 1s.")) }
            close(context, world)
            profiles(context, world)
            context.waitFor { left(it).let { left -> left != null && left <= 0 } }
            context.shotInBothThemes("profile-cooldown-ready")
            context.runOnClient<Nothing> { DsModClient.feature<MenuTheme>().feature.enabled = false }
            context.waitTicks(2)
            context.takeScreenshot("profile-cooldown-ready-plain")

            context.waitFor({ left(it) == null }, ProfileCooldown.READY_MILLIS.toInt() / 50 + 40)
        }
    }
}
