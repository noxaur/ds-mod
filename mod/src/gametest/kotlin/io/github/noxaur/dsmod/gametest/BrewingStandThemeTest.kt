package io.github.noxaur.dsmod.gametest

import io.github.noxaur.dsmod.core.text.Role
import net.minecraft.world.SimpleContainer
import net.minecraft.world.inventory.BrewingStandMenu
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext

class BrewingStandThemeTest : FabricClientGameTest {
    override fun runTest(context: ClientGameTestContext) = ThemedMenus.run(
        context, "Brewing Stand", { id, inventory -> BrewingStandMenu(id, inventory, SimpleContainer(5), ThemedMenus.data(200, 20)) },
        listOf(Part("the fuel", 65, 45) { Role.WARNING.argb() }, Part("the brewed part of the arrow", 101, 20) { Role.BODY.argb() }),
    )
}
