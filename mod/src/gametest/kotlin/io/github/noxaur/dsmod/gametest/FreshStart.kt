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

object FreshStart {
    @OptIn(ExperimentalPathApi::class)
    fun before(context: ClientGameTestContext) = context.runOnClient<Nothing> {
        defaults(DsModClient.settings, DsModConfig())
        for (store in Store.entries) DsModClient.settings.stored(store).reset()
        check(ConfigFile.encode(DsModClient.settings) == ConfigFile.encode(DsModConfig().also { SettingsTree(it) })) {
            "A setting kept its value from the last test. Teach FreshStart.defaults the kind of field it missed."
        }
        DsModClient.restart { FabricLoader.getInstance().configDir.resolve("dsmod/data").deleteRecursively() }
    }

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
