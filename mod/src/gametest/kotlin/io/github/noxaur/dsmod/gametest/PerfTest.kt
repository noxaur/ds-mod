package io.github.noxaur.dsmod.gametest

import io.github.noxaur.dsmod.core.container.SlotMark
import io.github.noxaur.dsmod.core.feature.boss.BossBeacon
import io.github.noxaur.dsmod.core.feature.boss.BossTracer
import io.github.noxaur.dsmod.core.feature.contribute.ReportGameData
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
import net.minecraft.world.InteractionHand
import net.minecraft.world.SimpleContainer
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.minecraft.world.item.component.ItemLore
import java.io.File
import java.util.Locale
import kotlin.io.path.Path
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
private const val MENU_SLOTS = 54
private const val JAR = "jar"

/** @keep Covers [io.github.noxaur.dsmod.edge.capture.BlockScan], [io.github.noxaur.dsmod.edge.mixin.CaptureInputMixin], [io.github.noxaur.dsmod.edge.mixin.HudHotbarMixin]. */
class PerfTest : FabricClientGameTest {
    @Serializable
    private class Row(val label: String, val cap: Double)

    @Serializable
    private class Budgets(val calibrate: Double, val rows: Map<String, Row>)

    private lateinit var budgets: Budgets
    private val samples = HashMap<String, MutableList<Double>>()

