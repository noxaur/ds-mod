package io.github.noxaur.dsmod.edge

import com.mojang.blaze3d.platform.InputConstants
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper
import net.minecraft.client.KeyMapping
import net.minecraft.client.Minecraft
import net.minecraft.client.input.InputWithModifiers
import net.minecraft.client.input.KeyEvent
import net.minecraft.client.resources.language.I18n
import org.lwjgl.glfw.GLFW

/** A bound key as `KeyBind` holds it: a GLFW key code, a mouse button from 0 to 9, or -1 for none. */
object Keys {
    private const val MOUSE_BUTTONS = 9

    fun isMouse(bound: Int) = bound in 0..MOUSE_BUTTONS

    fun held(client: Minecraft, bound: Int) = when {
        bound < 0 -> false
        isMouse(bound) -> GLFW.glfwGetMouseButton(client.window.handle(), bound) == GLFW.GLFW_PRESS
        else -> InputConstants.isKeyDown(client.window, bound)
    }

    // A modifier held with a key or a press. The keyboard is asked too, since a game test's input carries no modifiers.

    /** Ctrl, or Cmd on macOS. */
    fun control(input: InputWithModifiers) = input.hasControlDownWithQuirk() || Minecraft.getInstance().hasControlDown()

    fun shift(input: InputWithModifiers) = input.hasShiftDown() || Minecraft.getInstance().hasShiftDown()

    fun alt(input: InputWithModifiers) = input.hasAltDown() || Minecraft.getInstance().hasAltDown()

    /** 1 for the plus keys and -1 for the minus keys, which resize a HUD block, and 0 for any other. */
    fun resizeStep(event: KeyEvent) = when (event.key()) {
        GLFW.GLFW_KEY_EQUAL, GLFW.GLFW_KEY_KP_ADD -> 1
        GLFW.GLFW_KEY_MINUS, GLFW.GLFW_KEY_KP_SUBTRACT -> -1
        else -> 0
    }

    /** What the game calls the [bound] key, such as "F6" or "Mouse Button 4", or "None". */
    fun name(bound: Int): String = when {
        bound < 0 -> "None"
        isMouse(bound) -> InputConstants.Type.MOUSE.getOrCreate(bound).displayName.string
        else -> InputConstants.Type.KEYSYM.getOrCreate(bound).displayName.string
    }

    /** Several keys as one label: "Shift + F6" held together, "F6 » F7" in order, or "None". */
    fun names(keys: List<Int>, inOrder: Boolean): String = if (keys.isEmpty()) "None" else keys.joinToString(if (inOrder) " » " else " + ", transform = ::name)

    /**
     * The name of the game's own key bind on [bound], or null. A debug key works only with F3 held, so F3 is the
     * only one of those that counts, and F6, which the game binds to F3+F6, is free.
     */
    fun gameKey(bound: Int): String? {
        val options = Minecraft.getInstance().options
        return options.keyMappings.firstOrNull {
            (it.category != KeyMapping.Category.DEBUG || it === options.keyDebugModifier) && matches(KeyMappingHelper.getBoundKeyOf(it), bound)
        }?.let { I18n.get(it.name) }
    }

    /** Whether the game's [key] is the [bound] one. */
    fun matches(key: InputConstants.Key, bound: Int) = bound >= 0 && key.value == bound && (key.type == InputConstants.Type.MOUSE) == isMouse(bound)
}
