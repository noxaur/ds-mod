package io.github.noxaur.dsmod.edge

import com.mojang.brigadier.arguments.StringArgumentType
import io.github.notenoughupdates.moulconfig.managed.ManagedConfig
import io.github.noxaur.dsmod.core.config.DsModConfig
import io.github.noxaur.dsmod.core.feature.Game
import io.github.noxaur.dsmod.core.feature.features
import io.github.noxaur.dsmod.core.feature.inventory.SlotLock
import io.github.noxaur.dsmod.core.game.DragSim
import io.github.noxaur.dsmod.core.game.SystemMessage
import io.github.noxaur.dsmod.core.game.WorldChanged
import io.github.noxaur.dsmod.edge.capture.Recorder
import io.github.noxaur.dsmod.edge.capture.Recording
import io.github.noxaur.dsmod.edge.contribute.Contribute
import io.github.noxaur.dsmod.edge.profile.Profiles
import io.github.noxaur.dsmod.edge.ui.Kit
import net.fabricmc.api.ClientModInitializer
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback
import net.fabricmc.fabric.api.client.command.v2.ClientCommands
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientChunkEvents
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLevelEvents
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents
import net.fabricmc.loader.api.FabricLoader
import net.minecraft.client.Minecraft
import org.slf4j.LoggerFactory
import java.lang.reflect.Field
import java.net.InetAddress
import java.net.InetSocketAddress

object DsModClient : ClientModInitializer {
    private const val FLUSH_TICKS = 20 * 60
    private const val TAB_POLL_TICKS = 5
    private const val PET_POLL_TICKS = 20

    private lateinit var config: ManagedConfig<DsModConfig>

    // The jar relocates MoulConfig, so a type from it in a signature would not link for a mod built outside.
    val settings: DsModConfig get() = config.instance
    lateinit var game: Game
        private set
    private lateinit var readouts: Readouts
    lateinit var saved: SavedFiles
        private set
    private lateinit var tabs: TabListPoller
    private lateinit var sidebars: SidebarPoller
    private lateinit var containers: ContainerPoller
    private lateinit var pets: PetPoller
    private var ticks = 0
    private var onDragSim = false

    /** dragsim.co's addresses, so a player who joins by IP is on it too. Looked up off the client thread, and empty until then. */
    @Volatile
    private var dragSimAt = emptySet<InetAddress>()

    /** Looks dragsim.co up again for the next join, so a move to a new IP is followed and a failed lookup is retried. */
    private fun lookUpDragSim() {
        Thread.ofVirtual().start { runCatching { dragSimAt = InetAddress.getAllByName(DragSim.HOST).toSet() } }
    }

    /** Lets the mod run on any server whatever the setting says. The debug build sets it, so `runClient` and the game tests run anywhere. */
    var anyServer = false

    /** Whether the mod runs on the server the player is on. A feature in the edge asks this before its own switch. */
    val active: Boolean get() = anyServer || onDragSim || !config.instance.general.onlyOnDragSim

    /** Opens the settings where the player left them, scrolled to [setting] of `DsModConfig` when it is given. */
    fun openSettings(setting: Field? = null) = Settings.open(setting)

    /** Each of the mod's own screens goes back to the screen it was opened over, so a button in the settings returns there. */
    fun openBeaconEditor() = Minecraft.getInstance().let { it.setScreenAndShow(BeaconScreen(config.instance.end, it.gui.screen(), config::saveToFile)) }

    fun openTabEditor() =
        Minecraft.getInstance().let { it.setScreenAndShow(TabEditorScreen(config.instance.betterUi, it.gui.screen(), config::saveToFile)) }

    fun openBindsEditor() =
        Minecraft.getInstance().let { it.setScreenAndShow(CommandBindsScreen(config.instance.general, it.gui.screen(), config::saveToFile)) }

    fun openHudEditor() =
        Minecraft.getInstance().let { it.setScreenAndShow(HudEditorScreen(readouts, config.instance.hud, it.gui.screen(), config::saveToFile)) }

    fun openRarityStyle() =
        Minecraft.getInstance().let { it.setScreenAndShow(RarityStyleScreen(config.instance.inventory, it.gui.screen(), config::saveToFile)) }

