package io.github.noxaur.dsmod.gametest

import io.github.noxaur.dsmod.core.feature.inventory.MenuTheme
import io.github.noxaur.dsmod.core.ui.MenuLook
import io.github.noxaur.dsmod.core.ui.Theme
import io.github.noxaur.dsmod.edge.DsModClient
import io.github.noxaur.dsmod.edge.mixin.ContainerScreenAccessor
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen
import net.minecraft.network.chat.Component
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.SimpleMenuProvider
import net.minecraft.world.entity.player.Inventory
import net.minecraft.world.inventory.AbstractContainerMenu
import net.minecraft.world.inventory.SimpleContainerData
import java.nio.file.Path
import javax.imageio.ImageIO

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
        shows(context, title)
        check(context, title, parts)
    }

    fun onServer(world: TestSingleplayerContext, block: (ServerPlayer) -> Unit) =
        world.server.runOnServer<Nothing> { server -> block(server.playerList.players.first()) }

    fun shows(context: ClientGameTestContext, title: String) {
        context.waitFor { (it.gui.screen() as? AbstractContainerScreen<*>)?.title?.string == title }
        context.waitTicks(5)
    }

    fun check(context: ClientGameTestContext, title: String, parts: List<Part>) {
        val name = title.lowercase().replace(' ', '-')
        for (theme in listOf(Theme.CHARCOAL, Theme.VANILLA)) {
            theme(context, theme)
            val shot = context.takeScreenshot("themed-$name-${theme.name.lowercase()}")
            val (left, top) = corner(context)
            expect(context, shot, left + 3, top + 3, theme.panel, "the panel")
            for (part in parts) expect(context, shot, left + part.x, top + part.y, part.color(theme), part.name)
        }
        theme(context, Theme.CHARCOAL)
        try {
            look(context, MenuLook.GLASS)
            val (left, top) = corner(context)
            val shot = context.takeScreenshot("glass-$name")
            check(pixel(context, shot, left + 3, top + 3) != Theme.CHARCOAL.panel and RGB) { "The $title is the solid panel under the Glass look in $shot" }
        } finally {
            look(context, MenuLook.SOLID)
        }
        context.runOnClient<Nothing> { DsModClient.feature<MenuTheme>().feature.enabled = false }
        try {
            context.waitTicks(2)
            val (left, top) = corner(context)
            val shot = context.takeScreenshot("themed-$name-off")
            check(pixel(context, shot, left + 3, top + 3) != Theme.CHARCOAL.panel and RGB) { "The $title stayed themed with Menu Theme off in $shot" }
        } finally {
            context.runOnClient<Nothing> { DsModClient.feature<MenuTheme>().feature.enabled = true }
        }
        context.waitTicks(2)
    }

    private fun expect(context: ClientGameTestContext, shot: Path, x: Int, y: Int, color: Int, what: String) {
        val found = pixel(context, shot, x, y)
        check(found == color and RGB) { "%s is %06X in %s, not %06X".format(what, found, shot, color and RGB) }
    }

    private fun pixel(context: ClientGameTestContext, shot: Path, x: Int, y: Int): Int {
        val scale = context.computeOnClient<Int, Nothing> { it.window.guiScale }
        return ImageIO.read(shot.toFile()).getRGB(x * scale + scale / 2, y * scale + scale / 2) and RGB
    }

    private fun corner(context: ClientGameTestContext) = context.computeOnClient<Pair<Int, Int>, Nothing> { client ->
        val screen = client.gui.screen() as ContainerScreenAccessor
        screen.leftPos to screen.topPos
    }

    private fun theme(context: ClientGameTestContext, theme: Theme) {
        context.runOnClient<Nothing> { DsModClient.settings.hud.theme = theme }
        context.waitTicks(2)
    }

    private fun look(context: ClientGameTestContext, look: MenuLook) {
        context.runOnClient<Nothing> {
            DsModClient.feature<MenuTheme>().menuLook.value = look
            DsModClient.feature<MenuTheme>().glassOpacity.value = 60f
        }
        context.waitTicks(2)
    }
}
