package io.github.noxaur.dsmod.gametest

import io.github.noxaur.dsmod.core.game.PetCard
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext
import net.minecraft.ChatFormatting
import net.minecraft.core.component.DataComponents
import net.minecraft.network.chat.Component
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.minecraft.world.item.component.ItemLore

class PetsMenuTest : FabricClientGameTest {

    private fun pet(name: String, color: ChatFormatting, rarity: String, active: Boolean = false) = ItemStack(Items.PLAYER_HEAD).also {
        it.set(DataComponents.CUSTOM_NAME, Component.literal("[Lvl 100] ").withStyle(ChatFormatting.GRAY).append(Component.literal(name).withStyle(color)))
        val click = if (active) listOf(Component.literal("ACTIVE").withStyle(ChatFormatting.GREEN), Component.literal(PetCard.ACTIVE)) else listOf(Component.literal("Click to summon!"))
        it.set(DataComponents.LORE, ItemLore(listOf(Component.literal("LEVEL 100"), Component.empty(), Component.literal("MAX LEVEL"), Component.empty()) + click + Component.literal("$rarity COMBAT PET")))
    }

    private fun pets(context: ClientGameTestContext, world: TestSingleplayerContext) {
        val menu = ServerMenus.menu(6, PetCard.MENU) {
            it.setItem(4, ServerMenus.named(Items.BONE, PetCard.MENU))
            it.setItem(10, pet("Enderman", ChatFormatting.GOLD, "LEGENDARY"))
            it.setItem(11, pet("Wolf", ChatFormatting.GOLD, "LEGENDARY"))
            it.setItem(12, pet("Ender Dragon", ChatFormatting.DARK_PURPLE, "EPIC"))
            it.setItem(13, pet("Black Cat", ChatFormatting.GOLD, "LEGENDARY", active = true))
            it.setItem(14, pet("Rabbit", ChatFormatting.BLUE, "RARE"))
        }
        world.server.runOnServer<Nothing> { server -> server.player.openMenu(menu) }
        context.waitFor { ServerMenus.title(it) == PetCard.MENU }
        context.waitTicks(5)
    }

    override fun runTest(context: ClientGameTestContext) {
        context.worldBuilder().create().use { world ->
            pets(context, world)
            context.takeScreenshot("pets-menu")
        }
    }
}
