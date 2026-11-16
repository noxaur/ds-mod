package io.github.noxaur.dsmod.edge

import com.mojang.blaze3d.platform.InputConstants
import io.github.noxaur.dsmod.core.camera.KeyMode
import io.github.noxaur.dsmod.core.feature.general.InventoryHud
import net.minecraft.client.Minecraft

/**
 * Inventory HUD's key. With none bound the grid always shows. Under Hold it shows while the key is down, and under
 * Toggle each press shows or hides it, starting shown. The HUD editor always shows it, so the player can place it.
 */
object InventoryKey {
    private const val ID = "inventory-hud"

    private val settings get() = DsModClient.settings.general

    /** A press since the last tick. A tap can start and end inside one tick, so the press comes from the game's key event. */
    private var tapped = false

    /** Whether the key was down a tick ago. A key held down repeats its event, and a repeat is not a new press. */
    private var wasDown = false
    private var holding = false
    private var toggled = true

    /** The game's own key event, which it sends only with no screen open, so a key typed into chat is not a press. */
    fun pressed(key: InputConstants.Key) {
        if (Keys.matches(key, settings.inventoryKey)) tapped = true
    }

    /** Call once a tick. */
    fun tick(client: Minecraft) {
        val on = DsModClient.runs<InventoryHud>()
        val key = settings.inventoryKey
        val down = Keys.held(client, key)
        val pressed = tapped && !wasDown
        tapped = false
        wasDown = down
        // Hold lasts from the press to the release, also when a screen opened between them.
        holding = on && (pressed || holding && down)
        if (!on) toggled = true else if (pressed) toggled = !toggled
        val shown = key < 0 || client.gui.screen() is HudEditorScreen || if (settings.inventoryKeyMode == KeyMode.HOLD) holding else toggled
        Hud.conceal(ID, !shown)
    }
}
