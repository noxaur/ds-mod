package io.github.noxaur.dsmod.gametest

import net.fabricmc.api.ClientModInitializer
import net.fabricmc.fabric.api.resource.v1.ResourceLoader
import net.fabricmc.fabric.api.resource.v1.pack.PackActivationType
import net.fabricmc.loader.api.FabricLoader
import net.minecraft.resources.Identifier

/**
 * Offers a resource pack, off until a test turns it on, that draws a chest's slots dark, as many players' packs do.
 * `RarityStylesTest` shows the marks on it. Its texture keeps vanilla's slot places and is drawn by the tests themselves.
 */
object DarkChestPack : ClientModInitializer {
    override fun onInitializeClient() {
        val mod = FabricLoader.getInstance().getModContainer("dsmod_gametest").orElseThrow()
        ResourceLoader.registerBuiltinPack(Identifier.fromNamespaceAndPath("dsmod_gametest", "dark_chest"), mod, PackActivationType.NORMAL)
    }
}
