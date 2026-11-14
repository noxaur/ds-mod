package io.github.noxaur.dsmod.gametest

import io.github.noxaur.dsmod.edge.DsModClient
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext

/**
 * The server sends a Rare and an Epic drop, and the edge delivers what Loot Alerts says. Read the screenshot:
 * the title must name the Golden Eye alone, and the mod's chat line must sit under the server's drop line.
 */
class LootAlertsTest : FabricClientGameTest {
    override fun runTest(context: ClientGameTestContext) {
        context.worldBuilder().create().use { world ->
            world.server.runCommand("""tellraw @a "§6§lRARE DROP! §7(§5Summoning Eye§7) (§b✯ 90%§7)"""")
            world.server.runCommand("""tellraw @a "§5§lEPIC DROP! §6Golden Eye"""")
            context.waitTicks(15)
            val waiting = context.computeOnClient<Int, Nothing> { DsModClient.game.messages.size }
            check(waiting == 0) { "$waiting messages were never delivered" }
            context.takeScreenshot("loot-alerts")
        }
    }
}
