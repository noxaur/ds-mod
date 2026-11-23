package io.github.noxaur.dsmod.edge

import com.mojang.brigadier.arguments.IntegerArgumentType
import com.mojang.brigadier.arguments.StringArgumentType
import com.mojang.brigadier.builder.ArgumentBuilder
import com.mojang.brigadier.builder.LiteralArgumentBuilder
import com.mojang.brigadier.context.CommandContext
import io.github.noxaur.dsmod.core.feature.Game
import io.github.noxaur.dsmod.core.feature.general.Waypoints
import io.github.noxaur.dsmod.core.profile.Profile
import io.github.noxaur.dsmod.core.text.text
import io.github.noxaur.dsmod.core.waypoint.SavedWaypoint
import io.github.noxaur.dsmod.core.waypoint.WaypointText
import io.github.noxaur.dsmod.edge.input.CommandLinks
import io.github.noxaur.dsmod.edge.input.InputApproved
import io.github.noxaur.dsmod.edge.input.PlayerCommands
import net.fabricmc.fabric.api.client.command.v2.ClientCommands
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents
import net.minecraft.client.Minecraft
import net.minecraft.commands.SharedSuggestionProvider
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.HoverEvent

/** @keep Fabric runs a clicked `/dsmod waypoint show` on the client, so nothing reaches the server. */
object WaypointChat {
    private lateinit var game: Game
    private lateinit var waypoints: Waypoints
    private lateinit var saveSettings: () -> Unit
    private lateinit var openSettings: (String) -> Unit

    enum class Channel(val word: String, val command: String?) {
        CURRENT("chat", null),
        GLOBAL("global", "chat global"),
        PARTY("party", "chat party"),
        INSTANCE("instance", "chat instance"),
    }

    fun bind(game: Game, waypoints: Waypoints, saveSettings: () -> Unit, openSettings: (String) -> Unit) {
        this.game = game
        this.waypoints = waypoints
        this.saveSettings = saveSettings
        this.openSettings = openSettings
        waypoints.position = { Minecraft.getInstance().player?.blockPosition()?.let { Triple(it.x, it.y, it.z) } }
    }

    private val on get() = waypoints.feature.runs

    fun register() {
        ClientReceiveMessageEvents.MODIFY_GAME.register { message, overlay ->
            if (overlay || !on || !waypoints.chatLinks.value) message else links(message)
        }
    }

    @InputApproved(by = "noxaur", reaches = ["CommandLinks.runs"])
    fun links(message: Component): Component {
        val plain = message.string
        val found = WaypointText.find(plain)
        if (found.isEmpty()) return message
        return message.restyle(found.map { it.range }) { shown, style ->
            val waypoint = found.first { plain.substring(it.range) == shown }.waypoint
            style.withClickEvent(CommandLinks.runs(command("show", waypoint)))
                .withHoverEvent(HoverEvent.ShowText(text { action("Click to show this waypoint.") }.toComponent()))
        }
    }

    private fun command(verb: String, waypoint: SavedWaypoint) =
        "/dsmod waypoint $verb ${waypoint.x} ${waypoint.y} ${waypoint.z} \"${WaypointText.clean(waypoint.area)}\" \"${WaypointText.clean(waypoint.name)}\""

    fun dsmod(literal: String): LiteralArgumentBuilder<FabricClientCommandSource> = ClientCommands.literal(literal)
        .executes { ready(it) { client -> client.schedule { openSettings(waypoints.feature.id) } } }
        .then(
            ClientCommands.literal("add")
                .executes { add(it, "") }
                .then(ClientCommands.argument("name", StringArgumentType.greedyString()).executes { add(it, StringArgumentType.getString(it, "name")) }),
        )
        .then(ClientCommands.literal("show").then(spot(::show)))
        .then(ClientCommands.literal("save").then(spot(::save)))
        .then(shares())

    private fun shares(): LiteralArgumentBuilder<FabricClientCommandSource> {
        val share = ClientCommands.literal("share").executes { share(it, Channel.CURRENT, null, "") }
            .then(named { context, name -> share(context, Channel.CURRENT, null, name) })
        for (channel in Channel.entries) share.then(
            ClientCommands.literal(channel.word)
                .executes { share(it, channel, null, "") }
                .then(named { context, name -> share(context, channel, null, name) }),
        )
        return share.then(
            ClientCommands.literal("whisper").then(
                ClientCommands.argument("player", StringArgumentType.word())
                    .suggests { context, builder -> SharedSuggestionProvider.suggest(context.source.client.connection?.onlinePlayers?.map { it.profile.name() }.orEmpty(), builder) }
                    .executes { share(it, Channel.CURRENT, StringArgumentType.getString(it, "player"), "") }
                    .then(named { context, name -> share(context, Channel.CURRENT, StringArgumentType.getString(context, "player"), name) }),
            ),
        )
    }

