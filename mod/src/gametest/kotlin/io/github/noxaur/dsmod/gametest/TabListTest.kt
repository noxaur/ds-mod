package io.github.noxaur.dsmod.gametest

import com.mojang.authlib.GameProfile
import io.github.noxaur.dsmod.core.game.TabList
import io.github.noxaur.dsmod.edge.DsModClient
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext
import net.minecraft.network.chat.Component
import net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket
import net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket.Action
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ClientInformation
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.level.GameType
import java.util.EnumSet
import java.util.UUID

class TabListTest : FabricClientGameTest {
    /** @keep A tab list entry borrows a real connection, because the packet reads the latency from it. */
    private class Listed(server: MinecraftServer, name: String, var display: String?, private val order: Int = 0, private val mode: GameType = GameType.SURVIVAL) :
        ServerPlayer(server, server.overworld(), GameProfile(UUID.randomUUID(), name), ClientInformation.createDefault()) {
        init {
            connection = server.player.connection
        }

        override fun getTabListDisplayName(): Component? = display?.let(Component::literal)

        override fun getTabListOrder() = order

        override fun gameMode() = mode
    }

    private fun checkStage(context: ClientGameTestContext, stage: String, last: String) = context.readout("protector-status").let {
        check(it.first() == "Stage: $stage" && it.last().startsWith(last)) { "Got $it" }
    }

    override fun runTest(context: ClientGameTestContext) {
        context.worldBuilder().create().use { world ->
            val posted = ArrayList<TabList>()
            context.runOnClient<Nothing> { DsModClient.game.tap = { _, event -> if (event is TabList) posted += event } }

            listOf("team add zz", """team modify zz prefix {"text":"[T] "}""", "team join zz teamed").forEach(world.server::runCommand)
            val area = world.server.computeOnServer<Listed, Nothing> { Listed(it, "!bb", "Area: The End") }
            val protector = world.server.computeOnServer<Listed, Nothing> { Listed(it, "!bc", " Protector: Rising (Tier 3/5, 97)") }
            val others = world.server.computeOnServer<List<Listed>, Nothing> { server ->
                listOf(
                    Listed(server, "!aa", "     End Island (1) 1/1     "),
                    Listed(server, "!ba", "             Info             "),
                    Listed(server, "zeta", null),
                    Listed(server, "Alpha", null),
                    Listed(server, "beta", null),
                    Listed(server, "front", null, order = 5),
                    Listed(server, "teamed", null),
                    Listed(server, "ghost", null, mode = GameType.SPECTATOR),
                )
            }
            val adding = EnumSet.of(Action.ADD_PLAYER, Action.UPDATE_LISTED, Action.UPDATE_GAME_MODE, Action.UPDATE_DISPLAY_NAME, Action.UPDATE_LIST_ORDER)
            world.server.runOnServer<Nothing> { it.playerList.broadcastAll(ClientboundPlayerInfoUpdatePacket(adding, others + area + protector)) }
            context.waitTicks(20)

            val drawn = context.computeOnClient<List<String>, Nothing> { client ->
                val tab = client.gui.hud.tabList
                tab.getPlayerInfos().map { tab.getNameForDisplay(it).string }
            }
            val sent = context.computeOnClient<List<String>, Nothing> { posted.last().rows.map { it.plain } }
            check(sent == drawn) { "The mod's rows differ from the client's.\nClient: $drawn\nMod:    $sent" }

            val self = context.computeOnClient<String, Nothing> { it.player!!.gameProfile.name }
            val expected = listOf(
                "front", "     End Island (1) 1/1     ", "             Info             ", "Area: The End", " Protector: Rising (Tier 3/5, 97)",
                "Alpha", "beta", self, "zeta", "[T] teamed", "ghost",
            )
            check(sent == expected) { "Expected $expected, got $sent" }

            val count = posted.size
            context.waitTicks(20)
            check(posted.size == count) { "The tab list did not change, but ${posted.size - count} more were posted" }
            check(context.readout("protector-status") == listOf("Stage: Rising 3/5", "Kills Left: 97")) { "Got ${context.readout("protector-status")}" }
            context.takeScreenshot("protector-status")

            protector.display = " Protector: Spawned"
            world.server.runOnServer<Nothing> { it.playerList.broadcastAll(ClientboundPlayerInfoUpdatePacket(Action.UPDATE_DISPLAY_NAME, protector)) }
            world.eggSpawns()
            world.server.runCommand("""tellraw @a "☠ The Endstone Protector has emerged from the ground! (Tier 5/5)"""")
            context.waitTicks(20)
            checkStage(context, "Spawned", "Alive: 0:")
            check(context.readout(DRAGON_TIMER) == listOf("Eyes: 0/8")) { "Got ${context.readout(DRAGON_TIMER)}" }

            world.server.runCommand("""tellraw @a "The Endstone Protector has been defeated!"""")
            context.waitTicks(5)
            checkStage(context, "Defeated", "Fight: 0:")

            area.display = "Area: Village"
            world.server.runOnServer<Nothing> { it.playerList.broadcastAll(ClientboundPlayerInfoUpdatePacket(Action.UPDATE_DISPLAY_NAME, area)) }
            context.waitTicks(20)
            check(context.readout("protector-status").isEmpty()) { "Got ${context.readout("protector-status")}" }
            check(context.readout(DRAGON_TIMER).isEmpty()) { "Got ${context.readout(DRAGON_TIMER)}" }
            context.runOnClient<Nothing> { DsModClient.game.tap = null }
        }
    }
}
