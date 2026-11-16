package io.github.noxaur.dsmod.gametest

import io.github.noxaur.dsmod.edge.DsModClient
import io.github.noxaur.dsmod.edge.Hud
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext
import net.minecraft.ChatFormatting
import net.minecraft.core.component.DataComponents
import net.minecraft.network.chat.Component
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.component.ItemLore
import java.util.Base64

/**
 * Active Pet's block on the HUD, with its icon left of its rows. The sidebar names the pet, a pet floating beside the
 * player gives its head, and the Pets menu its held item and XP, with the active pet marked. A held item in chat and a
 * level up in the sidebar follow. Read the screenshots: the name in Epic purple with the hint, the head beside it, the
 * mark in the menu, the bar at 40%, and after the level up the new item and the hint again.
 */
class ActivePetTest : FabricClientGameTest {
    override fun runTest(context: ClientGameTestContext) {
        val general = DsModClient.settings.general
        general.activePet = true
        general.equipmentDisplay = false
        try {
            context.worldBuilder().create().use { world ->
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

                /** The middle of the block's icon in GUI pixels, and the item there. */
                fun icon(): ItemStack? = context.computeOnClient<ItemStack?, Nothing> {
                    val block = checkNotNull(Hud.find(ID)) { "Active Pet is not on the HUD" }
                    Hud.itemAt((block.x + 12 * block.scale).toDouble(), (block.y + 12 * block.scale).toDouble())
                }
                check(icon() == null) { "The pet has an icon before its head was seen" }
                context.takeScreenshot("active-pet-name")

                world.server.runCommand(
                    """execute at @a run summon armor_stand ~1 ~1 ~1 {CustomName:"[Lvl 37] Ender Dragon",Invisible:1b,NoGravity:1b,equipment:{head:{id:"minecraft:player_head",components:{"minecraft:profile":{properties:[{name:"textures",value:"$TEXTURES"}]}}}}}""",
                )
                context.waitTicks(30)
                check(skin(icon())) { "The icon is not the head of the pet floating beside the player" }
                context.takeScreenshot("active-pet-head")

                // The menu's head wears the same skin as the floating one, taken from a head the server gave the player.
                world.server.runCommand("give @a minecraft:player_head[profile={properties:[{name:\"textures\",value:\"$TEXTURES\"}]}]")
                context.waitTicks(5)
                val head = world.server.computeOnServer<ItemStack, Nothing> { it.playerList.players.first().inventory.getItem(0).copy() }
                ServerMenus.open(context, world, 6, "Pets") { chest ->
                    chest.setItem(11, card(head.copy(), active = false, "Click to summon!"))
                    chest.setItem(13, card(head.copy(), active = true, "Progress to Level 38: 40%", "4,000/10,000 XP"))
                }
                context.waitTicks(5)
                check(context.computeOnClient<Boolean, Nothing> { DsModClient.game.readouts.single { it.feature.id == ID }.slots.keys == setOf(13) }) {
                    "The active pet is not the one marked in the Pets menu"
                }
                context.takeScreenshot("active-pet-menu")
                context.runOnClient<Nothing> { it.player!!.closeContainer() }
                context.waitTicks(10)
                context.takeScreenshot("active-pet")

                world.server.runCommand("""tellraw @a "Equipped Lucky Clover on [Lvl 37] Ender Dragon!"""")
                pet(38)
                context.waitTicks(20)
                val rows = context.computeOnClient<List<String>, Nothing> { DsModClient.game.readouts.single { r -> r.feature.id == ID }.hud!!.rows.map { r -> r.text.plain } }
                check(rows == listOf("[Lvl 38] Ender Dragon", "Item: Lucky Clover", "Open your Pets menu to read its XP.")) { "After the level up the block reads $rows" }
                check(skin(icon())) { "The icon went after the level up" }
                context.takeScreenshot("active-pet-level-up")
            }
        } finally {
            general.activePet = false
            general.equipmentDisplay = true
        }
    }

    /** The Ender Dragon as the Pets menu shows it, holding Gold Claws, with [progress] under its level. */
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

    /** Whether [stack] wears [SKIN]. */
    private fun skin(stack: ItemStack?): Boolean {
        val value = stack?.get(DataComponents.PROFILE)?.partialProfile()?.properties()?.get("textures")?.firstOrNull()?.value() ?: return false
        return SKIN in String(Base64.getDecoder().decode(value))
    }

    private companion object {
        const val ID = "active-pet"
        const val SKIN = "f83159c5bb7d80454d2d646b15774b7116aabcb3af5b6777a27573fc45f7854f"
        val TEXTURES: String = Base64.getEncoder().encodeToString("{\"textures\":{\"SKIN\":{\"url\":\"http://textures.minecraft.net/texture/$SKIN\"}}}".toByteArray())
    }
}
