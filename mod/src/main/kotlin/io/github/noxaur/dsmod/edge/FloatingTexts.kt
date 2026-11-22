package io.github.noxaur.dsmod.edge

import io.github.noxaur.dsmod.core.game.FloatingText
import net.minecraft.network.chat.Component

object FloatingTexts {
    @JvmField
    val HIDDEN: Component = Component.empty()

    @JvmStatic
    fun edit(text: Component): Component? {
        val game = DsModClient.game
        if (!game.wants(FloatingText::class.java)) return null
        val plain = text.toText()
        if (plain.plain.isEmpty()) return null
        if (!game.post(System.currentTimeMillis(), FloatingText(plain))) return HIDDEN
        return game.replaced?.toComponent()
    }
}
