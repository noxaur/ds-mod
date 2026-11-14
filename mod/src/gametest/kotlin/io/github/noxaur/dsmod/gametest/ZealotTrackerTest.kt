package io.github.noxaur.dsmod.gametest

import io.github.noxaur.dsmod.edge.DsModClient
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext
import net.fabricmc.loader.api.FabricLoader
import kotlin.io.path.createDirectories
import kotlin.io.path.exists
import kotlin.io.path.readText
import kotlin.io.path.writeText

/** The real edge loads the saved file in a running game, counts a drop the server sends, and writes it back. */
class ZealotTrackerTest : FabricClientGameTest {
    private val file = FabricLoader.getInstance().configDir.resolve("dsmod/data/zealot-tracker.json")

    override fun runTest(context: ClientGameTestContext) {
        file.parent.createDirectories()
        file.writeText("""{"drops":{"Summoning Eye":7}}""")
        context.runOnClient<Nothing> { DsModClient.saved.load() }

        context.worldBuilder().create().use { world ->
            world.server.runCommand("""tellraw @a "§6§lRARE DROP! §7(§5Summoning Eye§7) (§b✯ 90%§7)"""")
            context.waitTicks(5)
            context.runOnClient<Nothing> { DsModClient.saved.flush() }
            repeat(100) { if (file.readText() != """{"drops":{"Summoning Eye":7}}""") return@repeat else context.waitTicks(1) }
            val written = file.readText()
            check(written == """{"drops":{"Summoning Eye":8}}""") { "Expected 7 loaded and 1 counted, the file says $written" }
            check(!file.resolveSibling("zealot-tracker.json.tmp").exists()) { "The temporary file was left behind" }
        }
    }
}
