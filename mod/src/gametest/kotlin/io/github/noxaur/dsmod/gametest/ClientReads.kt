package io.github.noxaur.dsmod.gametest

import io.github.noxaur.dsmod.edge.Hud
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext
import net.minecraft.client.Minecraft

fun ClientGameTestContext.checkClient(message: String, test: (Minecraft) -> Boolean) = check(computeOnClient<Boolean, Nothing> { test(it) }) { message }

fun ClientGameTestContext.guiSize() = computeOnClient<Pair<Int, Int>, Nothing> { it.window.guiScaledWidth to it.window.guiScaledHeight }

fun ClientGameTestContext.placed(id: String) = computeOnClient<Hud.Placed, Nothing> { checkNotNull(Hud.find(id)) { "$id is not on the HUD" } }
