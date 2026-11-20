package io.github.noxaur.dsmod.gametest

import io.github.noxaur.dsmod.core.text.Role
import net.minecraft.world.SimpleContainer
import net.minecraft.world.inventory.FurnaceMenu
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext

class FurnaceThemeTest : FabricClientGameTest {
    override fun runTest(context: ClientGameTestContext) = ThemedMenus.run(
        context, "Furnace", { id, inventory -> FurnaceMenu(id, inventory, SimpleContainer(3), ThemedMenus.data(100, 200, 100, 200)) },
        listOf(Part("the lit flame", 63, 48) { Role.WARNING.argb() }, Part("the done arrow", 83, 42) { Role.BODY.argb() }, Part("the arrow to go", 93, 42) { it.well }),
    )
}
