package io.github.noxaur.dsmod.edge.ui

import io.github.noxaur.dsmod.core.text.text
import io.github.noxaur.dsmod.edge.toComponent
import net.minecraft.client.gui.screens.Screen

abstract class EditorScreen(title: String, private val parent: Screen?, private val onSave: () -> Unit) : Screen(text { body(title) }.toComponent()) {
    override fun onClose() = minecraft.gui.setScreen(parent)

    override fun removed() = onSave()
}
