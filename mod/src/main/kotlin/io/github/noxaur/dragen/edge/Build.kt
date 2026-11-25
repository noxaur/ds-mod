package io.github.noxaur.dragen.edge

import net.fabricmc.loader.api.FabricLoader

object Build {
    private val mod = FabricLoader.getInstance().getModContainer("dragen").get().metadata

    val backend: String = mod.getCustomValue("dragen:backend").asString.trimEnd('/')
    val website: String = mod.getCustomValue("dragen:website").asString.trimEnd('/')
    val version: String = mod.version.friendlyString
}
