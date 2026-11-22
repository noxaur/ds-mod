package io.github.noxaur.dsmod.gametest

import io.github.noxaur.dsmod.core.config.ConfigFile
import io.github.noxaur.dsmod.core.config.DsModConfig
import io.github.noxaur.dsmod.core.config.SettingsTree
import io.github.noxaur.dsmod.core.config.Store
import io.github.noxaur.dsmod.edge.DsModClient
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext
import net.fabricmc.loader.api.FabricLoader
import kotlin.io.path.ExperimentalPathApi
import kotlin.io.path.deleteRecursively

object FreshStart {
    @OptIn(ExperimentalPathApi::class)
    fun before(context: ClientGameTestContext) = context.runOnClient<Nothing> {
        DsModClient.settings.favorites = emptyList()
        for (store in Store.entries) DsModClient.settings.stored(store).reset()
        check(ConfigFile.encode(DsModClient.settings) == ConfigFile.encode(DsModConfig().also { SettingsTree(it) })) {
            "A setting kept its value from the last test. Save it in a slot of a store, so FreshStart resets it."
        }
        DsModClient.restart { FabricLoader.getInstance().configDir.resolve("dsmod/data").deleteRecursively() }
    }
}