    override fun runTest(context: ClientGameTestContext) {
        budgets = Json.decodeFromString(checkNotNull(javaClass.getResource("/perf-budgets.json")).readText())

        context.worldBuilder().create().use { world ->
            world.server.runCommand("""scoreboard objectives add perf dummy "DragSim"""")
            world.server.runCommand("scoreboard objectives setdisplay sidebar perf")
            repeat(SIDEBAR_LINES) { line ->
                world.server.runCommand("team add line$line")
                world.server.runCommand("""team modify line$line prefix "Purse: 527,042,$line"""")
                world.server.runCommand("team join line$line §$line")
                world.server.runCommand("scoreboard players set §$line perf $line")
            }
            repeat(INVENTORY_SLOTS) { slot ->
                world.server.runCommand(
                    """item replace entity @p container.$slot with diamond_sword[custom_data={item_data:{item_id:"perf_$slot",rarity:"LEGENDARY"}},""" +
                        """custom_name='"Perf Sword $slot"',lore=['"Damage: +$slot"','"LEGENDARY SWORD"']]""",
                )
            }
            context.runOnClient<Nothing> { DsModClient.feature<ReportGameData>().feature.enabled = true }
            context.waitTicks(20)
            context.runOnClient<Nothing> {
                val group = HudGroup("perf", 6, 6, background = true)
                repeat(HUD_BLOCKS) { block ->
                    Hud.show("perf-$block", group, hud("Tracker $block") {
                        scaleRows()
                    })
                }
            }
            context.openInventory()
            context.runOnClient<Nothing> {
                Slots.mark("perf", (0 until MARKED_SLOTS).associate { slot ->
                    FIRST_MARKED_SLOT + slot to SlotMark(Role.UNCOMMON, text { good("$slot") }, rarity = slot % 2 == 0)
                })
            }
            context.waitTicks(WARM_UP_TICKS)
            windows(context, "inventory") { context.waitTicks(WINDOW_TICKS) }
            etherwarp(context, world)
            recording(context, world)
            bosses(context, world)
            menuBurst(context, world)
            val calibrate = context.computeOnClient<Double, Nothing> {
                repeat(3) { spin() }
                List(7) { spin() }.sorted()[3]
            }
            context.runOnClient<Nothing> {
                it.gui.setScreen(null)
                repeat(HUD_BLOCKS) { block -> Hud.hide("perf-$block") }
                DsModClient.feature<ReportGameData>().feature.enabled = false
            }
            System.getProperty("dsmod.jar")?.let { samples[JAR] = mutableListOf(File(it).length() / 1024.0) }
            report(budgets.calibrate / calibrate)
        }
    }

    private fun report(speed: Double) {
        val failed = ArrayList<String>()
        val tables = LinkedHashMap<String, MutableList<String>>()
        for ((name, row) in budgets.rows) {
            if (name == JAR && name !in samples) continue
            val unit = if (name == JAR) "KB" else "µs"
            val values = samples[name]?.sorted()?.map { if (name == JAR) it else it * speed }
            val median = values?.get(values.size / 2)
            val max = values?.last()
            val ok = median != null && median <= row.cap
            if (!ok) failed += row.label
            val cells = if (median == null || max == null) "**none** | " else "${if (ok) number(median) else "**${number(median)}**"} | ${number(max)}"
            val group = if (name == JAR) "Size" else name.substringAfter('.').replaceFirstChar(Char::uppercase)
            tables.getOrPut(group, ::ArrayList) +=
                "| ${row.label} | $cells | ≤ ${number(row.cap)} $unit | ${if (ok) "✅" else "❌"} |"
        }
        val note = "(scaled to the reference machine, which this one runs at ${"%.2f".format(Locale.ROOT, speed)}×)"
        val report = buildString {
            appendLine(if (failed.isEmpty()) "### ✅ Performance $note" else "### ❌ Performance: ${failed.size} over the cap $note")
            for ((group, rows) in tables) {
                appendLine()
                appendLine("| $group | Median | Max | Cap | |")
                appendLine("|:--|:--|:--|:--|:--|")
                rows.forEach(::appendLine)
            }
        }
        Path("perf.md").writeText(report)
        println(report)
        check(failed.isEmpty()) {
            "Over the cap: ${failed.joinToString()}. The table is in perf.md. Make the code faster, or raise the cap in perf-budgets.json if the user agrees."
        }
    }

    private fun windows(context: ClientGameTestContext, scenario: String, window: (Int) -> Unit) {
        repeat(WINDOWS) { at ->
            val start = context.computeOnClient<Long, Nothing> {
                DsModClient.game.metrics.flush()
                DrawnFrames.count
            }
            window(at)
            val (timers, frames) = context.computeOnClient<Pair<Map<String, Metrics.Timer>, Long>, Nothing> {
                DsModClient.game.metrics.snapshot().timers to DrawnFrames.count - start
            }
            val per = mapOf(Metrics.TICK to WINDOW_TICKS.toLong(), Metrics.FRAME to frames, Metrics.PACKETS to 1L)
            for ((boundary, count) in per) {
                val name = "$scenario.$boundary"
                if (name !in budgets.rows) continue
                val timer = checkNotNull(timers[boundary]) { "${budgets.rows.getValue(name).label}: the mod did no $boundary work" }
                samples.getOrPut(name, ::ArrayList) += timer.totalNanos / 1000.0 / count
            }
        }
    }

    private fun etherwarp(context: ClientGameTestContext, world: TestSingleplayerContext) {
        context.runOnClient<Nothing> { it.gui.setScreen(null) }
        world.server.runOnServer<Nothing> { server ->
            server.player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack(Items.DIAMOND_SHOVEL).also { stack ->
                stack.set(DataComponents.LORE, ItemLore(listOf("Etherwarp", "looking at, up to 57 blocks away.").map(Component::literal)))
            })
        }
        context.input.holdKey { it.keyShift }
        context.input.lookAt(0f, 0f)
        context.waitTicks(20)
        try {
            windows(context, "etherwarp") { window ->
                repeat(WINDOW_TICKS) { tick ->
                    context.input.lookAt((window * WINDOW_TICKS + tick).toFloat(), 0f)
                    context.waitTick()
                }
            }
        } finally {
            context.input.releaseKey { it.keyShift }
        }
    }

    private fun recording(context: ClientGameTestContext, world: TestSingleplayerContext) {
        world.give(
            (0 until world.server.computeOnServer<Int, Nothing> { it.player.inventory.containerSize }).associateWith { slot ->
                ServerMenus.lored(Items.DIAMOND_SWORD, "Sword $slot", (1..20).map { "Line $it of the lore of sword $slot" })
            },
        )
        repeat(ARMORED_MOBS) { i ->
            world.server.runCommand(
                "execute at @a run summon zombie ~${i % 8 - 4} ~ ~${3 + i / 8} {NoAI:1b,Silent:1b,PersistenceRequired:1b,Tags:[\"perf\"],CustomName:\"Mob $i\"," +
                    "equipment:{head:{id:\"minecraft:diamond_helmet\"},chest:{id:\"minecraft:diamond_chestplate\"}," +
                    "legs:{id:\"minecraft:diamond_leggings\"},feet:{id:\"minecraft:diamond_boots\"},mainhand:{id:\"minecraft:diamond_sword\"}}}",
            )
        }
        context.waitTicks(20)
        val seen = context.computeOnClient<Int, Nothing> { client -> client.level!!.entitiesForRendering().count { it.hasCustomName() } }
        check(seen >= ARMORED_MOBS) { "Only $seen of the $ARMORED_MOBS mobs are in view" }
        val session = context.computeOnClient<CaptureSession, Nothing> { Recorder.start(it.gameDirectory.toPath().resolve("dsmod/captures")) }
        context.waitTicks(RECORDING_WARM_UP_TICKS)
        windows(context, "recording") { context.waitTicks(WINDOW_TICKS) }
        context.runOnClient<Nothing> { Recorder.stop(session) }
    }

    private fun bosses(context: ClientGameTestContext, world: TestSingleplayerContext) {
        world.server.runCommand("""execute at @a run summon iron_golem ~ ~ ~-12 {NoAI:1b,CustomName:"[Lv100] Endstone Protector 10M/10M"}""")
        world.server.runCommand("""execute at @a run summon zombie ~12 ~ ~ {NoAI:1b,CustomName:"☠ Revenant Horror I 3.8M/3.8M❤"}""")
        world.server.runCommand("""execute at @a run summon zombie ~-12 ~ ~ {NoAI:1b,CustomName:"☠ Atoned Horror V 50M/50M❤"}""")
        context.runOnClient<Nothing> {
            val beacon = DsModClient.feature<BossBeacon>()
            val tracer = DsModClient.feature<BossTracer>()
            for (feature in listOf(beacon.feature, tracer.feature)) feature.enabled = true
            for (bosses in listOf(beacon.bosses, tracer.bosses)) bosses.otherSlayers.value = true
        }
        val fight = { at: Int ->
            world.server.runCommand("""execute as @e[tag=perf] run data modify entity @s CustomName set value "[Lv70] Zealot ${13_000 - at}/13k❤"""")
            context.input.lookAt(at * 3f, 0f)
        }
        repeat(RECORDING_WARM_UP_TICKS) { at ->
            fight(-at)
            context.waitTick()
        }
        windows(context, "bosses") { window ->
            repeat(WINDOW_TICKS) { tick ->
                fight(window * WINDOW_TICKS + tick)
                context.waitTick()
            }
        }
    }

    private fun menuBurst(context: ClientGameTestContext, world: TestSingleplayerContext) {
        val chest = SimpleContainer(MENU_SLOTS)
        val fill = { page: Int ->
            for (slot in 0 until MENU_SLOTS) {
                chest.setItem(slot, ServerMenus.lored(Items.DIAMOND_SWORD, "Page $page Sword $slot", listOf("Damage: +$slot", "LEGENDARY SWORD")))
            }
        }
        world.server.runOnServer<Nothing> { server ->
            fill(0)
            server.player.openMenu(ServerMenus.sixRows(chest, "Perf Menu"))
        }
        context.waitTicks(20)
        windows(context, "menu") { window ->
            world.server.runOnServer<Nothing> { fill(window + 1) }
            context.waitTicks(WINDOW_TICKS)
        }
        context.runOnClient<Nothing> { it.player!!.closeContainer() }
    }

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

    private fun number(value: Double) = when {
        value == Math.floor(value) || value >= 100 -> "%,.0f".format(Locale.ROOT, value)
        value >= 1 -> "%.2f".format(Locale.ROOT, value)
        else -> "%.3f".format(Locale.ROOT, value)
    }
}
