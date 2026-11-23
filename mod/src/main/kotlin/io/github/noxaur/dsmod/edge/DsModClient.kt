package io.github.noxaur.dsmod.edge

import io.github.noxaur.dsmod.core.feature.general.CtrlClick
import io.github.noxaur.dsmod.core.ui.SettingsTabs
import com.mojang.blaze3d.platform.InputConstants
import com.mojang.brigadier.arguments.StringArgumentType
import io.github.noxaur.dsmod.core.config.Canvas
import io.github.noxaur.dsmod.core.config.ConfigFile
import io.github.noxaur.dsmod.core.config.DsModConfig
import io.github.noxaur.dsmod.core.config.Opens
import io.github.noxaur.dsmod.core.config.Panel
import io.github.noxaur.dsmod.core.config.SettingsTree
import io.github.noxaur.dsmod.core.feature.Game
import io.github.noxaur.dsmod.core.game.DragSim
import io.github.noxaur.dsmod.core.game.LeftServer
import io.github.noxaur.dsmod.core.game.SlotClicked
import io.github.noxaur.dsmod.core.game.WorldChanged
import io.github.noxaur.dsmod.core.text.text
import io.github.noxaur.dsmod.core.ui.MenuTab
import io.github.noxaur.dsmod.edge.capture.Recorder
import io.github.noxaur.dsmod.edge.capture.Recording
import io.github.noxaur.dsmod.edge.contribute.Contribute
import io.github.noxaur.dsmod.edge.overview.ItemOverviews
import io.github.noxaur.dsmod.edge.storage.StorageOverlays
import io.github.noxaur.dsmod.edge.storage.StoragePreviews
import io.github.noxaur.dsmod.edge.profile.Profiles
import io.github.noxaur.dsmod.edge.ui.Editors
import io.github.noxaur.dsmod.edge.ui.Kit
import io.github.noxaur.dsmod.edge.ui.Looks
import net.fabricmc.api.ClientModInitializer
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback
import net.fabricmc.fabric.api.client.command.v2.ClientCommands
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientChunkEvents
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLevelEvents
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents
import net.fabricmc.fabric.api.client.rendering.v1.PictureInPictureRendererRegistry
import net.fabricmc.fabric.api.client.screen.v1.ScreenMouseEvents
import net.fabricmc.loader.api.FabricLoader
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.screens.Screen
import org.slf4j.LoggerFactory
import java.net.InetAddress
import java.net.InetSocketAddress
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen

object DsModClient : ClientModInitializer {
    private const val FLUSH_TICKS = 20 * 60
    private const val TAB_POLL_TICKS = 5

    private val log = LoggerFactory.getLogger("dsmod")
    private lateinit var file: ConfigFile

    lateinit var settings: DsModConfig
        private set
    internal lateinit var tree: SettingsTree
    lateinit var game: Game
        private set
    private lateinit var readouts: Readouts
    lateinit var saved: SavedFiles
        private set
    private lateinit var tabs: TabListPoller
    private lateinit var sidebars: SidebarPoller
    private lateinit var containers: ContainerPoller
    private lateinit var entities: EntityTracker
    private var ticks = 0
    private var onDragSim = false

    @Volatile
    private var left = false

    private fun leave() {
        if (!left) return
        left = false
        game.post(System.currentTimeMillis(), LeftServer)
        onDragSim = false
        Recording.left()
    }

    @Volatile
    private var dragSimAt = emptySet<InetAddress>()

    private fun lookUpDragSim() {
        Thread.ofVirtual().start { runCatching { dragSimAt = InetAddress.getAllByName(DragSim.HOST).toSet() } }
    }

    var anyServer = FabricLoader.getInstance().isModLoaded("dsmod_debug")

    fun settingsScreen(parent: Screen?, tabs: SettingsTabs = SettingsScreen.session(settings), reveal: String? = null, query: String? = null) =
        SettingsScreen(parent, settings, ::saveSettings, tabs, reveal, query)

    fun openSettings(id: String? = null, search: String? = null) =
        Minecraft.getInstance().let { it.setScreenAndShow(settingsScreen(it.gui.screen(), reveal = id, query = search)) }

    fun saveSettings() {
        runCatching { file.save(settings) }.onFailure { log.error("Could not save the settings", it) }
    }

