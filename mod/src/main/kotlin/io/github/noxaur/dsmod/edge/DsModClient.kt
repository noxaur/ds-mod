package io.github.noxaur.dsmod.edge

import com.mojang.blaze3d.platform.InputConstants
import com.mojang.brigadier.arguments.StringArgumentType
import io.github.noxaur.dsmod.core.config.Canvas
import io.github.noxaur.dsmod.core.config.ConfigFile
import io.github.noxaur.dsmod.core.config.DsModConfig
import io.github.noxaur.dsmod.core.config.Opens
import io.github.noxaur.dsmod.core.config.Panel
import io.github.noxaur.dsmod.core.config.SettingsTree
import io.github.noxaur.dsmod.core.feature.Feature
import io.github.noxaur.dsmod.core.feature.Game
import io.github.noxaur.dsmod.core.feature.features
import io.github.noxaur.dsmod.core.game.DragSim
import io.github.noxaur.dsmod.core.game.SystemMessage
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
import net.fabricmc.loader.api.FabricLoader
import net.minecraft.client.Minecraft
import org.slf4j.LoggerFactory
import java.net.InetAddress
import java.net.InetSocketAddress
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents

object DsModClient : ClientModInitializer {
    private const val FLUSH_TICKS = 20 * 60
    private const val TAB_POLL_TICKS = 5
    private const val PET_POLL_TICKS = 20

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
    private lateinit var pets: PetPoller
    private var ticks = 0
    private var onDragSim = false

    /** Set on the network thread when the client leaves a server, for the next client tick to tell the features. */
    @Volatile
    private var left = false

    /** dragsim.co's addresses, so a player who joins by IP is on it too. Looked up off the client thread, and empty until then. */
    @Volatile
    private var dragSimAt = emptySet<InetAddress>()

    /** Looks dragsim.co up again for the next join, so a move to a new IP is followed and a failed lookup is retried. */
    private fun lookUpDragSim() {
        Thread.ofVirtual().start { runCatching { dragSimAt = InetAddress.getAllByName(DragSim.HOST).toSet() } }
    }

    /** Lets the mod run on any server whatever the setting says. The debug build sets it, so `runClient` and the game tests run anywhere. */
    var anyServer = false

    /** Whether the mod runs on the server the player is on. */
    val active: Boolean get() = anyServer || onDragSim || !settings.general.onlyOnDragSim

    private var byType = emptyMap<Class<out Feature>, Feature>()

    /** Whether the feature of [type] runs now, which is false off dragsim.co whatever its switch says, and outside its area. */
    fun runs(type: Class<out Feature>): Boolean = byType[type]?.runs == true

    inline fun <reified F : Feature> runs() = runs(F::class.java)

    /** The feature of [type] that runs now. */
    fun <F : Feature> feature(type: Class<F>): F = type.cast(byType.getValue(type))

    /**
     * Opens the settings over the screen shown now, which Done goes back to, where the player left them: at the row
     * [id], or searched for [search], when one is given. It is the one way in, for `/dsmod`, `/dsm`, the Settings Key
     * and Mod Menu.
     */
    fun openSettings(id: String? = null, search: String? = null) =
        Minecraft.getInstance().let { it.setScreenAndShow(SettingsScreen(it.gui.screen(), reveal = id, query = search)) }

    /** Writes the settings to disk. The settings screen does it when it closes, and every editor that changes them. */
    fun saveSettings() {
        runCatching { file.save(settings) }.onFailure { log.error("Could not save the settings", it) }
    }

    /**
     * The game's own key event, which it sends only with no screen open, so a key typed into chat is not a press.
     * The settings open on the next tick, after the game has handed out the typed character, or the search would take it.
     */
    fun pressed(key: InputConstants.Key) {
        if (Keys.matches(key, settings.general.settingsKey)) Minecraft.getInstance().schedule { openSettings() }
        if (Keys.matches(key, settings.hud.editKey)) Minecraft.getInstance().schedule { openHudEditor() }
    }

    /** Each of the mod's own screens goes back to the screen it was opened over, so a button in the settings returns there. */
    fun openHudEditor() =
        Minecraft.getInstance().let { it.setScreenAndShow(HudEditorScreen(readouts, settings.hud, it.gui.screen(), ::saveSettings)) }

