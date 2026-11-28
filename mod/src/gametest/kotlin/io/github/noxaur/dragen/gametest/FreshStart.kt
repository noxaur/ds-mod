package io.github.noxaur.dragen.gametest

import io.github.noxaur.dragen.core.config.ConfigFile
import io.github.noxaur.dragen.core.config.DragenConfig
import io.github.noxaur.dragen.core.config.SettingsTree
import io.github.noxaur.dragen.core.config.Store
import io.github.noxaur.dragen.edge.DragenClient
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext
import net.fabricmc.loader.api.FabricLoader
import kotlin.io.path.ExperimentalPathApi
import kotlin.io.path.deleteRecursively

object FreshStart {
    @OptIn(ExperimentalPathApi::class)
    fun before(context: ClientGameTestContext) = context.runOnClient<Nothing> {
        DragenClient.settings.favorites = emptyList()
        for (store in Store.entries) DragenClient.settings.stored(store).reset()
        check(ConfigFile.encode(DragenClient.settings) == ConfigFile.encode(DragenConfig().also { SettingsTree(it, dev = DragenClient.dev) })) {
            "A setting kept its value from the last test. Save it in a slot of a store, so FreshStart resets it."
        }
        DragenClient.restart { FabricLoader.getInstance().configDir.resolve("dragen/data").deleteRecursively() }
    }
}
