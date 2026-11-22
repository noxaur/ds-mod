package io.github.noxaur.dsmod.edge

import com.mojang.blaze3d.platform.InputConstants
import com.mojang.brigadier.arguments.StringArgumentType
import io.github.noxaur.dsmod.core.config.Canvas
import io.github.noxaur.dsmod.core.config.ConfigFile
import io.github.noxaur.dsmod.core.config.DsModConfig
import io.github.noxaur.dsmod.core.config.Opens
import io.github.noxaur.dsmod.core.config.Panel
import io.github.noxaur.dsmod.core.config.SettingsTree
import io.github.noxaur.dsmod.core.feature.HasFeature
import io.github.noxaur.dsmod.core.feature.Game
import io.github.noxaur.dsmod.core.feature.contribute.ReportGameData
import io.github.noxaur.dsmod.core.game.DragSim
import io.github.noxaur.dsmod.core.game.LeftServer
import io.github.noxaur.dsmod.core.game.WorldChanged
import io.github.noxaur.dsmod.core.text.text
import io.github.noxaur.dsmod.core.ui.MenuTab
import io.github.noxaur.dsmod.edge.capture.Recorder
import io.github.noxaur.dsmod.edge.capture.Recording
import io.github.noxaur.dsmod.edge.contribute.Contribute
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
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents
import net.fabricmc.fabric.api.client.rendering.v1.PictureInPictureRendererRegistry
import net.fabricmc.loader.api.FabricLoader
import net.minecraft.client.Minecraft
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
    lateinit var game: Game
        private set
    lateinit var readouts: Readouts
        private set
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

    fun leave() {
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

    var anyServer = false

    private var byType = emptyMap<Class<out HasFeature>, HasFeature>()

    fun runs(type: Class<out HasFeature>): Boolean = byType[type]?.feature?.runs == true

    inline fun <reified F : HasFeature> runs() = runs(F::class.java)

    fun <F : HasFeature> feature(type: Class<F>): F = type.cast(byType.getValue(type))

    inline fun <reified F : HasFeature> feature(): F = feature(F::class.java)

    fun openSettings(id: String? = null, search: String? = null) =
        Minecraft.getInstance().let { it.setScreenAndShow(SettingsScreen(it.gui.screen(), reveal = id, query = search)) }

    fun saveSettings() {
        runCatching { file.save(settings) }.onFailure { log.error("Could not save the settings", it) }
    }

    /** @keep The settings open on the next tick, after the game has handed out the typed character, or the search would take it. */
    fun pressed(key: InputConstants.Key) {
        if (Keys.matches(key, settings.general.settingsKey)) Minecraft.getInstance().schedule { openSettings() }
        if (Keys.matches(key, settings.hud.editKey)) Minecraft.getInstance().schedule { openHudEditor() }
    }

    fun openHudEditor() =
        Minecraft.getInstance().let { it.setScreenAndShow(HudEditorScreen(readouts, settings.hud, it.gui.screen(), ::saveSettings)) }

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

    private fun start() {
        val tree = SettingsTree(settings)
        game = Game(tree.features) { anyServer || onDragSim }
        byType = tree.owners.associateBy { it.javaClass }
        saved = SavedFiles(FabricLoader.getInstance().configDir.resolve("dsmod/data"), game).also { it.load() }
        readouts = Readouts(game, settings.hud)
        tabs = TabListPoller(game)
        sidebars = SidebarPoller(game)
        containers = ContainerPoller(game)
        entities = EntityTracker(game)
        game.metrics.sinks += { snapshot ->
            snapshot.failures.forEach { (name, failure) -> log.error("{} failed {} times, first with {}", name, failure.count, failure.first) }
        }
    }

    fun restart(between: () -> Unit = {}) {
        saved.close()
        between()
        start()
    }

    override fun onInitializeClient() {
        file = ConfigFile(FabricLoader.getInstance().configDir.resolve("dsmod/config.json"))
        settings = file.load().apply { favorites = SettingsTree.favorites(favorites) }
        warnUnreadable()
        start()
        Kit.register()
        Looks.register()
        SlotArts.register()
        Hud.register()
        Slots.register()
        MenuSkin.register()
        ForgeCrafts.register()
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
        ScreenHooks.onEveryLayout<AbstractContainerScreen<*>> { screen, _ -> readouts.opened(screen) }
        ChatDrags.register(settings.hud, ::saveSettings)
        Editors.register(Canvas.HUD_LAYOUT) { panel, host -> @Suppress("UNCHECKED_CAST") HudLayoutEditor(panel as Panel<Map<String, DsModConfig.Position>>, readouts, host) }
        Editors.register(Canvas.MENU_TABS) { panel, host -> @Suppress("UNCHECKED_CAST") TabPlacesEditor(panel as Panel<List<MenuTab>>, host) }
        Editors.register(Canvas.PLAYER_MODEL) { _, _ -> PlayerPreview() }
        Editors.register(Canvas.HELD_ITEM) { _, _ -> HeldItemPreview() }
        PictureInPictureRendererRegistry.register { HeldItemPictureRenderer() }
        Opens.register(Opens.HUD_EDITOR, ::openHudEditor)
        Opens.register(Opens.REQUESTS, Contribute::open)
        Recorder.register()
        Recording.register()
        Contribute.register()
        Profiles.registerNames()

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
            World.worldChanged()
            game.post(System.currentTimeMillis(), WorldChanged)
            tabs.forget()
            entities.forget()
            sidebars.forget()
        }
        ClientChunkEvents.CHUNK_LOAD.register { _, chunk -> Packets.chunk(chunk) }
        ClientTickEvents.START_CLIENT_TICK.register { leave() }
        ClientTickEvents.END_CLIENT_TICK.register {
            if (ticks % TAB_POLL_TICKS == 0) {
                game.metrics.time("tab-poll") { tabs.poll(it) }
                game.metrics.time("sidebar-poll") { sidebars.poll(it) }
            }
            game.metrics.time("container-poll") { containers.poll(it) }
            game.metrics.time("entities") { entities.track(it) }
            Frames.tick(it)
            Packets.tick(it)
            game.tick(System.currentTimeMillis())
            readouts.push()
            InventoryKey.tick(it)
            Contribute.tick(game, runs<ReportGameData>(), System.currentTimeMillis())
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
                    .then(Profiles.dsmod()),
            )
            Profiles.register(dispatcher)
        }
    }
}
