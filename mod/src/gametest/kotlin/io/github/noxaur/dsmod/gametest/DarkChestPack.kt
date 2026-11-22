package io.github.noxaur.dsmod.gametest

import net.fabricmc.api.ClientModInitializer
import net.fabricmc.fabric.api.resource.v1.ResourceLoader
import net.fabricmc.fabric.api.resource.v1.pack.PackActivationType
import net.fabricmc.loader.api.FabricLoader
import net.minecraft.resources.Identifier

object DarkChestPack : ClientModInitializer {
    override fun onInitializeClient() {
        val mod = FabricLoader.getInstance().getModContainer("dsmod_gametest").orElseThrow()
        ResourceLoader.registerBuiltinPack(Identifier.fromNamespaceAndPath("dsmod_gametest", "dark_chest"), mod, PackActivationType.NORMAL)
    }
}
