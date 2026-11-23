package io.github.noxaur.dsmod.edge.overview

import com.mojang.blaze3d.platform.InputConstants
import com.mojang.brigadier.arguments.StringArgumentType
import com.mojang.brigadier.builder.LiteralArgumentBuilder
import com.mojang.brigadier.context.CommandContext
import io.github.noxaur.dsmod.core.feature.Game
import io.github.noxaur.dsmod.core.feature.general.ItemOverview
import io.github.noxaur.dsmod.core.overview.Entry
import io.github.noxaur.dsmod.core.overview.Shelf
import io.github.noxaur.dsmod.core.profile.ProfileLookup
import io.github.noxaur.dsmod.core.text.text
import io.github.noxaur.dsmod.edge.Account
import io.github.noxaur.dsmod.edge.Chat
import io.github.noxaur.dsmod.edge.Keys
import io.github.noxaur.dsmod.edge.input.InputApproved
import io.github.noxaur.dsmod.edge.input.PlayerCommands
import io.github.noxaur.dsmod.edge.profile.Profiles
import net.fabricmc.fabric.api.client.command.v2.ClientCommands
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource
import net.minecraft.client.Minecraft

object ItemOverviews {
    private lateinit var game: Game
    private lateinit var overview: ItemOverview

    fun bind(game: Game, overview: ItemOverview) {
        this.game = game
        this.overview = overview
    }

    fun pressed(key: InputConstants.Key) {
        if (overview.feature.runs && Keys.matches(key, overview.key.value)) Minecraft.getInstance().schedule { open("") }
    }

    fun dsmod(): LiteralArgumentBuilder<FabricClientCommandSource> = ClientCommands.literal("items")
        .executes { open(it, "") }
        .then(ClientCommands.argument("search", StringArgumentType.greedyString()).executes { open(it, StringArgumentType.getString(it, "search")) })

    private fun open(context: CommandContext<FabricClientCommandSource>, query: String): Int {
        if (!overview.feature.runs) {
            val why = if (game.active) "Item Overview is off. Turn it on in /dsmod to list your items." else "Item Overview works only on dragsim.co."
            Chat.send(text { bad(why) })
            return 0
        }
        // @keep The chat screen closes after the command returns, so open on the next tick.
        context.source.client.schedule { open(query) }
        return 1
    }

    fun open(query: String) {
        val client = Minecraft.getInstance()
        val lookup = ProfileLookup(Account.name, Profiles.source) { client.execute(it) }
        client.setScreenAndShow(ItemOverviewScreen(lookup, overview.copies, overview.feature.player.profile, query, client.gui.screen(), ::pick))
    }

    /** @keep A click runs the command that opens the first Storage page or Ender Chest holding the item, and marks the
     * item there. The inventory needs no command, and the server has none that opens one wardrobe or bag page. */
    @InputApproved(reaches = ["PlayerCommands.send"])
    private fun pick(entry: Entry): Boolean {
        val command = entry.places.firstNotNullOfOrNull { Shelf.command(it.id) } ?: return false
        overview.find(entry.item.key, System.currentTimeMillis())
        Minecraft.getInstance().gui.setScreen(null)
        PlayerCommands.send(command)
        return true
    }
}
