package io.github.noxaur.dsmod.edge

import io.github.noxaur.dsmod.core.config.DsModConfig
import io.github.noxaur.dsmod.core.feature.general.CommandBinds
import io.github.noxaur.dsmod.core.input.BindMatcher
import io.github.noxaur.dsmod.core.input.CommandBind
import io.github.noxaur.dsmod.core.input.CommandBind.Message
import io.github.noxaur.dsmod.core.input.Where
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.components.EditBox
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen

/**
 * Command Binds. `KeyInputMixin` and `MouseInputMixin` hand over every press and release, and [BindMatcher] decides
 * what fires. A press counts in the world, or in a menu such as a chest, and nowhere else: not in chat, on the
 * mod's own screens, with a text field focused, or with F3 held for one of the game's debug keys.
 */
object CommandKeys {
    private const val PRESS = 1
    private const val RELEASE = 0

    private lateinit var settings: DsModConfig.General
    private val matcher = BindMatcher()

    private val on: Boolean get() = DsModClient.runs<CommandBinds>()

    fun register(settings: DsModConfig.General) {
        this.settings = settings
        ClientTickEvents.END_CLIENT_TICK.register { client ->
            matcher.forget { Keys.held(client, it) }
            // Off is no place. idle drops a sequence no live bind covers in both places.
            val here = if (on) where(client) else null
            matcher.idle(System.currentTimeMillis(), settings.sequenceGap.toLong(), live(), here, DsModClient.game::isIn)?.let(::send)
        }
    }

    /** A key's GLFW code or a mouse button, and GLFW's action: press, release or repeat. A repeat is not a press. */
    fun input(key: Int, action: Int) {
        if (!::settings.isInitialized) return
        when (action) {
            RELEASE -> matcher.release(key)
            PRESS -> {
                val at = if (on) where(Minecraft.getInstance()) else null
                matcher.press(key, System.currentTimeMillis(), live(), at, DsModClient.game::isIn)?.let(::send)
            }
        }
    }

    /** The binds whose group is on. */
    private fun live() = if (settings.offGroups.isEmpty()) settings.binds else settings.binds.filter { it.group !in settings.offGroups }

    private fun where(client: Minecraft): Where? {
        if (client.player == null || client.options.keyDebugModifier.isDown) return null
        return when (val screen = client.gui.screen()) {
            null -> Where.WORLD
            is AbstractContainerScreen<*> -> Where.MENUS.takeUnless { screen.focused is EditBox }
            else -> null
        }
    }

    private fun send(bind: CommandBind) {
        val connection = Minecraft.getInstance().connection ?: return
        when (val message = bind.message) {
            is Message.Command -> connection.sendCommand(message.text)
            is Message.Chat -> connection.sendChat(message.text)
            null -> {}
        }
    }
}
