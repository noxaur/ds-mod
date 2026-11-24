package io.github.noxaur.dsmod.gametest

import io.github.noxaur.dsmod.edge.DsModClient
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import net.fabricmc.loader.api.FabricLoader
import net.minecraft.client.Minecraft
import kotlin.io.path.createDirectories
import kotlin.io.path.exists
import kotlin.io.path.readText
import kotlin.io.path.writeText

class ZealotTrackerTest : FabricClientGameTest {
    private val dir = FabricLoader.getInstance().configDir.resolve("dsmod/data")
    private val file = dir.resolve("zealot-tracker.json")
    private val player by lazy { dir.resolve("players/${Minecraft.getInstance().user.profileId}.json") }

    private fun written(): String? = if (!player.exists()) null else
        Json.parseToJsonElement(player.readText()).jsonObject["profiles"]?.jsonObject?.values?.firstNotNullOfOrNull { it.jsonObject["features"]?.jsonObject?.get("zealot-tracker") }?.toString()

    override fun runTest(context: ClientGameTestContext) {
        file.parent.createDirectories()
        file.writeText("""{"drops":{"Summoning Eye":7},"kills":0}""")
        context.runOnClient<Nothing> { DsModClient.saved.load() }

        context.worldBuilder().create().use { world ->
            context.enterTheEnd()
            world.server.runCommand("""tellraw @a "§6§lRARE DROP! §7(§5Summoning Eye§7) (§b✯ 90%§7)"""")
            listOf("Mine", "Theirs").forEach { world.server.runCommand("""execute at @a run summon pig ~ ~ ~3 {CustomName:"[Lv55] Zealot $it",NoAI:1b,Silent:1b}""") }
            context.waitTicks(20)
            world.server.runCommand("""kill @e[name="[Lv55] Zealot Mine"]""")
            world.server.runCommand("""title @a actionbar "+375.84 Combat (18.3%)"""")
            context.waitTicks(10)
            world.server.runCommand("""kill @e[name="[Lv55] Zealot Theirs"]""")
            context.waitTicks(10)
            context.runOnClient<Nothing> { DsModClient.saved.flush() }
            context.waitUntil("the drop and the kill were written", 100) { written().let { it != null && it != """{"drops":{"Summoning Eye":7},"kills":0}""" } }
            val written = written()
            check(written == """{"drops":{"Summoning Eye":8},"kills":1}""") { "Expected 7 carried over from the old file, 1 drop and 1 kill counted, the player's file says $written" }
            check(!player.resolveSibling("${player.fileName}.tmp").exists()) { "The temporary file was left behind" }
        }
    }
}
