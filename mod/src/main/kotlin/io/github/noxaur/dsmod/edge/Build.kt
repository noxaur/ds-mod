package io.github.noxaur.dsmod.edge

import net.fabricmc.loader.api.FabricLoader

object Build {
    private val mod = FabricLoader.getInstance().getModContainer("dsmod").get().metadata

    val backend: String = mod.getCustomValue("dsmod:backend").asString.trimEnd('/')
    val website: String = mod.getCustomValue("dsmod:website").asString.trimEnd('/')
    val version: String = mod.version.friendlyString
}
