package io.github.noxaur.dsmod.edge

import java.util.Collections
import java.util.WeakHashMap
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents
import net.fabricmc.fabric.api.client.screen.v1.Screens
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.components.AbstractWidget
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.gui.screens.inventory.ContainerScreen

object ScreenHooks {
    private val hooks = ArrayList<(Screen, Boolean) -> Unit>()
    private val laidOut: MutableSet<Screen> = Collections.newSetFromMap(WeakHashMap())

    init {
        ScreenEvents.AFTER_INIT.register { _, screen, _, _ ->
            val opened = laidOut.add(screen)
            for (hook in hooks) hook(screen, opened)
        }
    }

    inline fun <reified S : Screen> onEveryLayout(noinline add: (screen: S, opened: Boolean) -> Unit) = onEveryLayout(S::class.java, add)

    @PublishedApi
    internal fun <S : Screen> onEveryLayout(type: Class<S>, add: (S, Boolean) -> Unit) {
        hooks += { screen, opened -> if (type.isInstance(screen)) add(type.cast(screen), opened) }
    }
}

/** @keep A panel the mod puts beside a server's menu, as Forge Craft's left of The Forge. It reads its feature on each
 * tick, follows the menu's place before each frame, and draws behind the items, so a tooltip covers it */
interface SidePanel {
    val widgets: List<AbstractWidget>
    val shown: Boolean

    fun sync()

    fun place()

    fun draw(g: GuiGraphicsExtractor, mouseX: Int, mouseY: Int)

    fun attach(screen: ContainerScreen) {
        Screens.getWidgets(screen) += widgets
        sync()
        ScreenEvents.afterTick(screen).register { sync() }
        ScreenEvents.beforeExtract(screen).register { _, _, _, _, _ -> place() }
        ScreenEvents.afterBackground(screen).register { _, graphics, mouseX, mouseY, _ -> if (shown) draw(graphics, mouseX, mouseY) }
    }
}