    override fun onInitializeClient() {
        config = ManagedConfig.create(
            FabricLoader.getInstance().configDir.resolve("dsmod/config.json").toFile(),
            DsModConfig::class.java,
        )
        val all = features(config.instance)
        game = Game(all) { active }
        saved = SavedFiles(FabricLoader.getInstance().configDir.resolve("dsmod/data"), game).also { it.load() }
        Settings.register(config)
        Kit.register()
        Hud.register()
        Slots.register(config.instance.inventory)
        MenuSkin.register()
        MenuBack.register()
        TabBar.register()
        World.register()
        Etherwarps.register()
        ArmorSwapGuards.register(config.instance.inventory)
        EyeGuards.register(config.instance.end)
        CopyChats.register(config.instance.chat)
        SlotGuards.register(all.filterIsInstance<SlotLock>().single(), config.instance.inventory)
        CommandKeys.register(config.instance.general)
        readouts = Readouts(game, config.instance.hud).also { it.register() }
        ChatDrags.register(readouts, config.instance.hud, config::saveToFile)
        tabs = TabListPoller(game)
        sidebars = SidebarPoller(game)
        containers = ContainerPoller(game)
        pets = PetPoller(game)
        config.instance.hud.edit = Runnable { openHudEditor() }
        config.instance.end.editBeam = Runnable { openBeaconEditor() }
        config.instance.betterUi.editTabs = Runnable { openTabEditor() }
        config.instance.general.editBinds = Runnable { openBindsEditor() }
        config.instance.inventory.editRarityStyle = Runnable { openRarityStyle() }
        config.instance.contribute.open = Runnable { Contribute.open() }
        Recorder.register()
        Recording.register()
        Contribute.register()
        Profiles.registerNames()
        val log = LoggerFactory.getLogger("dsmod")
        game.metrics.sinks += { snapshot ->
            snapshot.failures.forEach { (name, failure) -> log.error("{} failed {} times, first with {}", name, failure.count, failure.first) }
        }

        ClientReceiveMessageEvents.ALLOW_GAME.register { message, overlay ->
            // A server may send the action bar as a chat line marked as an overlay, or as its own packet, which `Packets` sees.
            if (overlay) Packets.actionBar(message)
            overlay || !game.wants(SystemMessage::class.java) || game.post(System.currentTimeMillis(), SystemMessage(message.toText()))
        }
        // Read once per connection, because the Fullbright mixin asks [active] every frame.
        lookUpDragSim()
        ClientPlayConnectionEvents.JOIN.register { handler, _, _ ->
            val reached = (handler.connection.remoteAddress as? InetSocketAddress)?.address
            onDragSim = DragSim.serves(handler.serverData?.ip, reached, dragSimAt)
            log.info("Joined {} at {}, which is {}dragsim.co", handler.serverData?.ip ?: "singleplayer", reached, if (onDragSim) "" else "not ")
            lookUpDragSim()
        }
        ClientPlayConnectionEvents.DISCONNECT.register { _, _ -> onDragSim = false }
        ClientLevelEvents.AFTER_CLIENT_LEVEL_CHANGE.register { _, _ ->
            Packets.worldChanged()
            game.post(System.currentTimeMillis(), WorldChanged)
            tabs.forget()
            sidebars.forget()
        }
        ClientChunkEvents.CHUNK_LOAD.register { _, chunk -> Packets.chunk(chunk) }
        ClientTickEvents.END_CLIENT_TICK.register {
            if (ticks % TAB_POLL_TICKS == 0) {
                game.metrics.time("tab-poll") { tabs.poll(it) }
                game.metrics.time("sidebar-poll") { sidebars.poll(it) }
            }
            game.metrics.time("container-poll") { containers.poll(it) }
            // A pet changes when the player swaps it, so once a second is soon enough.
            if (ticks % PET_POLL_TICKS == 0) game.metrics.time("pet-poll") { pets.poll(it) }
            Packets.tick(it)
            game.tick(System.currentTimeMillis())
            readouts.push()
            Contribute.tick(game, config.instance.contribute.reportGameData, System.currentTimeMillis())
            if (++ticks % FLUSH_TICKS == 0) {
                saved.flush()
                game.metrics.flush()
            }
        }
        // In this order: a request's run cut short is deleted, the recorder finishes what it holds, then an upload gets its moment.
        ClientLifecycleEvents.CLIENT_STOPPING.register {
            Recording.quit()
            Recorder.quit()
            Contribute.quit()
            saved.close()
        }
        // `/dsm` is the short name, as `/sh` is SkyHanni's. `/du` is DragSim Utils', and a player may run both mods.
        ClientCommandRegistrationCallback.EVENT.register { dispatcher, _ ->
            for (name in listOf("dsmod", "dsm")) dispatcher.register(
                ClientCommands.literal(name)
                    .executes { context ->
                        // The chat screen closes after the command returns, so open on the next tick.
                        context.source.client.schedule { openSettings() }
                        1
                    }
                    .then(
                        ClientCommands.argument("search", StringArgumentType.greedyString()).executes { context ->
                            val search = StringArgumentType.getString(context, "search")
                            context.source.client.schedule { Settings.open(search = search) }
                            1
                        },
                    )
                    .then(
                        ClientCommands.literal("hud").executes { context ->
                            context.source.client.schedule { openHudEditor() }
                            1
                        },
                    )
                    .then(ClientCommands.literal("login").executes { Account.login(); 1 })
                    .then(ClientCommands.literal("submit").executes { Contribute.submit(); 1 })
                    .then(ClientCommands.literal("cancel").executes { Contribute.cancel(); 1 })
                    .then(Profiles.dsmod()),
            )
            Profiles.register(dispatcher)
        }
    }
}
