package io.github.noxaur.dsmod.gametest

import io.github.noxaur.dsmod.core.game.Metrics
import io.github.noxaur.dsmod.edge.DsModClient
import io.github.noxaur.dsmod.edge.capture.Recorder
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext
import net.minecraft.core.component.DataComponents
import net.minecraft.network.chat.Component
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.minecraft.world.item.component.ItemLore
import java.nio.file.Files
import java.util.Locale

private const val ENTITIES = 40
private const val WINDOWS = 12
private const val WINDOW_TICKS = 20

/**
 * Measures what a recording costs the client thread where it reads items: a full inventory of named items with
 * long lore, and [ENTITIES] armored mobs in view. The numbers are the recorder's own [Metrics] timers, printed
 * for a reader. It gates nothing, because the recorder has no cap in `perf-budgets.json`.
 */
class RecorderCostTest : FabricClientGameTest {
    override fun runTest(context: ClientGameTestContext) {
        context.worldBuilder().create().use { world ->
            world.server.runOnServer<Nothing> { server ->
                val inventory = server.playerList.players.first().inventory
                for (slot in 0 until inventory.containerSize) {
                    inventory.setItem(slot, ItemStack(Items.DIAMOND_SWORD).also { stack ->
                        stack.set(DataComponents.CUSTOM_NAME, Component.literal("Sword $slot"))
                        stack.set(DataComponents.LORE, ItemLore((1..20).map { Component.literal("Line $it of the lore of sword $slot") }))
                    })
                }
            }
            repeat(ENTITIES) { i ->
                world.server.runCommand(
                    "execute at @a run summon zombie ~${i % 8 - 4} ~ ~${3 + i / 8} {NoAI:1b,Silent:1b,PersistenceRequired:1b,CustomName:\"Mob $i\"," +
                        "equipment:{head:{id:\"minecraft:diamond_helmet\"},chest:{id:\"minecraft:diamond_chestplate\"}," +
                        "legs:{id:\"minecraft:diamond_leggings\"},feet:{id:\"minecraft:diamond_boots\"},mainhand:{id:\"minecraft:diamond_sword\"}}}",
                )
            }
            context.waitTicks(40)
            val dir = context.computeOnClient<java.nio.file.Path, Nothing> { it.gameDirectory.toPath().resolve("dsmod/captures") }
            val file = context.computeOnClient<java.nio.file.Path, Nothing> { Recorder.start(dir) }
            context.waitTicks(40)

            val samples = HashMap<String, MutableList<Metrics.Timer>>()
            repeat(WINDOWS) {
                context.runOnClient<Nothing> { DsModClient.game.metrics.flush() }
                context.waitTicks(WINDOW_TICKS)
                val timers = context.computeOnClient<Map<String, Metrics.Timer>, Nothing> { DsModClient.game.metrics.snapshot().timers }
                for (name in listOf("capture-items", "capture-entities")) samples.getOrPut(name, ::ArrayList) += timers.getValue(name)
            }
            val seen = context.computeOnClient<Int, Nothing> { client -> client.level!!.entitiesForRendering().count { it.hasCustomName() } }
            context.runOnClient<Nothing> { Recorder.stop()!!.also { session -> Thread { session.close() }.start() } }
            context.waitTicks(20)

            check(seen >= ENTITIES) { "Only $seen of the $ENTITIES mobs are in view" }
            val lines = Files.readAllLines(file)
            check(lines.any { "\"type\":\"entities\"" in it && "minecraft:diamond_helmet" in it }) { "The capture holds no armored mob" }
            check(lines.any { "\"type\":\"itemcomponents\"" in it }) { "The capture holds no item components" }
            println("RecorderCostTest: 46 item slots and $seen armored mobs, each pass once every 5 ticks, over $WINDOWS windows of $WINDOW_TICKS ticks")
            for ((name, windows) in samples) {
                val perPass = windows.map { it.totalNanos / 1000.0 / it.count }.sorted()
                println(
                    "RecorderCostTest: %s median %.0f µs a pass, worst window mean %.0f µs, longest pass %.0f µs".format(
                        Locale.ROOT, name, perPass[perPass.size / 2], perPass.last(), windows.maxOf { it.maxNanos } / 1000.0,
                    ),
                )
            }
        }
    }
}
