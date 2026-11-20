package io.github.noxaur.dsmod.gametest

import io.github.noxaur.dsmod.core.text.Role
import net.minecraft.world.SimpleContainer
import net.minecraft.world.inventory.SmokerMenu
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext

class SmokerThemeTest : FabricClientGameTest {
    override fun runTest(context: ClientGameTestContext) = ThemedMenus.run(
        context, "Smoker", { id, inventory -> SmokerMenu(id, inventory, SimpleContainer(3), ThemedMenus.data(0, 200, 90, 100)) },
        listOf(Part("the cold flame", 63, 48) { it.well }, Part("the done arrow", 83, 42) { Role.BODY.argb() }),
    )
}
