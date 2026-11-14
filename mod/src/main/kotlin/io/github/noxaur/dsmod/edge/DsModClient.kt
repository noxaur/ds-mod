package io.github.noxaur.dsmod.edge

import io.github.notenoughupdates.moulconfig.managed.ManagedConfig
import io.github.noxaur.dsmod.core.config.DsModConfig
import net.fabricmc.api.ClientModInitializer
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback
import net.fabricmc.fabric.api.client.command.v2.ClientCommands
import net.fabricmc.loader.api.FabricLoader

object DsModClient : ClientModInitializer {
    private lateinit var config: ManagedConfig<DsModConfig>

    fun openSettings() = config.openConfigGui()

    override fun onInitializeClient() {
        config = ManagedConfig.create(
            FabricLoader.getInstance().configDir.resolve("dsmod/config.json").toFile(),
            DsModConfig::class.java,
        )
        Hud.register()
        Slots.register()
        Tooltips.register()
        World.register()
        ClientCommandRegistrationCallback.EVENT.register { dispatcher, _ ->
            dispatcher.register(
                ClientCommands.literal("dsmod").executes { context ->
                    // The chat screen closes after the command returns, so open on the next tick.
                    context.source.client.schedule { openSettings() }
                    1
                },
            )
        }
    }
}