    private fun named(run: (CommandContext<FabricClientCommandSource>, String) -> Int) =
        ClientCommands.argument("name", StringArgumentType.greedyString())
            .suggests { _, builder -> SharedSuggestionProvider.suggest(waypoints.names(), builder) }
            .executes { run(it, StringArgumentType.getString(it, "name")) }

    private fun spot(run: (CommandContext<FabricClientCommandSource>, SavedWaypoint) -> Int): ArgumentBuilder<FabricClientCommandSource, *> {
        fun read(context: CommandContext<FabricClientCommandSource>, area: String, name: String) = SavedWaypoint(
            WaypointText.clean(name).take(SavedWaypoint.NAME_LENGTH),
            IntegerArgumentType.getInteger(context, "x"),
            IntegerArgumentType.getInteger(context, "y"),
            IntegerArgumentType.getInteger(context, "z"),
            WaypointText.clean(area).take(SavedWaypoint.AREA_LENGTH),
        )
        return ClientCommands.argument("x", IntegerArgumentType.integer(-FAR, FAR)).then(
            ClientCommands.argument("y", IntegerArgumentType.integer(LOW, HIGH)).then(
                ClientCommands.argument("z", IntegerArgumentType.integer(-FAR, FAR))
                    .executes { run(it, read(it, "", "")) }
                    .then(
                        ClientCommands.argument("area", StringArgumentType.string())
                            .executes { run(it, read(it, StringArgumentType.getString(it, "area"), "")) }
                            .then(
                                ClientCommands.argument("name", StringArgumentType.string())
                                    .executes { run(it, read(it, StringArgumentType.getString(it, "area"), StringArgumentType.getString(it, "name"))) },
                            ),
                    ),
            ),
        )
    }

    private inline fun ready(context: CommandContext<FabricClientCommandSource>, run: (Minecraft) -> Unit): Int {
        if (!on) {
            val why = if (game.active) "Waypoints is off. Turn it on in /dsmod to use waypoints." else "Waypoints works only on dragsim.co."
            Chat.send(text { bad(why) })
            return 0
        }
        run(context.source.client)
        return 1
    }

    private fun add(context: CommandContext<FabricClientCommandSource>, name: String) = ready(context) {
        val waypoint = waypoints.here(WaypointText.clean(name).take(SavedWaypoint.NAME_LENGTH)) ?: return@ready Chat.send(text { bad("Join a world first.") })
        waypoints.save(waypoint)
        saveSettings()
        Chat.send(text { body("Saved "); value(waypoint.title); body(" at ${waypoint.x}, ${waypoint.y}, ${waypoint.z}.") })
    }

    @InputApproved(by = "noxaur", reaches = ["CommandLinks.runs"])
    private fun show(context: CommandContext<FabricClientCommandSource>, found: SavedWaypoint) = ready(context) {
        val waypoint = waypoints.styled(found)
        waypoints.show(waypoint)
        val line = text {
            body("Showing "); value(waypoint.title)
            if (!waypoint.shows(waypoints.feature.areaName)) body(" in ${waypoint.area}")
            body(". "); action("Click to save it.")
        }
        Chat.send(line.toComponent().withStyle { it.withClickEvent(CommandLinks.runs(command("save", waypoint))) })
    }

    @InputApproved(by = "noxaur", reaches = ["CommandLinks.runs"])
    private fun save(context: CommandContext<FabricClientCommandSource>, found: SavedWaypoint) = ready(context) {
        val waypoint = waypoints.styled(found)
        val added = waypoints.save(waypoint)
        if (added) saveSettings()
        val line = text {
            value(waypoint.title); body(if (added) " is saved to your waypoints. " else " is saved already. "); action("Click to edit it.")
        }
        Chat.send(line.toComponent().withStyle { it.withClickEvent(CommandLinks.runs("/dsmod waypoint")) })
    }

    @InputApproved(by = "noxaur", reaches = ["PlayerCommands.chat", "PlayerCommands.send"])
    private fun share(context: CommandContext<FabricClientCommandSource>, channel: Channel, player: String?, name: String) = ready(context) { client ->
        if (player != null && !Profile.validName(player)) return@ready Chat.send(text { bad("$player is not a Minecraft name.") })
        // @keep A name that matches no saved waypoint is refused, so a typo never says where the player stands.
        val waypoint = if (name.isBlank()) {
            waypoints.here() ?: return@ready Chat.send(text { bad("Join a world first.") })
        } else {
            waypoints.named(name) ?: return@ready Chat.send(text { bad("No waypoint is called ${name.trim()}. Save one with /dsmod waypoint add.") })
        }
        val line = WaypointText.format(waypoint)
        // @keep The chat screen sends the typed command as it closes, so the shared line goes on the next tick, after it.
        client.schedule {
            when {
                player != null -> PlayerCommands.send("whisper $player $line")
                channel.command != null -> PlayerCommands.send("${channel.command} $line")
                else -> PlayerCommands.chat(line)
            }
        }
    }

    private const val FAR = 30_000_000
    private const val LOW = -2048
    private const val HIGH = 4096
}
