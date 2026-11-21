package io.github.noxaur.dsmod.gametest

import io.github.noxaur.dsmod.core.text.Role
import net.minecraft.world.SimpleContainer
import net.minecraft.world.inventory.BlastFurnaceMenu
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext

class BlastFurnaceThemeTest : FabricClientGameTest {
    override fun runTest(context: ClientGameTestContext) = ThemedMenus.run(
        context, "Blast Furnace", { id, inventory -> BlastFurnaceMenu(id, inventory, SimpleContainer(3), ThemedMenus.data(50, 200, 20, 100)) },
        listOf(Part("the lit flame", 63, 48) { Role.WARNING.argb() }, Part("the arrow to go", 93, 42) { it.well }),
    )
}
