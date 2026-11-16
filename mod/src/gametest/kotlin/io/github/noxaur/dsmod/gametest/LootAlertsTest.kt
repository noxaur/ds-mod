package io.github.noxaur.dsmod.gametest

import io.github.noxaur.dsmod.edge.mixin.ChatComponentAccessor
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
            val shown = context.computeOnClient<Pair<String?, List<String>>, Nothing> { client ->
                val title = client.gui.hud.title?.string
                val lines = (client.gui.hud.chat as ChatComponentAccessor).allMessages.asReversed().map { it.content().string }
                title to lines
            }
            check(shown.first == "Golden Eye") { "The title was ${shown.first}" }
            val epic = shown.second.indexOfLast { "EPIC DROP!" in it && "Golden Eye" in it }
            check(epic >= 0) { "The server's Golden Eye line was not in chat: ${shown.second}" }
            val under = shown.second.getOrNull(epic + 1)
            check(under == "[ds-mod] Loot Alerts | Golden Eye dropped.") { "The mod line under the drop was $under" }
            context.takeScreenshot("loot-alerts")
        }
    }
}
