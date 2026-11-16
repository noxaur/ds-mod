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

/**
 * A server lists fake players the way dragsim.co builds its tab list, and the real client sorts and draws them.
 * The mod's rows must match the order of the client's own private method, and the readouts must match the rows.
 */
class TabListTest : FabricClientGameTest {
    /** A tab list entry that is not a player. It borrows a real connection, because the packet reads the latency from it. */
    private class Listed(server: MinecraftServer, name: String, var display: String?, private val order: Int = 0, private val mode: GameType = GameType.SURVIVAL) :
        ServerPlayer(server, server.overworld(), GameProfile(UUID.randomUUID(), name), ClientInformation.createDefault()) {
        init {
            connection = server.playerList.players.first().connection
        }

        override fun getTabListDisplayName(): Component? = display?.let(Component::literal)

        override fun getTabListOrder() = order

        override fun gameMode() = mode
    }

    private fun readout(context: ClientGameTestContext, id: String) = context.computeOnClient<List<String>, Nothing> {
        DsModClient.game.readouts.single { it.feature.id == id }.hud?.rows?.map { row -> row.text.plain } ?: emptyList()
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

            // The client's own order, from the private method that draws the list.
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
            check(readout(context, "protector-status") == listOf("Stage: Rising 3/5", "Kills Left: 97")) { "Got ${readout(context, "protector-status")}" }
            context.takeScreenshot("protector-status")

            protector.display = " Protector: Spawned"
            world.server.runOnServer<Nothing> { it.playerList.broadcastAll(ClientboundPlayerInfoUpdatePacket(Action.UPDATE_DISPLAY_NAME, protector)) }
            world.server.runCommand("""tellraw @a "§5✦ §dThe Egg has Spawned!"""")
            world.server.runCommand("""tellraw @a "☠ The Endstone Protector has emerged from the ground! (Tier 5/5)"""")
            context.waitTicks(20)
            check(readout(context, "protector-status").let { it.first() == "Stage: Spawned" && it.last().startsWith("Alive: 0:") }) { "Got ${readout(context, "protector-status")}" }
            check(readout(context, "dragon-timer") == listOf("Eyes: 0/8")) { "Got ${readout(context, "dragon-timer")}" }

            world.server.runCommand("""tellraw @a "The Endstone Protector has been defeated!"""")
            context.waitTicks(5)
            check(readout(context, "protector-status").let { it.first() == "Stage: Defeated" && it.last().startsWith("Fight: 0:") }) { "Got ${readout(context, "protector-status")}" }

            // Outside the End neither feature shows anything, even with the Protector row still listed.
            area.display = "Area: Village"
            world.server.runOnServer<Nothing> { it.playerList.broadcastAll(ClientboundPlayerInfoUpdatePacket(Action.UPDATE_DISPLAY_NAME, area)) }
            context.waitTicks(20)
            check(readout(context, "protector-status").isEmpty()) { "Got ${readout(context, "protector-status")}" }
            check(readout(context, "dragon-timer").isEmpty()) { "Got ${readout(context, "dragon-timer")}" }
            context.runOnClient<Nothing> { DsModClient.game.tap = null }
        }
    }
}
