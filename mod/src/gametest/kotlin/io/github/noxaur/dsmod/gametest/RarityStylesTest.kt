package io.github.noxaur.dsmod.gametest

import io.github.noxaur.dsmod.core.container.MarkStyle
import io.github.noxaur.dsmod.core.container.SlotStyle
import io.github.noxaur.dsmod.core.feature.inventory.itemrarity.ItemRarity
import io.github.noxaur.dsmod.core.feature.menus.look.menutheme.MenuTheme
import io.github.noxaur.dsmod.edge.DsModClient
import io.github.noxaur.dsmod.edge.SlotArts
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext
import java.util.concurrent.CompletableFuture

class RarityStylesTest : FabricClientGameTest {
    private companion object {
        const val DARK_CHEST = "dsmod_gametest:dark_chest"
        const val RELOAD_TICKS = 1200
    }

    private fun eachStyle(context: ClientGameTestContext, name: String) {
        context.point(0, 0)
        context.runOnClient<Nothing> { it.gui.toastManager().clear() }
        for (mark in MarkStyle.entries) {
            for (sparkles in listOf(false, true)) {
                context.runOnClient<Nothing> { DsModClient.feature<ItemRarity>().inventoryStyle.value = SlotStyle(mark, sparkles = sparkles) }
                context.waitTicks(3)
                context.takeScreenshot("$name-${mark.name.lowercase()}" + if (sparkles) "-sparkles" else "")
            }
        }
        context.runOnClient<Nothing> { DsModClient.feature<ItemRarity>().inventoryStyle.value = SlotStyle() }
    }

    private fun open(context: ClientGameTestContext, server: TestServerContext, dark: Boolean, why: String) {
        RarityChest.open(context, server)
        check(context.computeOnClient<Boolean, Nothing> { SlotArts.containerDark } == dark) { why }
    }

    private fun close(context: ClientGameTestContext) {
        context.runOnClient<Nothing> { it.player!!.closeContainer() }
        context.waitTicks(5)
    }

    private fun darkChest(context: ClientGameTestContext, on: Boolean) {
        val reload = context.computeOnClient<CompletableFuture<Void>, Nothing> { client ->
            val packs = client.resourcePackRepository
            check(packs.isAvailable(DARK_CHEST)) { "No pack $DARK_CHEST among ${packs.availableIds}" }
            if (on) packs.addPack(DARK_CHEST) else packs.removePack(DARK_CHEST)
            client.reloadResourcePacks()
        }
        context.waitFor({ reload.isDone && it.gui.overlay() == null }, RELOAD_TICKS)
    }

    override fun runTest(context: ClientGameTestContext) {
        context.worldBuilder().create().use { world ->
            for ((slot, item) in listOf(
                "COMMON" to "dirt", "UNCOMMON" to "iron_sword", "RARE" to "bow", "EPIC" to "golden_sword",
                "LEGENDARY" to "diamond_sword", "MYTHIC" to "netherite_sword", "DIVINE" to "trident", "SPECIAL" to "mace",
            ).withIndex()) {
                world.server.runCommand("""item replace entity @a container.$slot with ${item.second}[custom_data={item_data:{rarity:"${item.first}"}}]""")
            }
            context.runOnClient<Nothing> {
                DsModClient.feature<ItemRarity>().markCommon.value = true
                it.gui.toastManager().clear()
            }
            context.waitTicks(5)

            open(context, world.server, dark = true, "Menu Theme's Charcoal slots did not read as dark")
            eachStyle(context, "rarity-chest")
            context.runOnClient<Nothing> { DsModClient.feature<ItemRarity>().inventoryStyle.value = SlotStyle(opacity = 50) }
            context.waitTicks(3)
            context.takeScreenshot("rarity-chest-frame-half")
            context.runOnClient<Nothing> { DsModClient.feature<ItemRarity>().inventoryStyle.value = SlotStyle() }
            close(context)

            context.runOnClient<Nothing> { DsModClient.feature<MenuTheme>().feature.enabled = false }
            open(context, world.server, dark = false, "Vanilla's gray slots read as dark")
            eachStyle(context, "rarity-vanilla-chest")
            close(context)
            darkChest(context, on = true)
            open(context, world.server, dark = true, "The dark chest pack did not read as dark")
            eachStyle(context, "rarity-dark-chest")
            close(context)
            darkChest(context, on = false)
            context.runOnClient<Nothing> { DsModClient.feature<MenuTheme>().feature.enabled = true }

            eachStyle(context, "rarity-hotbar")
            context.runOnClient<Nothing> { DsModClient.feature<ItemRarity>().markCommon.value = false }
        }
    }
}
