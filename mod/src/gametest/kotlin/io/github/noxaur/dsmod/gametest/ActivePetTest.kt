package io.github.noxaur.dsmod.gametest

import io.github.noxaur.dsmod.core.feature.ui.activepet.ActivePet
import io.github.noxaur.dsmod.core.feature.ui.equipmentdisplay.EquipmentDisplay
import io.github.noxaur.dsmod.edge.DsModClient
import io.github.noxaur.dsmod.edge.Hud
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext
import net.minecraft.ChatFormatting
import net.minecraft.core.component.DataComponents
import net.minecraft.network.chat.Component
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.component.ItemLore

class ActivePetTest : FabricClientGameTest {
    override fun runTest(context: ClientGameTestContext) {
        val activePet = DsModClient.feature<ActivePet>()
        val equipmentDisplay = DsModClient.feature<EquipmentDisplay>()
        activePet.feature.enabled = true
        equipmentDisplay.feature.enabled = false
        try {
            context.worldBuilder().create().use { world ->
                fun hudRows() = context.computeOnClient<List<String>, Nothing> { DsModClient.game.readouts.single { r -> r.feature.id == ID }.hud!!.rows.map { r -> r.text.plain } }
                fun pet(level: Int) =
                    world.server.runCommand("""team modify ds_pet suffix [{"text":"[Lvl $level] ","color":"gray"},{"text":"Ender Dragon","color":"dark_purple"}]""")
                listOf(
                    """scoreboard objectives add ds dummy "Dragon Sim"""",
                    "scoreboard objectives setdisplay sidebar ds",
                    "team add ds_pet",
                    """team modify ds_pet prefix {"text":"Pet: "}""",
                    "team join ds_pet Pet",
                    "scoreboard players set Pet ds 1",
                    "scoreboard players display name Pet ds \"\"",
                ).forEach(world.server::runCommand)
                pet(37)
                context.waitTicks(20)

                fun icon(): ItemStack? = context.computeOnClient<ItemStack?, Nothing> {
                    val block = checkNotNull(Hud.find(ID)) { "Active Pet is not on the HUD" }
                    Hud.itemAt((block.x + 12 * block.scale).toDouble(), (block.y + 12 * block.scale).toDouble())
                }
                check(icon() == null) { "The pet has an icon before its head was seen" }
                context.takeScreenshot("active-pet-name")

                world.floatPet("[Lvl 37] Ender Dragon")
                context.waitTicks(30)
                check(HeadSkin.worn(icon())) { "The icon is not the head of the pet floating beside the player" }
                context.takeScreenshot("active-pet-head")

                world.server.runCommand("give @a ${HeadSkin.ITEM}")
                context.waitTicks(5)
                val head = world.server.computeOnServer<ItemStack, Nothing> { it.player.inventory.getItem(0).copy() }
                ServerMenus.open(context, world, 6, "Pets") { chest ->
                    chest.setItem(11, card(head.copy(), active = false, "Click to summon!"))
                    chest.setItem(13, card(head.copy(), active = true, "Progress to Level 38: 40%", "4,000/10,000 XP"))
                }
                context.waitTicks(5)
                check(context.computeOnClient<Boolean, Nothing> { DsModClient.game.readouts.single { it.feature.id == "pets-menu" }.slots.filterValues { m -> m.outline }.keys == setOf(13) }) {
                    "The active pet is not the one Pets Menu outlines"
                }
                context.takeScreenshot("active-pet-menu")
                context.runOnClient<Nothing> { it.player!!.closeContainer() }
                context.waitTicks(10)
                context.takeScreenshot("active-pet")

                context.waitTicks(60)
                ServerMenus.open(context, world, 6, "Pets") { chest ->
                    chest.setItem(13, card(head.copy(), active = true, "Progress to Level 38: 50%", "5,000/10,000 XP"))
                }
                context.runOnClient<Nothing> { it.player!!.closeContainer() }
                context.waitTicks(10)
                val paced = hudRows()
                check(paced.any { it.startsWith("XP per Hour: ") } && paced.any { it.startsWith("Next Level: ") }) { "Two reads gave no rate: $paced" }
                context.takeScreenshot("active-pet-rate")

                world.server.runCommand("""tellraw @a "Equipped Lucky Clover on [Lvl 37] Ender Dragon!"""")
                pet(38)
                context.waitTicks(20)
                val rows = hudRows()
                check(rows == listOf("[Lvl 38] Ender Dragon", "Item: Lucky Clover", "Open your Pets menu to read its XP.")) { "After the level up the block reads $rows" }
                check(HeadSkin.worn(icon())) { "The icon went after the level up" }
                context.takeScreenshot("active-pet-level-up")
            }
        } finally {
            activePet.feature.enabled = false
            equipmentDisplay.feature.enabled = true
        }
    }

    private fun card(stack: ItemStack, active: Boolean, vararg progress: String): ItemStack {
        stack.set(DataComponents.CUSTOM_NAME, Component.literal("[Lvl 37] ").withStyle(ChatFormatting.GRAY).append(Component.literal(if (active) "Ender Dragon" else "Wolf").withStyle(ChatFormatting.DARK_PURPLE)))
        val lines = listOf(
            Component.literal("Held Item").withStyle(ChatFormatting.GOLD),
            Component.literal("Gold Claws").withStyle(ChatFormatting.GOLD),
            Component.empty(),
            Component.literal("LEVEL 37"),
        ) + progress.map { Component.literal(it).withStyle(ChatFormatting.GRAY) } + Component.empty() +
            (if (active) listOf(Component.literal("ACTIVE").withStyle(ChatFormatting.GREEN), Component.literal("Click to deactivate!")) else emptyList())
        stack.set(DataComponents.LORE, ItemLore(lines))
        return stack
    }

    private companion object {
        const val ID = "active-pet"
    }
}
