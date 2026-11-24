package io.github.noxaur.dsmod.gametest

import io.github.noxaur.dsmod.core.feature.ui.equipmentdisplay.EquipmentDisplay
import io.github.noxaur.dsmod.core.feature.ui.equipmentdisplay.EquipmentLayout
import io.github.noxaur.dsmod.core.feature.ui.equipmentdisplay.EquipmentLook
import io.github.noxaur.dsmod.core.feature.ui.equipmentdisplay.PetPosition
import io.github.noxaur.dsmod.core.hud.HudDock
import io.github.noxaur.dsmod.edge.DsModClient
import io.github.noxaur.dsmod.edge.Hud
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext
import net.minecraft.client.gui.screens.ChatScreen
import net.minecraft.core.component.DataComponents
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items

class EquipmentDisplayTest : FabricClientGameTest {
    override fun runTest(context: ClientGameTestContext) {
        context.worldBuilder().create().use { world ->
            world.server.runCommand("item replace entity @a armor.head with ${HeadSkin.ITEM}")
            world.server.runCommand("item replace entity @a armor.chest with minecraft:leather_chestplate[dyed_color=$DYE]")
            world.server.runCommand("item replace entity @a armor.feet with minecraft:leather_boots[dyed_color=$DYE]")
            context.waitTicks(20)

            fun piece(index: Int, column: Boolean = false, hotbar: Boolean = false): Pair<Double, Double> = context.computeOnClient<Pair<Double, Double>, Nothing> {
                val block = checkNotNull(Hud.find(ID)) { "The equipment is not on the HUD" }
                val inset = if (hotbar) 3 else 0
                val along = (4 + inset + index * (if (hotbar) 20 else 17) + 8) * block.scale
                val across = (4 + inset + 8) * block.scale
                if (column) block.x + across.toDouble() to block.y + along.toDouble() else block.x + along.toDouble() to block.y + across.toDouble()
            }
            fun stackAt(point: Pair<Double, Double>) = context.computeOnClient<ItemStack?, Nothing> { Hud.itemAt(point.first, point.second) }

            check(HeadSkin.worn(stackAt(piece(0)))) { "The helmet's icon lost its skin" }
            check(stackAt(piece(1))?.get(DataComponents.DYED_COLOR)?.rgb() == DYE) { "The chestplate lost its dye" }
            check(stackAt(piece(2)) == null) { "The leggings, which the player does not wear, have an icon" }
            check(stackAt(piece(3))?.item == Items.LEATHER_BOOTS) { "The fourth icon is not the boots" }
            context.takeScreenshot("equipment-display")

            context.runOnClient<Nothing> { it.gui.setScreen(ChatScreen("", false)) }
            context.waitForScreen(ChatScreen::class.java)
            val chest = piece(1)
            context.point(chest.first, chest.second, settle = 5)
            context.takeScreenshot("equipment-display-tooltip")
            context.runOnClient<Nothing> { it.gui.setScreen(null) }

            try {
                DsModClient.feature<EquipmentDisplay>().layout.value = EquipmentLayout.COLUMN
                context.waitTicks(10)
                check(stackAt(piece(3, column = true))?.item == Items.LEATHER_BOOTS) { "The boots are not at the foot of the column" }
                context.takeScreenshot("equipment-display-column")
            } finally {
                DsModClient.feature<EquipmentDisplay>().layout.value = EquipmentLayout.ROW
            }

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
            check(stackAt(piece(4)) == null) { "The pet has an icon before its head was seen" }
            context.takeScreenshot("equipment-display-pet-name")

            world.floatPet("[Lvl 100] Ender Dragon")
            context.waitTicks(30)
            val icon = stackAt(piece(4))
            check(HeadSkin.worn(icon)) { "The pet's icon lost the skin of the head it wears: $icon" }
            context.takeScreenshot("equipment-display-pet")

            val equipmentDisplay = DsModClient.feature<EquipmentDisplay>()
            try {
                equipmentDisplay.look.value = EquipmentLook.CLEAR
                context.waitTicks(10)
                context.takeScreenshot("equipment-display-clear")

                equipmentDisplay.look.value = EquipmentLook.HOTBAR
                context.waitTicks(10)
                check(HeadSkin.worn(stackAt(piece(0, hotbar = true)))) { "The hotbar look moved the helmet off its slot" }
                context.takeScreenshot("equipment-display-hotbar")

                equipmentDisplay.placement.value = HudDock.RIGHT_OF_HOTBAR
                context.waitTicks(10)
                val (width, height) = context.computeOnClient<Pair<Int, Int>, Nothing> { it.window.guiScaledWidth to it.window.guiScaledHeight }
                val first = width / 2 + 91 + 7 + 3 + 8.0 to height - 22 + 3 + 8.0
                check(HeadSkin.worn(stackAt(first))) { "The helmet is not in the first slot right of the hotbar" }
                check(HeadSkin.worn(stackAt(first.first + 4 * 20 to first.second))) { "The pet is not in the fifth slot right of the hotbar" }
                context.takeScreenshot("equipment-display-docked-right")

                equipmentDisplay.placement.value = HudDock.LEFT_OF_HOTBAR
                equipmentDisplay.petPosition.value = PetPosition.ABOVE
                context.waitTicks(10)
                val boots = width / 2 - 91 - 29 - 7 - 1 - 20 + 3 + 8.0 to height - 22 + 3 + 8.0
                check(stackAt(boots)?.item == Items.LEATHER_BOOTS) { "The boots are not in the last slot left of the offhand slot" }
                check(HeadSkin.worn(stackAt(boots.first - 3 * 20 to boots.second - 22))) { "The pet is not above the helmet" }
                context.takeScreenshot("equipment-display-docked-left")
            } finally {
                equipmentDisplay.look.value = EquipmentLook.PLATE
                equipmentDisplay.placement.value = HudDock.FREE
                equipmentDisplay.petPosition.value = PetPosition.WITH_ARMOR
            }
            context.waitTicks(10)

            for (slot in listOf("head", "chest", "feet")) world.server.runCommand("item replace entity @a armor.$slot with minecraft:air")
            context.waitTicks(20)
            check(stackAt(piece(0)) == null && HeadSkin.worn(stackAt(piece(4)))) { "With the armor off, the pet's head should be all that shows" }
        }
    }

    private companion object {
        const val ID = "equipment-display"
        const val DYE = 0x3366CC
    }
}