    /** @keep The settings open on the next tick, after the game has handed out the typed character, or the search would take it. */
    private fun pressed(key: InputConstants.Key) {
        if (Keys.matches(key, settings.general.settingsKey.value)) Minecraft.getInstance().schedule { openSettings() }
        if (Keys.matches(key, settings.hud.editKey.value)) Minecraft.getInstance().schedule { openHudEditor() }
        ItemOverviews.pressed(key)
    }

    fun openHudEditor() =
        Minecraft.getInstance().let { it.setScreenAndShow(HudEditorScreen(game, tree.feature(), readouts, settings.hud, it.gui.screen(), ::saveSettings)) }

    private fun warnUnreadable() {
        val kept = file.unreadable ?: return
        log.error("Could not read the settings, so they start from the defaults. The old file is {}", kept)
        var told = false
        ClientPlayConnectionEvents.JOIN.register { _, _, client ->
            if (told) return@register
            told = true
            client.schedule { Chat.send(text { bad("ds-mod could not read your settings and started from the defaults. Your old file is kept as config/dsmod/${kept.fileName}.") }) }
        }
    }

    internal fun start() {
        SettingsScreen.forget()
        tree = SettingsTree(settings)
        game = Game(tree.features) { anyServer || onDragSim }
        saved = SavedFiles(FabricLoader.getInstance().configDir.resolve("dsmod/data"), game, Minecraft.getInstance().user.profileId.toString()).also { it.load() }
        readouts = Readouts(game, settings.hud, tree.feature())
        tabs = TabListPoller(game)
        sidebars = SidebarPoller(game)
        containers = ContainerPoller(game)
        entities = EntityTracker(game)
        game.metrics.sinks += { snapshot ->
            snapshot.failures.forEach { (name, failure) -> log.error("{} failed {} times, first with {}", name, failure.count, failure.first) }
        }
        Hooks.bind(tree.feature(), tree.owners.filterIsInstance<CtrlClick>().firstOrNull(), tree.feature())
        Hud.bind(game, tree.feature())
        Slots.bind(game, tree.feature())
        MenuSkin.bind(game, tree.feature(), tree.feature(), tree.feature())
        World.bind(game)
        Packets.bind(game)
        FloatingTexts.bind(game)
        Etherwarps.bind(game, tree.feature())
        HeldItems.bind(tree.feature())
        Cameras.bind(tree.feature(), tree.feature())
        PlayerModels.bind(tree.feature())
        InventoryKey.bind(tree.feature())
        Recorder.bind(game)
        Ticks.bind(game)
        Contribute.bind(game, tree.feature())
        ArmorSwapGuards.bind(tree.feature())
        AutoClicks.bind(tree.feature())
        ChatDrags.bind(readouts, tree.feature())
        CommandKeys.bind(game, tree.feature())
        CopyChats.bind(tree.feature())
        EyeGuards.bind(tree.feature())
        ForgeCrafts.bind(tree.feature())
        Hits.bind(game)
        HotdShortcut.bind(tree.feature())
        MenuBack.bind(tree.feature())
        Pings.bind(tree.feature())
        SkyBlockMenuClicks.bind(tree.feature())
        SlotGuards.bind(tree.feature())
        Wardrobes.bind(tree.feature())
        ProfileCooldowns.bind(tree.feature())
        TabBar.bind(tree.feature())
        Profiles.bind(game, tree.feature())
        ItemOverviews.bind(game, tree.feature())
        WaypointChat.bind(game, tree.feature(), ::saveSettings) { openSettings(it) }
        StorageOverlays.bind(game, tree.feature())
        StoragePreviews.bind(tree.feature())
    }

