package io.github.noxaur.dsmod.edge.profile

import com.mojang.brigadier.CommandDispatcher
import com.mojang.brigadier.StringReader
import com.mojang.brigadier.arguments.ArgumentType
import com.mojang.brigadier.builder.LiteralArgumentBuilder
import com.mojang.brigadier.context.CommandContext
import com.mojang.brigadier.exceptions.CommandSyntaxException
import com.mojang.brigadier.suggestion.Suggestions
import com.mojang.brigadier.suggestion.SuggestionsBuilder
import io.github.noxaur.dsmod.core.capture.PlayerNames
import io.github.noxaur.dsmod.core.feature.general.ProfileViewer
import io.github.noxaur.dsmod.core.profile.Profile
import io.github.noxaur.dsmod.core.profile.ProfileLookup
import io.github.noxaur.dsmod.core.profile.ProfileSource
import io.github.noxaur.dsmod.core.text.text
import io.github.noxaur.dsmod.edge.Account
import io.github.noxaur.dsmod.edge.Chat
import io.github.noxaur.dsmod.edge.DsModClient
import io.github.noxaur.dsmod.edge.restyle
import io.github.noxaur.dsmod.edge.toComponent
import java.util.concurrent.CompletableFuture
import net.fabricmc.fabric.api.client.command.v2.ClientCommands
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents
import net.minecraft.client.Minecraft
import net.minecraft.commands.SharedSuggestionProvider
import net.minecraft.network.chat.ClickEvent
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.HoverEvent

/**
 * Profile Viewer's commands: `/pv` for the player's own profile, `/pv <name>` for anyone's, and the same under
 * `/dsmod pv`. The server has its own `/pv <vault>`, so `/pv` with a number of one or two digits is not ours: the name
 * refuses it as a command the client does not know, and Fabric then sends the line on to the server as typed. Off
 * dragsim.co, or with the setting off, `/pv` is not registered for the line at all, so it reaches the server whole.
 * A player's name in chat opens their profile when clicked.
 */
object Profiles {
    private val on get() = DsModClient.runs<ProfileViewer>()

    /** The backend, which caches every lookup for every player, so the screen asks only when the player does. */
    var source: ProfileSource = object : ProfileSource {
        override fun profile(name: String, profile: String?) = Account.backend.async { profile(name, profile) }

        override fun container(snapshot: String, id: String) = Account.backend.async { container(snapshot, id) }
    }

    /** Opens [name]'s profile over the screen shown now. Call it on the client thread. */
    fun open(name: String) {
        if (!Profile.validName(name)) return Chat.send(text { bad("$name is not a Minecraft name.") })
        val client = Minecraft.getInstance()
        val lookup = ProfileLookup(name, source) { client.execute(it) }
        client.setScreenAndShow(ProfileScreen(lookup, client.gui.screen()))
    }

    fun register(dispatcher: CommandDispatcher<FabricClientCommandSource>) {
        dispatcher.register(command("pv").requires { on })
    }

    fun registerNames() {
        ClientReceiveMessageEvents.MODIFY_GAME.register { message, overlay ->
            if (overlay || !on || !DsModClient.feature(ProfileViewer::class.java).clickNames.value) message else names(message)
        }
    }

    /**
     * [message] with each player's name in it made a link to their profile, with a hover that says so. The click runs
     * `/dsmod pv`, which is always the mod's own, so Fabric runs it here and nothing reaches the server.
     */
    fun names(message: Component): Component = message.restyle(PlayerNames.inText(message.string)) { name, style ->
        style.withClickEvent(ClickEvent.RunCommand("/dsmod pv $name"))
            .withHoverEvent(HoverEvent.ShowText(text { action("Click to view "); body(name); action("'s profile") }.toComponent()))
    }

    /** `/dsmod pv`, which says so when the setting is off instead of passing the line to the server. */
    fun dsmod(): LiteralArgumentBuilder<FabricClientCommandSource> = command("pv")

    private fun command(literal: String): LiteralArgumentBuilder<FabricClientCommandSource> =
        ClientCommands.literal(literal)
            .executes { open(it, Account.name) }
            .then(ClientCommands.argument("player", PlayerName).suggests(::suggest).executes { open(it, it.getArgument("player", String::class.java)) })

    private fun open(context: CommandContext<FabricClientCommandSource>, name: String): Int {
        if (!DsModClient.runs<ProfileViewer>()) {
            val why = if (DsModClient.active) "Profile Viewer is off. Turn it on in /dsmod to look players up." else "Profile Viewer works only on dragsim.co."
            Chat.send(text { bad(why) })
            return 0
        }
        // The chat screen closes after the command returns, so open on the next tick.
        context.source.client.schedule { open(name) }
        return 1
    }

    /** The players in the tab list, which are the ones a player most often looks up. Typing sends nothing. */
    private fun suggest(context: CommandContext<FabricClientCommandSource>, builder: SuggestionsBuilder): CompletableFuture<Suggestions> {
        val names = context.source.client.connection?.onlinePlayers?.map { it.profile.name() }?.filter(Profile::validName).orEmpty()
        return SharedSuggestionProvider.suggest(names, builder)
    }

    /** A Minecraft name. A vault number is the server's, so it reads as a command the client does not have. */
    private object PlayerName : ArgumentType<String> {
        override fun parse(reader: StringReader): String {
            val start = reader.cursor
            val word = reader.readUnquotedString()
            if (Profile.vault(word)) {
                reader.cursor = start
                throw CommandSyntaxException.BUILT_IN_EXCEPTIONS.dispatcherUnknownCommand().createWithContext(reader)
            }
            return word
        }

        override fun getExamples() = listOf("Notch", "jeb_")
    }
}
