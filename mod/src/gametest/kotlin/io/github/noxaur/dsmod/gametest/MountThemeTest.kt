package io.github.noxaur.dsmod.gametest

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext
import net.minecraft.client.gui.screens.inventory.AbstractMountInventoryScreen
import net.minecraft.client.gui.screens.inventory.HorseInventoryScreen
import net.minecraft.client.gui.screens.inventory.NautilusInventoryScreen
import net.minecraft.world.entity.EntitySpawnReason
import net.minecraft.world.entity.EntityTypes
import net.minecraft.world.entity.HasCustomInventoryScreen
import net.minecraft.world.entity.animal.equine.AbstractHorse
import net.minecraft.world.entity.animal.nautilus.AbstractNautilus

class MountThemeTest : FabricClientGameTest {
    override fun runTest(context: ClientGameTestContext) {
        context.worldBuilder().create().use { world ->
            for (type in listOf(EntityTypes.HORSE, EntityTypes.NAUTILUS)) {
                var id = 0
                ThemedMenus.onServer(world) { player ->
                    val mount = checkNotNull(type.create(player.level(), EntitySpawnReason.COMMAND))
                    when (mount) {
                        is AbstractHorse -> mount.setTamed(true)
                        is AbstractNautilus -> mount.tame(player)
                    }
                    mount.snapTo(player.x + 3, player.y, player.z, 0f, 0f)
                    player.level().addFreshEntity(mount)
                    id = mount.id
                }
                context.waitFor { it.level?.getEntity(id) != null }
                ThemedMenus.onServer(world) { player -> (player.level().getEntity(id) as HasCustomInventoryScreen).openCustomInventoryScreen(player) }
                context.waitFor { it.gui.screen() is HorseInventoryScreen || it.gui.screen() is NautilusInventoryScreen }
                context.waitTicks(5)
                val title = context.computeOnClient<String, Nothing> { (it.gui.screen() as AbstractMountInventoryScreen<*>).title.string }
                ThemedMenus.check(context, title, listOf(Part("the mount's well", 27, 19) { it.well }))
                ThemedMenus.onServer(world) { it.closeContainer() }
                context.waitFor { it.gui.screen() == null }
            }
        }
    }
}
