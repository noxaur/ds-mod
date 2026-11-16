package io.github.noxaur.dsmod.edge.ui

import io.github.noxaur.dsmod.core.config.Canvas
import io.github.noxaur.dsmod.core.config.Items
import io.github.noxaur.dsmod.core.config.Panel
import io.github.noxaur.dsmod.core.config.Setting
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.narration.NarrationElementOutput
import net.minecraft.client.input.CharacterEvent
import net.minecraft.client.input.KeyEvent
import net.minecraft.client.input.MouseButtonEvent

/**
 * The face of a row that edits many values in place, such as a list of binds or the HUD's layout. It sits under its
 * row's name and description, across the row. [SettingRows] lays it out when what is shown changes, tells it where
 * the pane is with [view], draws it each frame and hands it the mouse and the keys. Coordinates in [layout] are
 * the content's, which [view]'s origin turns into the screen's.
 */
interface Editor {
    /** What an editor asks of the rows around it, in content coordinates. */
    class Host(
        /** Lays the rows out again, as when the editor grows or shrinks. */
        val relayout: () -> Unit,
        /** Scrolls the content from top to bottom into view. */
        val scrollTo: (top: Int, bottom: Int) -> Unit,
        /** Reads every row again after a change, so the changed markers and summaries follow. */
        val changed: () -> Unit,
        /** The words of the search the rows show. */
        val words: () -> List<String>,
    )

    /** Lays out [width] wide from content [y], and returns how tall it is. Called on change, never per frame. */
    fun layout(x: Int, y: Int, width: Int): Int

    /** Reads the value again after a change from outside it, such as a reset. */
    fun refresh()

    /** Where the content's top is on the screen, the stretch of the screen the pane shows, and the screen's size. */
    fun view(origin: Int, clipTop: Int, clipBottom: Int, screenWidth: Int, screenHeight: Int)

    /** Draws what shows, with the keyboard's focus when [keys], and returns the tooltip of what is under the mouse. */
    fun draw(g: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, keys: Boolean): SettingRows.Tip?

    /** Draws what sits over everything, such as an open menu. */
    fun drawOver(g: GuiGraphicsExtractor, mouseX: Int, mouseY: Int) = Unit

    /** Whether the mouse is over the editor, so a press or the wheel there is its own. */
    fun isOver(mouseX: Double, mouseY: Double): Boolean

    fun mouseClicked(event: MouseButtonEvent, doubleClick: Boolean): Boolean

    fun mouseDragged(event: MouseButtonEvent, dx: Double, dy: Double): Boolean

    fun mouseReleased(event: MouseButtonEvent): Boolean

    fun mouseScrolled(mouseX: Double, mouseY: Double, amount: Double, shift: Boolean): Boolean

    /** A key while the rows' keys are on this row. False lets Up and Down go on to the next row. */
    fun keyPressed(event: KeyEvent): Boolean

    fun keyReleased(event: KeyEvent): Boolean = false

    fun charTyped(event: CharacterEvent): Boolean = false

    /** The keys came onto this row, moving down from the row above when [fromAbove], else up from below. */
    fun enter(fromAbove: Boolean) = Unit

    /** The keys left this row, so whatever it holds open closes. */
    fun leave() = Unit

    /** Whether it holds the keys and the mouse now: an open field, menu or key button, or a drag. */
    val capturing: Boolean

    /** Says what the keys or the mouse are on, or returns false to let the row speak for itself. */
    fun narrate(output: NarrationElementOutput): Boolean

    /** The screen is going. */
    fun close() = Unit
}

/** Makes the editor of each row that has one: a list's own, and each panel's from the canvas the edge registered. */
object Editors {
    private val canvases = HashMap<Canvas, (Panel<*>, Editor.Host) -> Editor>()

    /** Gives [canvas] its face, which `DsModClient` does for each one at start. */
    fun register(canvas: Canvas, make: (Panel<*>, Editor.Host) -> Editor) {
        canvases[canvas] = make
    }

    fun of(setting: Setting, host: Editor.Host): Editor = when (setting) {
        is Items<*> -> ItemsEditor(setting, host)
        is Panel<*> -> canvases[setting.canvas]?.invoke(setting, host) ?: error("No face for ${setting.canvas}")
        else -> error("${setting.id} has no editor")
    }
}
