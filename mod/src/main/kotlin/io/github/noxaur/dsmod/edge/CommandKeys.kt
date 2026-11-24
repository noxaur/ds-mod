package io.github.noxaur.dsmod.edge

import io.github.noxaur.dsmod.core.feature.Game
import io.github.noxaur.dsmod.core.feature.binds.commandbinds.CommandBinds
import io.github.noxaur.dsmod.core.input.BindMatcher
import io.github.noxaur.dsmod.core.input.CommandBind
import io.github.noxaur.dsmod.core.input.CommandBind.Message
import io.github.noxaur.dsmod.core.input.Where
import io.github.noxaur.dsmod.edge.input.InputApproved
import io.github.noxaur.dsmod.edge.input.PlayerCommands
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.components.EditBox
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen

object CommandKeys {
    private const val PRESS = 1
    private const val RELEASE = 0

    private val matcher = BindMatcher()

    private lateinit var game: Game
    private lateinit var settings: CommandBinds

    fun bind(game: Game, commandBinds: CommandBinds) {
        this.game = game
        settings = commandBinds
    }

    private val on: Boolean get() = settings.feature.runs
    private var registered = false

    fun register() {
        registered = true
        Ticks.end { client ->
            matcher.forget { Keys.held(client, it) }
            val here = if (on) where(client) else null
            matcher.idle(System.currentTimeMillis(), settings.sequenceGap.value.toLong(), settings.live(), here, game::isIn)?.let(::send)
        }
    }

    fun input(key: Int, action: Int) {
        if (!registered) return
        when (action) {
            RELEASE -> matcher.release(key)
            PRESS -> {
                val at = if (on) where(Minecraft.getInstance()) else null
                matcher.press(key, System.currentTimeMillis(), settings.live(), at, game::isIn)?.let(::send)
            }
        }
    }

    private fun where(client: Minecraft): Where? {
        if (client.player == null || client.options.keyDebugModifier.isDown) return null
        return when (val screen = client.gui.screen()) {
            null -> Where.WORLD
            is AbstractContainerScreen<*> -> Where.MENUS.takeUnless { screen.focused is EditBox }
            else -> null
        }
    }

    @InputApproved(reaches = ["PlayerCommands.chat", "PlayerCommands.send"])
    private fun send(bind: CommandBind) {
        when (val message = bind.message) {
            is Message.Command -> PlayerCommands.send(message.text)
            is Message.Chat -> PlayerCommands.chat(message.text)
            null -> {}
        }
    }
}
