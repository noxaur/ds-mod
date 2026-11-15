package io.github.noxaur.dsmod.gametest

import io.github.noxaur.dsmod.core.container.SlotMark
import io.github.noxaur.dsmod.core.game.Metrics
import io.github.noxaur.dsmod.core.hud.HudGroup
import io.github.noxaur.dsmod.core.hud.hud
import io.github.noxaur.dsmod.core.text.Role
import io.github.noxaur.dsmod.core.text.text
import io.github.noxaur.dsmod.edge.DsModClient
import io.github.noxaur.dsmod.edge.Hud
import io.github.noxaur.dsmod.edge.Slots
import io.github.noxaur.dsmod.edge.capture.CaptureSession
import io.github.noxaur.dsmod.edge.capture.Recorder
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext
import net.minecraft.core.component.DataComponents
import net.minecraft.network.chat.Component
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.minecraft.world.item.component.ItemLore
import net.minecraft.client.gui.screens.inventory.InventoryScreen
import java.io.File
import java.lang.management.ManagementFactory
import java.util.Locale
import kotlin.io.path.Path
import kotlin.io.path.exists
import kotlin.io.path.readLines
import kotlin.io.path.writeText

private const val WARM_UP_TICKS = 60
private const val WINDOWS = 12
private const val INVENTORY_SLOTS = 36
private const val WINDOW_TICKS = 10
private const val HUD_BLOCKS = 8
private const val FIRST_MARKED_SLOT = 9
private const val MARKED_SLOTS = 36
private const val SIDEBAR_LINES = 15
private const val ARMORED_MOBS = 40
private const val RECORDING_WARM_UP_TICKS = 100

/**
 * Measures the mod in a real game and checks each row against `perf-budgets.json`. The numbers are the mod's
 * own [Metrics] timers, read over [WINDOWS] windows of [WINDOW_TICKS] ticks while the HUD shows [HUD_BLOCKS]
 * blocks, the open inventory has [MARKED_SLOTS] marked slots and the sidebar has [SIDEBAR_LINES] lines. Then it
 * records, with a full inventory of named items with long lore and [ARMORED_MOBS] named, armored mobs in view, and
 * reads the recorder's own timers for a pass over the items and over the entities.
 *
 * The caps are for the device in the budgets file. A fixed loop measures how fast this machine is next to
 * that device, and each time is scaled to it before the check, so a slower runner needs no caps of its own.
 * The table goes to `perf.md` in the run folder, and a row over its cap fails the test.
 */
class PerfTest : FabricClientGameTest {
    @Serializable
    private class Device(val name: String, val calibrate: Double)

    @Serializable
    private class Row(val group: String, val label: String, val unit: String, val cap: Double, val gate: String = "median")

    @Serializable
    private class Budgets(val device: Device, val rows: Map<String, Row>)

