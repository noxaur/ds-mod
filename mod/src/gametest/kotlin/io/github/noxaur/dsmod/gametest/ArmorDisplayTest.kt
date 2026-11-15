package io.github.noxaur.dsmod.gametest

import io.github.noxaur.dsmod.core.feature.general.ArmorLayout
import io.github.noxaur.dsmod.edge.DsModClient
import io.github.noxaur.dsmod.edge.Hud
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext
import net.minecraft.client.gui.screens.ChatScreen
import net.minecraft.core.component.DataComponents
import net.minecraft.world.item.Items

/**
 * The worn armor shows on the HUD as the very stacks the player wears, so dyed leather keeps its color, and the chat's
 * free mouse reads a piece's tooltip. Read the screenshots: the row with a gap for the missing leggings, the tooltip
 * over the chestplate, the column, and Active Pet under the armor, first by name and then by the head of the pet
 * floating beside the player.
 */
class ArmorDisplayTest : FabricClientGameTest {
    override fun runTest(context: ClientGameTestContext) {
        context.worldBuilder().create().use { world ->
            world.server.runCommand("item replace entity @a armor.head with minecraft:diamond_helmet")
            world.server.runCommand("item replace entity @a armor.chest with minecraft:leather_chestplate[dyed_color=$DYE]")
            world.server.runCommand("item replace entity @a armor.feet with minecraft:leather_boots[dyed_color=$DYE]")
            context.waitTicks(20)

            /** The middle of armor piece [index] in GUI pixels, counted along the row or down the column. */
            fun piece(index: Int, column: Boolean = false): Pair<Double, Double> = context.computeOnClient<Pair<Double, Double>, Nothing> {
                val block = checkNotNull(Hud.find("armor-display")) { "The armor is not on the HUD" }
                val along = (4 + index * 17 + 8) * block.scale
                val across = (4 + 8) * block.scale
                if (column) block.x + across.toDouble() to block.y + along.toDouble() else block.x + along.toDouble() to block.y + across.toDouble()
            }
            fun stackAt(point: Pair<Double, Double>) = context.computeOnClient<net.minecraft.world.item.ItemStack?, Nothing> { Hud.itemAt(point.first, point.second) }

            check(stackAt(piece(0))?.item == Items.DIAMOND_HELMET) { "The first icon is not the helmet" }
            check(stackAt(piece(1))?.get(DataComponents.DYED_COLOR)?.rgb() == DYE) { "The chestplate lost its dye, so it is not the stack the player wears" }
            check(stackAt(piece(2)) == null) { "The leggings, which the player does not wear, have an icon" }
            check(stackAt(piece(3))?.item == Items.LEATHER_BOOTS) { "The last icon is not the boots" }
            context.takeScreenshot("armor-display")

            context.runOnClient<Nothing> { it.gui.setScreen(ChatScreen("", false)) }
            context.waitForScreen(ChatScreen::class.java)
            val chest = piece(1)
            val scale = context.computeOnClient<Int, Nothing> { it.window.guiScale }
            context.input.setCursorPos(chest.first * scale, chest.second * scale)
            context.waitTicks(5)
            context.takeScreenshot("armor-display-tooltip")
            context.runOnClient<Nothing> { it.gui.setScreen(null) }

            try {
                DsModClient.settings.general.armorLayout = ArmorLayout.COLUMN
                context.waitTicks(10)
                check(stackAt(piece(3, column = true))?.item == Items.LEATHER_BOOTS) { "The boots are not at the foot of the column" }
                context.takeScreenshot("armor-display-column")
            } finally {
                DsModClient.settings.general.armorLayout = ArmorLayout.ROW
            }

            // Active Pet reads the sidebar's pet line, here an Epic pet as dragsim.co writes it, and stacks under the armor.
            listOf(
                """scoreboard objectives add ds dummy "Dragon Sim"""",
                "scoreboard objectives setdisplay sidebar ds",
                "team add ds_pet",
                """team modify ds_pet prefix {"text":"Pet: "}""",
                """team modify ds_pet suffix [{"text":"[Lvl 100] ","color":"gray"},{"text":"Ender Dragon","color":"dark_purple"}]""",
                "team join ds_pet Pet",
                "scoreboard players set Pet ds 1",
                "scoreboard players display name Pet ds \"\"",
            ).forEach(world.server::runCommand)
            context.waitTicks(20)
            check(context.computeOnClient<Boolean, Nothing> { Hud.find("active-pet") != null }) { "The pet is not on the HUD" }
            context.takeScreenshot("active-pet-name")

            // dragsim.co floats the pet beside the player as an armor stand wearing its head, named as the sidebar names it.
            world.server.runCommand(
                """execute at @a run summon armor_stand ~1 ~1 ~1 {CustomName:"[Lvl 100] Ender Dragon",Invisible:1b,NoGravity:1b,equipment:{head:{id:"minecraft:player_head",components:{"minecraft:profile":{name:"DsPet"},"minecraft:custom_name":"Pet Head"}}}}""",
            )
            context.waitTicks(30)
            val icon = context.computeOnClient<net.minecraft.world.item.ItemStack?, Nothing> {
                val block = checkNotNull(Hud.find("active-pet")) { "The pet is not on the HUD" }
                // The icon is the first row under the header.
                Hud.itemAt(block.x + (4 + 8) * block.scale.toDouble(), block.y + (4 + 10 + 8) * block.scale.toDouble())
            }
            check(icon?.get(DataComponents.PROFILE) != null) { "The pet's icon is not the head it wears: $icon" }
            context.takeScreenshot("active-pet")

            for (slot in listOf("head", "chest", "feet")) world.server.runCommand("item replace entity @a armor.$slot with minecraft:air")
            context.waitTicks(20)
            check(context.computeOnClient<Boolean, Nothing> { Hud.find("armor-display") == null }) { "The HUD still shows armor after it all came off" }
        }
    }

    private companion object {
        const val DYE = 0x3366CC
    }
}
