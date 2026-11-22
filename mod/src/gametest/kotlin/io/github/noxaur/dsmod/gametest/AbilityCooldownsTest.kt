package io.github.noxaur.dsmod.gametest

import io.github.noxaur.dsmod.core.container.SlotMark
import io.github.noxaur.dsmod.core.hud.HudBlock
import io.github.noxaur.dsmod.edge.DsModClient
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext
import net.minecraft.client.gui.screens.inventory.InventoryScreen
import org.lwjgl.glfw.GLFW

/** @keep Covers [io.github.noxaur.dsmod.edge.Hits]. */
class AbilityCooldownsTest : FabricClientGameTest {
    private fun readout(context: ClientGameTestContext) = context.computeOnClient<Pair<HudBlock?, Map<Int, SlotMark>>, Nothing> {
        val readout = DsModClient.game.readouts.single { it.feature.id == "ability-cooldowns" }
        readout.hud to readout.slots
    }

    private fun rows(context: ClientGameTestContext) = readout(context).first?.rows?.map { it.text.plain }.orEmpty()

    override fun runTest(context: ClientGameTestContext) {
        context.worldBuilder().create().use { world ->
            listOf(
                """give @a golden_sword[custom_name="Giant's Sword",lore=["Giant's Slam RIGHT CLICK","Mana Cost: 100","Cooldown: 5s"]]""",
                """give @a goat_horn[custom_name="Weird Tuba",lore=["Howl RIGHT CLICK","Mana Cost: 150","Cooldown: 20s"]]""",
            ).forEach(world.server::runCommand)
            for (piece in listOf("head" to "helmet", "chest" to "chestplate", "legs" to "leggings", "feet" to "boots")) {
                world.server.runCommand(
                    """item replace entity @a armor.${piece.first} with leather_${piece.second}[custom_name="Riftwalker ${piece.second}",""" +
                        """lore=["Tiered Bonus: Between Worlds (4/4)","(4) Every 8s your next hit blinks you"]]""",
                )
            }
            world.server.runCommand("execute as @a at @s anchored eyes positioned ^ ^ ^2 run summon husk ~ ~-1 ~ {NoAI:1b,Silent:1b}")
            context.waitTicks(20)

            world.server.runCommand("""title @a actionbar "1,000/1,000❤     -100 Mana (Giant's Slam)     ✎ Mana 900/1,000"""")
            world.server.runCommand("""tellraw @a "HOWL! You and nearby players gain +20 Strength."""")
            context.input.pressMouse(GLFW.GLFW_MOUSE_BUTTON_LEFT)
            context.waitTicks(10)

            val shown = rows(context)
            check(shown.map { it.substringBefore(':') } == listOf("Giant's Slam", "Between Worlds", "Howl")) { "The list shows $shown" }
            val marked = readout(context).second
            check(marked.keys == setOf(5, 6, 7, 8, 36, 37) && marked.values.all { it.cooldown != null }) { "Marked $marked" }
            context.takeScreenshot("ability-cooldowns")

            context.openInventory()
            context.waitForScreen(InventoryScreen::class.java)
            context.waitTicks(5)
            context.takeScreenshot("ability-cooldowns-inventory")
            context.runOnClient<Nothing> { it.gui.setScreen(null) }
        }
    }
}