    override fun onInitializeClient() {
        file = ConfigFile(FabricLoader.getInstance().configDir.resolve("dsmod/config.json"))
        settings = file.load().apply { favorites = SettingsTree.favorites(favorites) }
        warnUnreadable()
        start()
        Hooks.register(settings.hud, ::pressed, { containers.changed(Minecraft.getInstance()) }) { parent -> settingsScreen(parent) }
        Kit.register(settings.hud)
        Looks.register()
        SlotArts.register()
        Hud.register()
        Slots.register()
        MenuSkin.register()
        StorageOverlays.register()
        StoragePreviews.register()
        ForgeCrafts.register()
        Wardrobes.register()
        ProfileCooldowns.register()
        AutoClicks.register()
        Trail.register()
        MenuBack.register()
        TabBar.register()
        HotdShortcut.register()
        World.register()
        Etherwarps.register()
        Hits.register()
        ArmorSwapGuards.register()
        EyeGuards.register()
        CopyChats.register()
        SlotGuards.register()
        CommandKeys.register()
        Pings.register()
        ScreenHooks.onEveryLayout<AbstractContainerScreen<*>> { screen, _ ->
            readouts.opened(screen)
            ScreenMouseEvents.beforeMouseClick(screen).register { _, click -> screen.slotAt(click.x(), click.y())?.takeIf { click.button() <= 1 }?.let { game.post(System.currentTimeMillis(), SlotClicked(it.index)) } }
        }
        ChatDrags.register(settings.hud, ::saveSettings)
        Editors.register(Canvas.HUD_LAYOUT) { panel, host -> @Suppress("UNCHECKED_CAST") HudLayoutEditor(panel as Panel<Map<String, DsModConfig.Position>>, readouts, host) }
        Editors.register(Canvas.MENU_TABS) { panel, host -> @Suppress("UNCHECKED_CAST") TabPlacesEditor(panel as Panel<List<MenuTab>>, host) }
        Editors.register(Canvas.PLAYER_MODEL) { _, _ -> PlayerPreview() }
        Editors.register(Canvas.HELD_ITEM) { _, _ -> HeldItemPreview() }
        PictureInPictureRendererRegistry.register { HeldItemPictureRenderer() }
        Opens.register(Opens.HUD_EDITOR, ::openHudEditor)
        Opens.register(Opens.REQUESTS, Contribute::open)
        Recorder.register()
        Recording.register(::leave)
        Contribute.register()
        Profiles.registerNames()
        WaypointChat.register()

        ClientReceiveMessageEvents.ALLOW_GAME.register { _, _ -> Packets.shows() }
        lookUpDragSim()
        ClientPlayConnectionEvents.JOIN.register { handler, _, _ ->
            leave()
            val reached = (handler.connection.remoteAddress as? InetSocketAddress)?.address
            onDragSim = DragSim.serves(handler.serverData?.ip, reached, dragSimAt)
            log.info("Joined {} at {}, which is {}dragsim.co", handler.serverData?.ip ?: "singleplayer", reached, if (onDragSim) "" else "not ")
            lookUpDragSim()
        }
        // @keep Fabric says so on the network thread, and the game drops the tasks queued for its thread as it disconnects.
        ClientPlayConnectionEvents.DISCONNECT.register { _, _ -> left = true }
        ClientLevelEvents.AFTER_CLIENT_LEVEL_CHANGE.register { _, _ ->
            Packets.worldChanged()
            readouts.worldChanged()
            game.post(System.currentTimeMillis(), WorldChanged)
            tabs.forget()
            entities.forget()
            sidebars.forget()
        }
        ClientChunkEvents.CHUNK_LOAD.register { _, chunk -> Packets.chunk(chunk) }
        Ticks.start { leave() }
        Ticks.end {
            if (ticks % TAB_POLL_TICKS == 0) {
                game.metrics.time("tab-poll") { tabs.poll(it) }
                game.metrics.time("sidebar-poll") { sidebars.poll(it) }
            }
            game.metrics.time("container-poll") { containers.poll(it) }
            game.metrics.time("entities") { entities.track(it) }
            Frames.tick(it, game)
            Packets.tick(it)
            game.tick(System.currentTimeMillis())
            readouts.push()
            InventoryKey.tick(it)
            Contribute.tick(System.currentTimeMillis())
            if (++ticks % FLUSH_TICKS == 0) {
                saved.flush()
                game.metrics.flush()
            }
            Recorder.endedClientTick()
        }
        ClientLifecycleEvents.CLIENT_STOPPING.register {
            Recording.quit()
            Recorder.quit()
            Contribute.quit()
            saved.close()
        }
        // @keep `/du` is DragSim Utils' command, and a player may run both mods.
        ClientCommandRegistrationCallback.EVENT.register { dispatcher, _ ->
            for (name in listOf("dsmod", "dsm")) dispatcher.register(
                ClientCommands.literal(name)
                    .executes { context ->
                        // @keep The chat screen closes after the command returns, so open on the next tick.
                        context.source.client.schedule { openSettings() }
                        1
                    }
                    .then(
                        ClientCommands.argument("search", StringArgumentType.greedyString()).executes { context ->
                            val search = StringArgumentType.getString(context, "search")
                            context.source.client.schedule { openSettings(search = search) }
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
                    .then(Profiles.dsmod())
                    .then(ItemOverviews.dsmod())
                    .then(WaypointChat.dsmod("waypoint"))
                    .then(WaypointChat.dsmod("wp")),
            )
            Profiles.register(dispatcher)
        }
    }
}
