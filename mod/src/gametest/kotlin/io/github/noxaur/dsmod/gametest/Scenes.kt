package io.github.noxaur.dsmod.gametest

import com.google.common.collect.ImmutableMultimap
import com.mojang.authlib.GameProfile
import com.mojang.authlib.properties.Property
import com.mojang.authlib.properties.PropertyMap
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext
import net.minecraft.core.component.DataComponents
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.component.ResolvableProfile
import java.util.Base64
import java.util.UUID

val MinecraftServer.player: ServerPlayer get() = playerList.players.single()

fun ClientGameTestContext.inWorld(body: (TestSingleplayerContext) -> Unit) = worldBuilder().create().use(body)

fun TestSingleplayerContext.commands(vararg lines: String) = lines.forEach(server::runCommand)

fun TestSingleplayerContext.freezeTime(noon: Boolean = true) {
    server.runCommand("gamerule advance_time false")
    server.runCommand("gamerule advance_weather false")
    if (noon) server.runCommand("time set noon")
}

fun TestSingleplayerContext.standOnStone() {
    server.runCommand("fill -1 99 -1 1 99 1 stone")
    server.runCommand("tp @a 0.5 100 0.5 -90 0")
}

fun TestSingleplayerContext.give(stacks: Map<Int, ItemStack>) = server.runOnServer<Nothing> { server ->
    val player = server.player
    player.inventory.clearContent()
    for ((slot, stack) in stacks) player.inventory.setItem(slot, stack.copy())
    player.containerMenu.broadcastChanges()
}

object HeadSkin {
    const val SKIN = "f83159c5bb7d80454d2d646b15774b7116aabcb3af5b6777a27573fc45f7854f"
    val TEXTURES: String = Base64.getEncoder().encodeToString("{\"textures\":{\"SKIN\":{\"url\":\"http://textures.minecraft.net/texture/$SKIN\"}}}".toByteArray())

    fun profile(): ResolvableProfile = ResolvableProfile.createResolved(
        GameProfile(UUID.nameUUIDFromBytes(SKIN.toByteArray()), "", PropertyMap(ImmutableMultimap.of("textures", Property("textures", TEXTURES)))),
    )

    fun worn(stack: ItemStack?): Boolean {
        val value = stack?.get(DataComponents.PROFILE)?.partialProfile()?.properties()?.get("textures")?.firstOrNull()?.value() ?: return false
        return SKIN in String(Base64.getDecoder().decode(value))
    }
}