    /** Tells the player once, in their first world, that their settings file could not be read and where it went. */
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

    /** Builds what a launch starts from: every feature, the game's state, what the player saved, and what reads and shows them. */
    private fun start() {
        val all = features(settings)
        game = Game(all) { active }
        byType = all.associateBy { it.javaClass }
        saved = SavedFiles(FabricLoader.getInstance().configDir.resolve("dsmod/data"), game).also { it.load() }
        readouts = Readouts(game, settings.hud)
        tabs = TabListPoller(game)
        sidebars = SidebarPoller(game)
        containers = ContainerPoller(game)
        pets = PetPoller(game)
        game.metrics.sinks += { snapshot ->
            snapshot.failures.forEach { (name, failure) -> log.error("{} failed {} times, first with {}", name, failure.count, failure.first) }
        }
    }

    /**
     * Saves, runs [between], then starts again from what a launch loads, so no feature keeps what it saw. The game tests
     * call it before each test, as Fabric restores the game's options, and clear the saved files in [between], so a test
     * passes whichever ran before it.
     */
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
        Slots.register(settings.inventory)
        MenuSkin.register()
        MenuBack.register()
        TabBar.register()
        World.register()
        Etherwarps.register()
        Hits.register()
        ArmorSwapGuards.register()
        EyeGuards.register()
        CopyChats.register()
        SlotGuards.register(settings.inventory)
        CommandKeys.register(settings.general)
        Pings.register(settings.general)
        ScreenEvents.AFTER_INIT.register { _, screen, _, _ -> if (screen is AbstractContainerScreen<*>) readouts.opened(screen) }
        ChatDrags.register(settings.hud, ::saveSettings)
        Editors.register(Canvas.HUD_LAYOUT) { panel, host -> @Suppress("UNCHECKED_CAST") HudLayoutEditor(panel as Panel<Map<String, DsModConfig.Position>>, readouts, host) }
        Editors.register(Canvas.MENU_TABS) { panel, host -> @Suppress("UNCHECKED_CAST") TabPlacesEditor(panel as Panel<List<MenuTab>>, host) }
        Editors.register(Canvas.PLAYER_MODEL) { _, _ -> PlayerPreview() }
        Opens.register(Opens.HUD_EDITOR, ::openHudEditor)
        Opens.register(Opens.REQUESTS, Contribute::open)
        Recorder.register()
        Recording.register()
        Contribute.register()
        Profiles.registerNames()

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
        // Fabric says so on the network thread, and the game drops the tasks queued for its thread as it disconnects.
        ClientPlayConnectionEvents.DISCONNECT.register { _, _ -> left = true }
        ClientLevelEvents.AFTER_CLIENT_LEVEL_CHANGE.register { _, _ ->
            Packets.worldChanged()
            // The end-of-tick draw runs before readouts replace the marks, so the old world must already be clear.
            World.worldChanged()
            game.post(System.currentTimeMillis(), WorldChanged)
            tabs.forget()
            sidebars.forget()
        }
        ClientChunkEvents.CHUNK_LOAD.register { _, chunk -> Packets.chunk(chunk) }
        ClientTickEvents.END_CLIENT_TICK.register {
            // The features hear it while the game still counts as on dragsim.co.
            if (left) {
                left = false
                game.post(System.currentTimeMillis(), LeftServer)
                onDragSim = false
            }
            if (ticks % TAB_POLL_TICKS == 0) {
                game.metrics.time("tab-poll") { tabs.poll(it) }
                game.metrics.time("sidebar-poll") { sidebars.poll(it) }
            }
            game.metrics.time("container-poll") { containers.poll(it) }
            // A pet changes when the player swaps it, so once a second is soon enough.
            if (ticks % PET_POLL_TICKS == 0) game.metrics.time("pet-poll") { pets.poll(it) }
            Frames.tick(it)
            Packets.tick(it)
            game.tick(System.currentTimeMillis())
            readouts.push()
            InventoryKey.tick(it)
            Contribute.tick(game, settings.contribute.reportGameData, System.currentTimeMillis())
            if (++ticks % FLUSH_TICKS == 0) {
                saved.flush()
                game.metrics.flush()
            }
            // After every listener registered before this one, so the capture's tick covers what this tick posted.
            Recorder.endedClientTick()
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