    override fun runTest(context: ClientGameTestContext) {
        val budgets = Json.decodeFromString<Budgets>(checkNotNull(javaClass.getResource("/perf-budgets.json")).readText())
        val samples = HashMap<String, MutableList<Double>>()

        context.worldBuilder().create().use { world ->
            // A full sidebar, each line in a team's prefix as dragsim.co sends it.
            world.server.runCommand("""scoreboard objectives add perf dummy "DragSim"""")
            world.server.runCommand("scoreboard objectives setdisplay sidebar perf")
            repeat(SIDEBAR_LINES) { line ->
                world.server.runCommand("team add line$line")
                world.server.runCommand("""team modify line$line prefix "Purse: 527,042,$line"""")
                world.server.runCommand("team join line$line §$line")
                world.server.runCommand("scoreboard players set §$line perf $line")
            }
            // A full inventory of the server's items, read by item sightings while the player reports what is unseen.
            repeat(INVENTORY_SLOTS) { slot ->
                world.server.runCommand(
                    """item replace entity @p container.$slot with diamond_sword[custom_data={item_data:{item_id:"perf_$slot",rarity:"LEGENDARY"}},""" +
                        """custom_name='"Perf Sword $slot"',lore=['"Damage: +$slot"','"LEGENDARY SWORD"']]""",
                )
            }
            context.runOnClient<Nothing> { DsModClient.settings.contribute.reportUnknowns = true }
            context.waitTicks(20)
            context.runOnClient<Nothing> {
                val group = HudGroup("perf", 6, 6, background = true)
                repeat(HUD_BLOCKS) { block ->
                    Hud.show("perf-$block", group, hud("Tracker $block") {
                        row("Sold") { value(1204) }
                        row("Rate") { value(310); muted("/h") }
                        bar("Storage", 0.8f, Role.WARNING) { warning("80%") }
                    })
                }
            }
            context.setScreen { InventoryScreen(context.computeOnClient<net.minecraft.world.entity.player.Player, Nothing> { it.player!! }) }
            context.runOnClient<Nothing> {
                Slots.mark("perf", (0 until MARKED_SLOTS).associate { slot ->
                    FIRST_MARKED_SLOT + slot to SlotMark(Role.GOOD, text { good("$slot") }, rarity = slot % 2 == 0)
                })
            }
            context.waitTicks(WARM_UP_TICKS)

            repeat(WINDOWS) {
                context.runOnClient<Nothing> { DsModClient.game.metrics.flush() }
                context.waitTicks(WINDOW_TICKS)
                val timers = context.computeOnClient<Map<String, Metrics.Timer>, Nothing> { DsModClient.game.metrics.snapshot().timers }
                check(timers.keys.none { it.startsWith("capture-") }) { "The recorder ran before anything started it: ${timers.keys}" }
                var features = 0L
                for ((name, timer) in timers) {
                    if (name in budgets.rows) samples.getOrPut(name, ::ArrayList) += timer.totalNanos / 1000.0 / timer.count
                    else features += timer.totalNanos
                }
                samples.getOrPut("features", ::ArrayList) += features / 1000.0 / WINDOW_TICKS
            }
            recording(context, world, samples)
            val calibrate = context.computeOnClient<Double, Nothing> {
                repeat(3) { spin() }
                List(7) { spin() }.sorted()[3]
            }
            context.runOnClient<Nothing> {
                it.gui.setScreen(null)
                repeat(HUD_BLOCKS) { block -> Hud.hide("perf-$block") }
                DsModClient.settings.contribute.reportUnknowns = false
            }
            System.getProperty("dsmod.jar")?.let { samples["jar"] = mutableListOf(File(it).length() / 1024.0) }

            val speed = budgets.device.calibrate / calibrate
            val failed = ArrayList<String>()
            val tables = LinkedHashMap<String, MutableList<String>>()
            for ((name, row) in budgets.rows) {
                // The dev run has no jar to weigh.
                if (name == "jar" && name !in samples) continue
                val values = samples[name]?.map { if (row.unit == "µs") it * speed else it }
                val stats = values?.let { mapOf("median" to it.sorted()[it.size / 2], "mean" to it.average(), "max" to it.max()) }
                val ok = stats != null && stats.getValue(row.gate) <= row.cap
                if (!ok) failed += row.label
                val cells = stats?.entries?.joinToString(" | ") { (key, value) -> if (ok || key != row.gate) number(value) else "**${number(value)}**" }
                    ?: "| | **none**"
                tables.getOrPut(row.group, ::ArrayList) +=
                    "| ${row.label} | $cells | ≤ ${number(row.cap)} ${row.unit} (${row.gate}) | ${if (ok) "✅" else "❌"} |"
            }
            val note = "(as a ${budgets.device.name} · ${machine()} · speed ${"%.2f".format(Locale.ROOT, speed)}×)"
            val report = buildString {
                appendLine(if (failed.isEmpty()) "### ✅ Performance $note" else "### ❌ Performance: ${failed.size} over the cap $note")
                for ((group, rows) in tables) {
                    appendLine()
                    appendLine("| $group | Median | Mean | Max | Cap | |")
                    appendLine("|:--|:--|:--|:--|:--|:--|")
                    rows.forEach(::appendLine)
                }
            }
            Path("perf.md").writeText(report)
            println(report)
            check(failed.isEmpty()) {
                "Over the cap: ${failed.joinToString()}. The table is in perf.md. Make the code faster, or raise the cap in perf-budgets.json if the user agrees."
            }
        }
    }

    /** Adds a sample for each `capture-` timer a pass ran under, per pass. A pass runs on one tick in five. */
    private fun recording(context: ClientGameTestContext, world: TestSingleplayerContext, samples: HashMap<String, MutableList<Double>>) {
        world.server.runOnServer<Nothing> { server ->
            val inventory = server.playerList.players.first().inventory
            for (slot in 0 until inventory.containerSize) {
                inventory.setItem(slot, ItemStack(Items.DIAMOND_SWORD).also { stack ->
                    stack.set(DataComponents.CUSTOM_NAME, Component.literal("Sword $slot"))
                    stack.set(DataComponents.LORE, ItemLore((1..20).map { Component.literal("Line $it of the lore of sword $slot") }))
                })
            }
        }
        repeat(ARMORED_MOBS) { i ->
            world.server.runCommand(
                "execute at @a run summon zombie ~${i % 8 - 4} ~ ~${3 + i / 8} {NoAI:1b,Silent:1b,PersistenceRequired:1b,CustomName:\"Mob $i\"," +
                    "equipment:{head:{id:\"minecraft:diamond_helmet\"},chest:{id:\"minecraft:diamond_chestplate\"}," +
                    "legs:{id:\"minecraft:diamond_leggings\"},feet:{id:\"minecraft:diamond_boots\"},mainhand:{id:\"minecraft:diamond_sword\"}}}",
            )
        }
        context.waitTicks(20)
        val seen = context.computeOnClient<Int, Nothing> { client -> client.level!!.entitiesForRendering().count { it.hasCustomName() } }
        check(seen >= ARMORED_MOBS) { "Only $seen of the $ARMORED_MOBS mobs are in view" }
        val session = context.computeOnClient<CaptureSession, Nothing> { Recorder.start(it.gameDirectory.toPath().resolve("dsmod/captures")) }
        context.waitTicks(RECORDING_WARM_UP_TICKS)
        repeat(WINDOWS) {
            context.runOnClient<Nothing> { DsModClient.game.metrics.flush() }
            context.waitTicks(WINDOW_TICKS)
            val timers = context.computeOnClient<Map<String, Metrics.Timer>, Nothing> { DsModClient.game.metrics.snapshot().timers }
            for ((name, timer) in timers) if (name.startsWith("capture-")) samples.getOrPut(name, ::ArrayList) += timer.totalNanos / 1000.0 / timer.count
        }
        context.runOnClient<Nothing> { Recorder.stop(session) }
    }

    /** A fixed amount of work for one core, in ms. */
    private fun spin(): Double {
        val start = System.nanoTime()
        var x = 88172645463325252L
        var sum = 0L
        repeat(40_000_000) {
            x = x xor (x shl 13)
            x = x xor (x ushr 7)
            x = x xor (x shl 17)
            sum += x
        }
        check(sum != 0L)
        return (System.nanoTime() - start) / 1e6
    }

    private fun machine(): String {
        val cpuinfo = Path("/proc/cpuinfo")
        val cpu = if (cpuinfo.exists()) cpuinfo.readLines().firstOrNull { it.startsWith("model name") }?.substringAfter(": ") else null
        val os = ManagementFactory.getOperatingSystemMXBean() as com.sun.management.OperatingSystemMXBean
        return "${cpu ?: System.getProperty("os.arch")}, ${os.availableProcessors} cores, ${os.totalMemorySize shr 30} GB"
    }

    private fun number(value: Double) = when {
        value == Math.floor(value) || value >= 100 -> "%,.0f".format(Locale.ROOT, value)
        value >= 1 -> "%.2f".format(Locale.ROOT, value)
        else -> "%.3f".format(Locale.ROOT, value)
    }
}
