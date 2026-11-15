package io.github.noxaur.dsmod.edge

import net.fabricmc.loader.api.FabricLoader

/**
 * What the build says about where ds-mod's own service lives. `backend_url` and `website_url` in `gradle.properties`
 * are written into the jar's `fabric.mod.json`, so one build serves one deployment and a player has nothing to set.
 */
object Build {
    private val mod = FabricLoader.getInstance().getModContainer("dsmod").get().metadata

    val backend: String = mod.getCustomValue("dsmod:backend").asString.trimEnd('/')
    val website: String = mod.getCustomValue("dsmod:website").asString.trimEnd('/')
}
