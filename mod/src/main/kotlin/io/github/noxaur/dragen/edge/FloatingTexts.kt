package io.github.noxaur.dragen.edge

import io.github.noxaur.dragen.core.feature.Game
import io.github.noxaur.dragen.core.game.FloatingText
import net.minecraft.network.chat.Component

object FloatingTexts {
    private lateinit var game: Game

    fun bind(game: Game) {
        this.game = game
    }

    @JvmField
    val HIDDEN: Component = Component.empty()

    @JvmStatic
    fun hidden(text: Component?) = text === HIDDEN

    @JvmStatic
    fun edit(text: Component): Component? {
        if (!game.wants(FloatingText::class.java)) return null
        val plain = text.toText()
        if (plain.plain.isEmpty()) return null
        if (!game.post(System.currentTimeMillis(), FloatingText(plain))) return HIDDEN
        return game.replaced?.toComponent()
    }
}
