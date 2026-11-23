package io.github.noxaur.dsmod.edge.ui

import io.github.noxaur.dsmod.core.config.Canvas
import io.github.noxaur.dsmod.core.config.Items
import io.github.noxaur.dsmod.core.config.Panel
import io.github.noxaur.dsmod.core.config.Preview
import io.github.noxaur.dsmod.core.config.Setting
import io.github.noxaur.dsmod.core.ui.RecentColors
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.narration.NarrationElementOutput
import net.minecraft.client.input.CharacterEvent
import net.minecraft.client.input.KeyEvent
import net.minecraft.client.input.MouseButtonEvent

interface Editor {
    class Host(
        val relayout: () -> Unit,
        val scrollTo: (top: Int, bottom: Int) -> Unit,
        val changed: () -> Unit,
        val words: () -> List<String>,
        val recent: RecentColors,
    )

    fun layout(x: Int, y: Int, width: Int): Int

    fun refresh()

    fun view(origin: Int, clipTop: Int, clipBottom: Int, screenWidth: Int, screenHeight: Int)

    fun draw(g: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, keys: Boolean): SettingRows.Tip?

    fun drawOver(g: GuiGraphicsExtractor, mouseX: Int, mouseY: Int) = Unit

    fun isOver(mouseX: Double, mouseY: Double): Boolean

    fun mouseClicked(event: MouseButtonEvent, doubleClick: Boolean): Boolean

    fun mouseDragged(event: MouseButtonEvent, dx: Double, dy: Double): Boolean

    fun mouseReleased(event: MouseButtonEvent): Boolean

    fun mouseScrolled(mouseX: Double, mouseY: Double, amount: Double, shift: Boolean): Boolean

    fun keyPressed(event: KeyEvent): Boolean

    fun keyReleased(event: KeyEvent): Boolean = false

    fun charTyped(event: CharacterEvent): Boolean = false

    fun enter(fromAbove: Boolean) = Unit

    fun leave() = Unit

    val capturing: Boolean

    val recording: Boolean get() = false

    fun narrate(output: NarrationElementOutput): Boolean

    fun close() = Unit
}

class Box {
    var x = 0
        private set
    var top = 0
        private set
    var width = 0
        private set

    var height = 0
    var origin = 0
        private set
    var clipTop = 0
        private set
    var clipBottom = 0
        private set

    fun place(x: Int, y: Int, width: Int) {
        this.x = x
        top = y
        this.width = width
    }

    fun view(origin: Int, clipTop: Int, clipBottom: Int) {
        this.origin = origin
        this.clipTop = clipTop
        this.clipBottom = clipBottom
    }

    fun isOver(mouseX: Double, mouseY: Double) = isOver(mouseX, mouseY, x, width, height)

    fun isOver(mouseX: Double, mouseY: Double, left: Int, wide: Int, tall: Int) =
        mouseY >= clipTop && mouseY < clipBottom && mouseX >= left && mouseX < left + wide && mouseY >= origin + top && mouseY < origin + top + tall
}

interface BoxEditor : Editor {
    val box: Box

    override fun view(origin: Int, clipTop: Int, clipBottom: Int, screenWidth: Int, screenHeight: Int) = box.view(origin, clipTop, clipBottom)

    override fun isOver(mouseX: Double, mouseY: Double) = box.isOver(mouseX, mouseY)
}

object Editors {
    private val canvases = HashMap<Canvas, (Setting, Editor.Host) -> Editor>()

    fun register(canvas: Canvas, make: (Setting, Editor.Host) -> Editor) {
        canvases[canvas] = make
    }

    fun of(setting: Setting, host: Editor.Host): Editor = when (setting) {
        is Items<*> -> ItemsEditor(setting, host)
        is Panel<*> -> canvases[setting.canvas]?.invoke(setting, host) ?: error("No face for ${setting.canvas}")
        is Preview -> canvases[setting.canvas]?.invoke(setting, host) ?: error("No face for ${setting.canvas}")
        else -> error("${setting.id} has no editor")
    }
}
