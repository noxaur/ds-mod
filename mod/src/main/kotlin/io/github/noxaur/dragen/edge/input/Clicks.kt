package io.github.noxaur.dragen.edge.input

import io.github.noxaur.dragen.core.input.ClickBudget
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper
import net.minecraft.client.KeyMapping
import net.minecraft.client.Minecraft
import java.util.IdentityHashMap

@InputApproved(by = "noxaur", hash = "a8735cfa7bc9")
object Clicks {
    private class Button {
        val budget = ClickBudget()
        var pressed = 0
    }

    private val buttons = IdentityHashMap<KeyMapping, Button>()

    private fun button(mapping: KeyMapping) = buttons.getOrPut(mapping, ::Button)

    fun press(mapping: KeyMapping) {
        if (mapping.isUnbound || !button(mapping).budget.admits(System.nanoTime())) return
        val key = KeyMappingHelper.getBoundKeyOf(mapping)
        for (bound in Minecraft.getInstance().options.keyMappings) if (KeyMappingHelper.getBoundKeyOf(bound) == key) button(bound).pressed++
        KeyMapping.click(key)
    }

    fun allows(mapping: KeyMapping): Boolean {
        val button = button(mapping)
        val now = System.nanoTime()
        if (button.budget.spend(now)) return true
        if (button.pressed > 0) {
            button.pressed--
            return false
        }
        button.budget.count(now)
        return true
    }

    fun settle() {
        for (button in buttons.values) button.pressed = 0
    }
}
