package io.github.noxaur.dsmod.edge.input

import io.github.noxaur.dsmod.core.input.ClickBudget
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper
import net.minecraft.client.KeyMapping
import java.util.IdentityHashMap

@InputApproved(by = "noxaur", hash = "444335ca4adb")
object Clicks {
    private class Button {
        val budget = ClickBudget()
        var pressed = 0
    }

    private val buttons = IdentityHashMap<KeyMapping, Button>()

    private fun button(mapping: KeyMapping) = buttons.getOrPut(mapping, ::Button)

    fun press(mapping: KeyMapping) {
        val button = button(mapping)
        if (mapping.isUnbound || !button.budget.admits(System.nanoTime())) return
        button.pressed++
        KeyMapping.click(KeyMappingHelper.getBoundKeyOf(mapping))
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
