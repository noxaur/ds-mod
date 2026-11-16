package io.github.noxaur.dsmod.gametest

import io.github.noxaur.dsmod.core.feature.general.DamageShown
import io.github.noxaur.dsmod.edge.DsModClient
import io.github.noxaur.dsmod.edge.EditedText
import io.github.noxaur.dsmod.edge.FloatingTexts
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext
import net.minecraft.network.chat.Component
import net.minecraft.network.syncher.EntityDataAccessor
import net.minecraft.world.entity.Display

/**
 * The server floats a hit and a crit as dragsim.co does, as text displays with `§` codes, once for each setting. The
 * check reads what each display shows on the client, and the screenshots show it. Then a number that stays up past two
 * seconds gets its own look back, and Boss Fights Only waits for a slayer boss. Read all six.
 */
class DamageNumbersTest : FabricClientGameTest {
    /** What each display on the client shows in place of the server's text, by the server's text: null for its own. */
    private fun shown(context: ClientGameTestContext) = context.computeOnClient<Map<String, String?>, Nothing> { client ->
        client.level!!.entitiesForRendering().filterIsInstance<Display.TextDisplay>().associate { display ->
            val edit = (display as EditedText).`dsmod$edit`()
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
        val general = DsModClient.settings.general
        context.worldBuilder().create().use { world ->
            context.waitTicks(20)
            try {
                val off = float(context, world, "damage-numbers-off")
                check(off == mapOf("74,540,375" to null, "✧1,461,845✧" to null)) { "The switch was off, yet the numbers changed: $off" }
                general.damageNumbers = true
                val short = float(context, world, "damage-numbers-short")
                check(short == mapOf("74,540,375" to "74.5M", "✧1,461,845✧" to "✧1.5M✧")) { "The numbers did not shorten: $short" }
                general.damageShown = DamageShown.CRITS
                val crits = float(context, world, "damage-numbers-crits")
                check(crits == mapOf("74,540,375" to HIDDEN, "✧1,461,845✧" to "✧1.5M✧")) { "Crits Only did not keep only the crit: $crits" }
                general.damageShown = DamageShown.NONE
                val none = float(context, world, "damage-numbers-none")
                check(none == mapOf("74,540,375" to HIDDEN, "✧1,461,845✧" to HIDDEN)) { "None left a number: $none" }
                // A hit's number is gone well within two seconds, so these two, still up after it, are holograms.
                context.waitTicks(HOLOGRAM_TICKS)
                context.takeScreenshot("damage-numbers-hologram")
                val stayed = shown(context)
                check(stayed == mapOf("74,540,375" to null, "✧1,461,845✧" to null)) { "A number that stayed up kept its edit: $stayed" }
                general.damageShown = DamageShown.ALL
                general.damageInFights = true
                val calm = float(context, world, "damage-numbers-no-fight")
                check(calm == mapOf("74,540,375" to null, "✧1,461,845✧" to null)) { "The numbers changed with no fight on: $calm" }
                world.server.runCommand("tellraw @a \"☠ SLAYER BOSS SPAWNING!\"")
                val fight = float(context, world, "damage-numbers-fight")
                check(fight == mapOf("74,540,375" to "74.5M", "✧1,461,845✧" to "✧1.5M✧")) { "The numbers did not change in a slayer fight: $fight" }
            } finally {
                general.damageNumbers = false
                general.damageShown = DamageShown.ALL
                general.damageInFights = false
            }
        }
    }

    private companion object {
        const val HIDDEN = "(hidden)"

        /** Past the two seconds a hit's number may last, counted from the five ticks `float` already waited. */
        const val HOLOGRAM_TICKS = 40

        @Suppress("UNCHECKED_CAST")
        val TEXT = Display.TextDisplay::class.java.getDeclaredField("DATA_TEXT_ID").apply { isAccessible = true }.get(null)
            as EntityDataAccessor<Component>
    }
}
