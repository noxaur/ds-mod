package io.github.noxaur.dsmod.gametest

import io.github.noxaur.dsmod.core.feature.menus.look.menutheme.MenuTheme
import io.github.noxaur.dsmod.core.ui.MenuLook
import io.github.noxaur.dsmod.core.ui.Theme
import io.github.noxaur.dsmod.edge.DsModClient
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext
import net.minecraft.network.chat.Component
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.SimpleMenuProvider
import net.minecraft.world.entity.player.Inventory
import net.minecraft.world.inventory.AbstractContainerMenu
import net.minecraft.world.inventory.SimpleContainerData
import java.nio.file.Path

class Part(val name: String, val x: Int, val y: Int, val color: (Theme) -> Int)

object ThemedMenus {
    private const val RGB = 0xFFFFFF

    fun data(vararg values: Int) = SimpleContainerData(values.size).also { data -> values.forEachIndexed(data::set) }

    fun run(
        context: ClientGameTestContext, title: String, menu: (Int, Inventory) -> AbstractContainerMenu, parts: List<Part>, fill: (ServerPlayer) -> Unit = {},
    ) = context.worldBuilder().create().use { world ->
        onServer(world) { player ->
            player.openMenu(SimpleMenuProvider({ id, inventory, _ -> menu(id, inventory) }, Component.literal(title)))
            fill(player)
            player.containerMenu.broadcastChanges()
        }
        ServerMenus.shows(context, title)
        check(context, title, parts)
    }

    fun onServer(world: TestSingleplayerContext, block: (ServerPlayer) -> Unit) =
        world.server.runOnServer<Nothing> { server -> block(server.player) }

    fun check(context: ClientGameTestContext, title: String, parts: List<Part>) {
        val name = title.lowercase().replace(' ', '-')
        for (theme in listOf(Theme.CHARCOAL, Theme.VANILLA)) context.inTheme(theme) {
            val shot = context.takeScreenshot("themed-$name-${theme.name.lowercase()}")
            val (left, top) = context.menuCorner()
            expect(context, shot, left + 3, top + 3, theme.panel, "the panel")
            for (part in parts) expect(context, shot, left + part.x, top + part.y, part.color(theme), part.name)
        }
        try {
            look(context, MenuLook.GLASS)
            val (left, top) = context.menuCorner()
            val shot = context.takeScreenshot("glass-$name")
            check(pixel(context, shot, left + 3, top + 3) != Theme.CHARCOAL.panel and RGB) { "The $title is the solid panel under the Glass look in $shot" }
        } finally {
            look(context, MenuLook.SOLID)
        }
        checkOff(context, name, title)
        context.waitTicks(2)
    }

    fun checkOff(context: ClientGameTestContext, name: String, title: String) = themeOff(context) {
        val (left, top) = context.menuCorner()
        val shot = context.takeScreenshot("themed-$name-off")
        check(pixel(context, shot, left + 3, top + 3) != Theme.CHARCOAL.panel and RGB) { "The $title stayed themed with Menu Theme off in $shot" }
    }

    fun themeOff(context: ClientGameTestContext, test: () -> Unit) {
        context.runOnClient<Nothing> { DsModClient.feature<MenuTheme>().feature.enabled = false }
        try {
            context.waitTicks(2)
            test()
        } finally {
            context.runOnClient<Nothing> { DsModClient.feature<MenuTheme>().feature.enabled = true }
        }
    }

    private fun expect(context: ClientGameTestContext, shot: Path, x: Int, y: Int, color: Int, what: String) {
        val found = pixel(context, shot, x, y)
        check(found == color and RGB) { "%s is %06X in %s, not %06X".format(what, found, shot, color and RGB) }
    }

    fun pixel(context: ClientGameTestContext, shot: Path, x: Int, y: Int) = context.guiPixel(shot, x + 0.5, y + 0.5)

    fun look(context: ClientGameTestContext, look: MenuLook, opacity: Float = 60f) {
        context.runOnClient<Nothing> {
            DsModClient.feature<MenuTheme>().menuLook.value = look
            DsModClient.feature<MenuTheme>().glassOpacity.value = opacity
        }
        context.waitTicks(2)
    }
}
