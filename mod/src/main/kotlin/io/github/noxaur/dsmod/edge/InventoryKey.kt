package io.github.noxaur.dsmod.edge

import com.mojang.blaze3d.platform.InputConstants
import io.github.noxaur.dsmod.core.camera.KeyMode
import io.github.noxaur.dsmod.core.feature.hud.inventoryhud.InventoryHud
import net.minecraft.client.Minecraft

object InventoryKey {
    private const val ID = "inventory-hud"

    private lateinit var settings: InventoryHud

    fun bind(inventoryHud: InventoryHud) {
        settings = inventoryHud
    }

    private var tapped = false

    private var wasDown = false
    private var holding = false
    private var toggled = true

    /** @keep The game's own key event, which it sends only with no screen open, so a key typed into chat is not a press. */
    fun pressed(key: InputConstants.Key) {
        if (Keys.matches(key, settings.key.value)) tapped = true
    }

    fun tick(client: Minecraft) {
        val settings = settings
        val on = settings.feature.runs
        val key = settings.key.value
        val down = Keys.held(client, key)
        val pressed = tapped && !wasDown
        tapped = false
        wasDown = down
        holding = on && (pressed || holding && down)
        if (!on) toggled = true else if (pressed) toggled = !toggled
        val shown = key < 0 || client.gui.screen() is HudEditorScreen || if (settings.keyMode.value == KeyMode.HOLD) holding else toggled
        Hud.conceal(ID, !shown)
    }
}
