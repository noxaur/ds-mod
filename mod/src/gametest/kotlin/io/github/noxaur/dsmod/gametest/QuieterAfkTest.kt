package io.github.noxaur.dsmod.gametest

import io.github.noxaur.dsmod.core.feature.menus.quieterafk.QuieterAfk
import io.github.noxaur.dsmod.edge.DsModClient
import io.github.noxaur.dsmod.edge.QuieterAfks
import io.github.noxaur.dsmod.edge.containerHeight
import io.github.noxaur.dsmod.edge.containerLeft
import io.github.noxaur.dsmod.edge.containerTop
import io.github.noxaur.dsmod.edge.containerWidth
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen
import net.minecraft.world.item.Items
import java.nio.file.Path

class QuieterAfkTest : FabricClientGameTest {
    private val title = "${QuieterAfk.TITLE}15:00"

    private class Panel(val left: Int, val top: Int, val right: Int, val bottom: Int)

    private fun panel(context: ClientGameTestContext) = context.computeOnClient<Panel, Nothing> { client ->
        val screen = client.gui.screen() as AbstractContainerScreen<*>
        val scale = client.window.guiScale
        Panel(screen.containerLeft * scale, screen.containerTop * scale, (screen.containerLeft + screen.containerWidth) * scale, (screen.containerTop + screen.containerHeight) * scale)
    }

    private fun mean(shot: Path, inside: Boolean, panel: Panel): Double {
        val image = image(shot)
        var sum = 0L
        var count = 0
        for (y in 0 until image.height step 2) for (x in 0 until image.width step 2) {
            if ((x in panel.left until panel.right && y in panel.top until panel.bottom) != inside) continue
            sum += brightness(image.getRGB(x, y))
            count++
        }
        return sum / 3.0 / count
    }

    private fun open(context: ClientGameTestContext, world: TestSingleplayerContext) {
        ServerMenus.open(context, world, 6, title) { it.setItem(4, ServerMenus.named(Items.DRAGON_EGG, "DRAGON'S REST")) }
        context.waitTicks(5)
    }

    override fun runTest(context: ClientGameTestContext) {
        val afk = DsModClient.feature<QuieterAfk>()
        context.worldBuilder().create().use { world ->
            context.waitTicks(20)
            afk.feature.enabled = false
            open(context, world)
            val panel = panel(context)
            val off = context.takeScreenshot("quieter-afk-off")
            val world0 = mean(off, inside = false, panel)
            check(world0 > 5) { "The world beside the menu was already dark ($world0) before Quieter AFK" }

            afk.feature.enabled = true
            open(context, world)
            val lit = context.takeScreenshot("quieter-afk-lit")
            val outside = mean(lit, inside = false, panel)
            val menu = mean(lit, inside = true, panel)
            check(outside < 1) { "Quieter AFK left the screen beside the menu at brightness $outside, not black" }
            check(menu > 20) { "Quieter AFK dimmed the menu ($menu) while the mouse had just moved" }
            check(QuieterAfks.volume(1f) == 0f) { "Quieter AFK left the sound on in the AFK menu" }

            context.runOnClient<Nothing> {
                QuieterAfk::class.java.getDeclaredField("litUntil").apply { isAccessible = true }.setLong(afk, 0L)
            }
            context.waitTicks(3)
            val dim = mean(context.takeScreenshot("quieter-afk-dimmed"), inside = true, panel)
            check(dim < menu / 5) { "The menu went from $menu to $dim after the mouse sat still, not a tenth" }

            context.runOnClient<Nothing> { it.player!!.closeContainer() }
            context.waitTicks(3)
            check(QuieterAfks.volume(1f) == 1f) { "The sound stayed off after the AFK menu closed" }
        }
    }
}
