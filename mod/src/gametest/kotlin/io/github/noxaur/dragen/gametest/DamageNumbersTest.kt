package io.github.noxaur.dragen.gametest

import io.github.noxaur.dragen.core.feature.combat.damagenumbers.DamageNumbers
import io.github.noxaur.dragen.core.feature.combat.damagenumbers.DamageShown
import io.github.noxaur.dragen.edge.DragenClient
import io.github.noxaur.dragen.edge.EditedText
import io.github.noxaur.dragen.edge.FloatingTexts
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext
import net.minecraft.network.chat.Component
import net.minecraft.network.syncher.EntityDataAccessor
import net.minecraft.world.entity.Display

class DamageNumbersTest : FabricClientGameTest {
    private fun shown(context: ClientGameTestContext) = context.computeOnClient<Map<String, String?>, Nothing> { client ->
        client.level!!.entitiesForRendering().filterIsInstance<Display.TextDisplay>().associate { display ->
            val edit = (display as EditedText).`dragen$edit`()
            display.entityData.get(TEXT).string.replace(Regex("§."), "") to if (edit === FloatingTexts.HIDDEN) HIDDEN else edit?.string
        }
    }

    private fun float(context: ClientGameTestContext, world: TestSingleplayerContext, name: String): Map<String, String?> {
        world.server.runCommand("kill @e[type=minecraft:text_display]")
        world.server.runCommand("""execute at @p run summon text_display ^-0.6 ^1.6 ^2 {billboard:"center",text:"§774,540,375"}""")
        world.server.runCommand("""execute at @p run summon text_display ^0.6 ^1.6 ^2 {billboard:"center",text:"§f✧§f1§f,§e4§66§c1§c,§68§e4§f5§f✧"}""")
        context.waitTicks(5)
        context.takeScreenshot(name)
        return shown(context)
    }

    override fun runTest(context: ClientGameTestContext) {
        val damageNumbers = DragenClient.feature<DamageNumbers>()
        context.worldBuilder().create().use { world ->
            context.waitTicks(20)
            try {
                val off = float(context, world, "damage-numbers-off")
                check(off == mapOf("74,540,375" to null, "✧1,461,845✧" to null)) { "The switch was off, yet the numbers changed: $off" }
                damageNumbers.feature.enabled = true
                val short = float(context, world, "damage-numbers-short")
                check(short == mapOf("74,540,375" to "74.5M", "✧1,461,845✧" to "✧1.5M✧")) { "The numbers did not shorten: $short" }
                damageNumbers.shown.value = DamageShown.CRITS
                val crits = float(context, world, "damage-numbers-crits")
                check(crits == mapOf("74,540,375" to HIDDEN, "✧1,461,845✧" to "✧1.5M✧")) { "Crits Only did not keep only the crit: $crits" }
                damageNumbers.shown.value = DamageShown.NONE
                val none = float(context, world, "damage-numbers-none")
                check(none == mapOf("74,540,375" to HIDDEN, "✧1,461,845✧" to HIDDEN)) { "None left a number: $none" }
            } finally {
                damageNumbers.feature.enabled = false
                damageNumbers.shown.value = DamageShown.ALL
            }
        }
    }

    private companion object {
        const val HIDDEN = "(hidden)"

        @Suppress("UNCHECKED_CAST")
        val TEXT = Display.TextDisplay::class.java.getDeclaredField("DATA_TEXT_ID").apply { isAccessible = true }.get(null)
            as EntityDataAccessor<Component>
    }
}
