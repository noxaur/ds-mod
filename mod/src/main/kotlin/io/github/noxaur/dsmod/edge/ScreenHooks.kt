package io.github.noxaur.dsmod.edge

import java.util.Collections
import java.util.WeakHashMap
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents
import net.minecraft.client.gui.screens.Screen

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
