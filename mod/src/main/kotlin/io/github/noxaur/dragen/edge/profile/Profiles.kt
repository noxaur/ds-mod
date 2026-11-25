package io.github.noxaur.dragen.edge.profile

import io.github.noxaur.dragen.core.feature.Game
import com.mojang.brigadier.CommandDispatcher
import com.mojang.brigadier.StringReader
import com.mojang.brigadier.arguments.ArgumentType
import com.mojang.brigadier.builder.LiteralArgumentBuilder
import com.mojang.brigadier.context.CommandContext
import com.mojang.brigadier.exceptions.CommandSyntaxException
import com.mojang.brigadier.suggestion.Suggestions
import com.mojang.brigadier.suggestion.SuggestionsBuilder
import io.github.noxaur.dragen.core.capture.PlayerNames
import io.github.noxaur.dragen.core.feature.utilities.chat.profileviewer.ProfileViewer
import io.github.noxaur.dragen.core.profile.Profile
import io.github.noxaur.dragen.core.profile.ProfileLookup
import io.github.noxaur.dragen.core.profile.ProfileSource
import io.github.noxaur.dragen.core.text.text
import io.github.noxaur.dragen.edge.Account
import io.github.noxaur.dragen.edge.Chat
import io.github.noxaur.dragen.edge.input.CommandLinks
import io.github.noxaur.dragen.edge.input.InputApproved
import io.github.noxaur.dragen.edge.restyle
import io.github.noxaur.dragen.edge.toComponent
import java.util.concurrent.CompletableFuture
import net.fabricmc.fabric.api.client.command.v2.ClientCommands
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents
import net.minecraft.client.Minecraft
import net.minecraft.commands.SharedSuggestionProvider
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.HoverEvent

// @keep The server has its own `/pv <vault>`, so a vault number of one or two digits is refused as an unknown command and Fabric then sends the line on to the server.
object Profiles {
    private lateinit var game: Game
    private lateinit var viewer: ProfileViewer

    fun bind(game: Game, viewer: ProfileViewer) {
        this.game = game
        this.viewer = viewer
        game.player.ask = { source.profile(Account.name, it) }
    }

    private val on get() = viewer.feature.runs

    var source: ProfileSource = object : ProfileSource {
        override fun profile(name: String, profile: String?) = Account.backend.async { profile(name, profile) }.also { answer ->
            if (name.equals(Account.name, ignoreCase = true)) answer.thenAccept { Minecraft.getInstance().execute { game.player.heard(it) } }
        }

        override fun container(snapshot: String, id: String) = Account.backend.async { container(snapshot, id) }

        override fun containers(snapshot: String) = Account.backend.async { containers(snapshot) }
    }

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
            if (overlay || !on || !viewer.clickNames.value) message else names(message)
        }
    }

    @InputApproved(reaches = ["CommandLinks.runs"])
    fun names(message: Component): Component = message.restyle(PlayerNames.inText(message.string)) { name, style ->
        style.withClickEvent(CommandLinks.runs("/dragen pv $name"))
            .withHoverEvent(HoverEvent.ShowText(text { action("Click to view "); body(name); action("'s profile") }.toComponent()))
    }

    fun dragen(): LiteralArgumentBuilder<FabricClientCommandSource> = command("pv")

    private fun command(literal: String): LiteralArgumentBuilder<FabricClientCommandSource> =
        ClientCommands.literal(literal)
            .executes { open(it, Account.name) }
            .then(ClientCommands.argument("player", PlayerName).suggests(::suggest).executes { open(it, it.getArgument("player", String::class.java)) })

    private fun open(context: CommandContext<FabricClientCommandSource>, name: String): Int {
        if (!on) {
            val why = if (game.active) "Profile Viewer is off. Turn it on in /dragen to look players up." else "Profile Viewer works only on dragsim.co."
            Chat.send(text { bad(why) })
            return 0
        }
        // @keep The chat screen closes after the command returns, so open on the next tick.
        context.source.client.schedule { open(name) }
        return 1
    }

    private fun suggest(context: CommandContext<FabricClientCommandSource>, builder: SuggestionsBuilder): CompletableFuture<Suggestions> {
        val names = context.source.client.connection?.onlinePlayers?.map { it.profile.name() }?.filter(Profile::validName).orEmpty()
        return SharedSuggestionProvider.suggest(names, builder)
    }

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
