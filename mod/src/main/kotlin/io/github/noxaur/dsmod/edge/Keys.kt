package io.github.noxaur.dsmod.edge

import com.mojang.blaze3d.platform.InputConstants
import net.minecraft.client.Minecraft
import org.lwjgl.glfw.GLFW

/** A bound key as MoulConfig's editor writes it: a GLFW key code, a mouse button from 0 to 9, or -1 for none. */
object Keys {
    private const val MOUSE_BUTTONS = 9

    fun isMouse(bound: Int) = bound in 0..MOUSE_BUTTONS

    fun held(client: Minecraft, bound: Int) = when {
        bound < 0 -> false
        isMouse(bound) -> GLFW.glfwGetMouseButton(client.window.handle(), bound) == GLFW.GLFW_PRESS
        else -> InputConstants.isKeyDown(client.window, bound)
    }

    /** Whether the game's [key] is the [bound] one. */
    fun matches(key: InputConstants.Key, bound: Int) = bound >= 0 && key.value == bound && (key.type == InputConstants.Type.MOUSE) == isMouse(bound)
}
