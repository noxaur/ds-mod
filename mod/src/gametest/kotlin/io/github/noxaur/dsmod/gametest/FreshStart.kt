package io.github.noxaur.dsmod.gametest

import io.github.noxaur.dsmod.core.config.ConfigFile
import io.github.noxaur.dsmod.core.config.DsModConfig
import io.github.noxaur.dsmod.core.config.SettingsTree
import io.github.noxaur.dsmod.core.config.Store
import io.github.noxaur.dsmod.core.config.Stored
import io.github.noxaur.dsmod.edge.DsModClient
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext
import net.fabricmc.loader.api.FabricLoader
import java.lang.reflect.Modifier
import kotlin.io.path.ExperimentalPathApi
import kotlin.io.path.deleteRecursively

/**
 * Every game test runs in one game, so before each one the mod starts from what a launch with no saved files gives,
 * as Fabric restores the game's options. A test then passes whichever tests ran before it, which a run that picks only
 * some of them needs.
 */
object FreshStart {
    @OptIn(ExperimentalPathApi::class)
    fun before(context: ClientGameTestContext) = context.runOnClient<Nothing> {
        defaults(DsModClient.settings, DsModConfig())
        for (store in Store.entries) DsModClient.settings.stored(store).reset()
        // A feature's slot is written once a feature was built on the config, so the fresh config gets its features too.
        check(ConfigFile.encode(DsModClient.settings) == ConfigFile.encode(DsModConfig().also { SettingsTree(it) })) {
            "A setting kept its value from the last test. Teach FreshStart.defaults the kind of field it missed."
        }
        DsModClient.restart { FabricLoader.getInstance().configDir.resolve("dsmod/data").deleteRecursively() }
    }

    /**
     * Copies every field of [fresh] into [settings] in place, because the edge's listeners hold its sections. A section
     * is a read-only field and is copied field by field, and a read-only map is refilled. The features' slots are
     * left to [Stored.reset], since the features built on the config hold them.
     */
    private fun defaults(settings: Any, fresh: Any) {
        for (field in settings.javaClass.declaredFields) {
            if (Modifier.isStatic(field.modifiers)) continue
            field.isAccessible = true
            val value = field.get(fresh)
            when {
                value is Map<*, *> && value.values.any { it is Stored } -> Unit
                !Modifier.isFinal(field.modifiers) -> field.set(settings, value)
                value is Map<*, *> -> @Suppress("UNCHECKED_CAST") (field.get(settings) as MutableMap<Any?, Any?>).apply { clear(); putAll(value) }
                else -> defaults(field.get(settings), value)
            }
        }
    }
}
