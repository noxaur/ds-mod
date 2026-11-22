package io.github.noxaur.dsmod.gametest

import com.mojang.blaze3d.platform.InputConstants
import io.github.noxaur.dsmod.core.camera.KeyMode
import io.github.noxaur.dsmod.core.feature.general.InventoryHud
import io.github.noxaur.dsmod.edge.DsModClient
import io.github.noxaur.dsmod.edge.Hud
import io.github.noxaur.dsmod.edge.InventoryKey
import io.github.noxaur.dsmod.gametest.InventoryGrid.ID
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext
import org.lwjgl.glfw.GLFW

/** @keep Covers [io.github.noxaur.dsmod.edge.mixin.KeyPressMixin]. */
class InventoryKeyTest : FabricClientGameTest {
    override fun runTest(context: ClientGameTestContext) = InventoryGrid.with(context) {
        val inventoryHud = DsModClient.feature<InventoryHud>()
        val key = GLFW.GLFW_KEY_K
        fun shown() = context.computeOnClient<Boolean, Nothing> { Hud.find(ID) != null }

        inventoryHud.key.value = key
        context.waitTick()
        check(shown()) { "Binding a key hid the grid before its first press" }
        context.input.pressKey(key)
        context.waitFor { Hud.find(ID) == null }
        context.input.holdKey(key)
        context.waitFor { Hud.find(ID) != null }
        context.runOnClient<Nothing> { InventoryKey.pressed(InputConstants.Type.KEYSYM.getOrCreate(key)) }
        context.waitTicks(2)
        check(shown()) { "A repeat of the held key toggled the grid again" }
        context.input.releaseKey(key)
        context.waitTick()
        context.input.pressKey(key)
        context.waitTicks(2)
        check(!shown()) { "Toggle's press did not hide the grid" }
        context.input.pressKey(key)
        context.waitTicks(2)
        check(shown()) { "Toggle's second press did not show the grid" }

        inventoryHud.keyMode.value = KeyMode.HOLD
        context.waitTicks(2)
        check(!shown()) { "Under Hold the grid shows with the key up" }
        context.input.holdKey(key)
        context.waitTicks(2)
        check(shown()) { "Under Hold the grid does not show while the key is down" }
        context.input.releaseKey(key)
        context.waitTicks(2)
        check(!shown()) { "Under Hold the grid stays after the release" }
        inventoryHud.key.value = -1
        context.waitTicks(2)
        check(shown()) { "With no key the grid does not always show" }
    }
}
