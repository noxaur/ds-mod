package io.github.noxaur.dsmod.edge

import io.github.noxaur.dsmod.core.game.Area
import io.github.noxaur.dsmod.core.input.CommandBind
import io.github.noxaur.dsmod.core.input.CommandBinds
import io.github.noxaur.dsmod.core.input.Where
import io.github.noxaur.dsmod.core.text.Role
import io.github.noxaur.dsmod.core.text.text
import io.github.noxaur.dsmod.edge.ui.Icon
import io.github.noxaur.dsmod.edge.ui.Kit
import io.github.noxaur.dsmod.edge.ui.KitButton
import io.github.noxaur.dsmod.edge.ui.KitCheckbox
import io.github.noxaur.dsmod.edge.ui.KitCycleButton
import io.github.noxaur.dsmod.edge.ui.KitKeysButton
import io.github.noxaur.dsmod.edge.ui.KitStepper
import io.github.noxaur.dsmod.edge.ui.KitTextField
import io.github.noxaur.dsmod.edge.ui.fit
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.components.events.GuiEventListener
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.input.KeyEvent
import net.minecraft.client.input.MouseButtonEvent
import org.lwjgl.glfw.GLFW

private const val WIDTH = 330
private const val LABEL_WIDTH = 52
private const val LABEL_HEIGHT = 8
private const val ROW = Kit.BUTTON_HEIGHT + Kit.GAP
private const val MAX_PRIORITY = 9
private val AREAS = Area.entries - Area.ANYWHERE

/**
 * Edits one bind. It shows what most binds need, a name, a command, keys and whether it is on, and keeps the
 * rest under More: where it works, its group, its priority, how its keys match and the areas it is limited to.
 * More starts open when the bind already uses any of those. Nothing changes until Save hands the bind to
 * [onSave], or Save as Copy hands a copy to [onCopy]. Delete asks for a second press and calls [onDelete].
 * Cancel and Escape go back to [parent] with nothing changed. The line above the buttons reads back what the
 * bind will do, or what stops it. [others] are the binds it may clash with.
 */
class BindEditScreen(
    private val parent: Screen,
    original: CommandBind,
    private val isNew: Boolean,
    private val groups: List<String>,
    private val others: List<CommandBind>,
    private val onSave: (CommandBind) -> Unit,
    private val onCopy: (CommandBind) -> Unit,
    private val onDelete: () -> Unit,
) : Screen(text { body(if (isNew) "New Bind" else "Edit Bind") }.toComponent()) {
    private var bind = original
    private var more = moreOpen || original.advanced
    private var armed = false
    private var panelX = 0
    private var panelY = 0
    private var panelHeight = 0
    private var left = 0
    private var top = 0
    private var labels = emptyList<Triple<String, Int, Int>>()
    private var readoutY = 0
    private var readout = ""
    private var bad = false
    private lateinit var keys: KitKeysButton
    private lateinit var save: KitButton
    private var delete: KitButton? = null

    override fun init() {
        val moreHeight = if (more) ROW * 2 + Kit.CHECKBOX_SIZE * 3 + Kit.GAP * 5 else 0
        panelHeight = Kit.INSET * 2 + ROW * 5 + moreHeight + LABEL_HEIGHT + Kit.GAP * 2 + Kit.BUTTON_HEIGHT
        panelX = (width - WIDTH - Kit.INSET * 2) / 2
        panelY = (height - panelHeight) / 2
        left = panelX + Kit.INSET
        top = panelY + Kit.INSET
        val right = left + WIDTH
        val controlX = left + LABEL_WIDTH
        val controlWidth = WIDTH - LABEL_WIDTH
        val labels = ArrayList<Triple<String, Int, Int>>()
        fun label(text: String, x: Int, y: Int) {
            labels += Triple(text, x, y + (Kit.BUTTON_HEIGHT - LABEL_HEIGHT) / 2)
        }

        var y = top + ROW
        label("Name", left, y)
        val name = addRenderableWidget(KitTextField(controlX, y, controlWidth, "Name", bind.name, CommandBind.MAX_LENGTH) { change(bind.copy(name = it)) })
        name.setHint(text { muted("Warp to The End") }.toComponent())
        y += ROW
        label("Command", left, y)
        val command = addRenderableWidget(KitTextField(controlX, y, controlWidth, "Command", bind.command, CommandBind.MAX_LENGTH) { change(bind.copy(command = it)) })
        command.setHint(text { muted("/warp end") }.toComponent())
        y += ROW
        label("Keys", left, y)
        keys = addRenderableWidget(KitKeysButton(controlX, y, controlWidth - Kit.BUTTON_HEIGHT - Kit.GAP, "Keys", bind.keys, CommandBind.MAX_KEYS) { change(bind.copy(keys = it)) })
        keys.inOrder = bind.inOrder
        addRenderableWidget(KitButton(right - Kit.BUTTON_HEIGHT, y, Icon.CROSS, "Clear Keys") { keys.clear() })
        y += ROW
        addRenderableWidget(KitCheckbox(controlX, y + (Kit.BUTTON_HEIGHT - Kit.CHECKBOX_SIZE) / 2, "On", bind.enabled) { change(bind.copy(enabled = it)) })
        val moreLabel = if (more) "Less" else "More"
        val moreWidth = font.width(moreLabel) + 16
        addRenderableWidget(
            KitButton(right - moreWidth, y, moreLabel, {
                more = !more
                moreOpen = more
                rebuildWidgets()
            }, moreWidth),
        )

        y += ROW
        if (more) {
            val half = (controlWidth - Kit.GAP) / 2
            label("Where", left, y)
            addRenderableWidget(KitCycleButton(controlX, y, half, "Where", Where.entries, bind.where, Where::title) { change(bind.copy(where = it)) })
            addRenderableWidget(KitCycleButton(controlX + half + Kit.GAP, y, half, "Group", groups, bind.group, { "Group: " + groupName(it) }) { change(bind.copy(group = it)) })
            y += ROW
            label("Priority", left, y)
            val stepper = addRenderableWidget(KitStepper(controlX, y, "Priority", 20) { step ->
                change(bind.copy(priority = (bind.priority + step).coerceIn(-MAX_PRIORITY, MAX_PRIORITY)))
            })
            stepper.value = bind.priority.toString()
            y += ROW
            addRenderableWidget(KitCheckbox(controlX, y, "Other Keys Held Too", bind.extraKeys) { change(bind.copy(extraKeys = it)) })
            addRenderableWidget(KitCheckbox(controlX + half + Kit.GAP, y, "Keys in Order", bind.inOrder) { change(bind.copy(inOrder = it)) })
            y += Kit.CHECKBOX_SIZE + Kit.GAP * 2
            labels += Triple("Only In", left, y + 1)
            for ((i, area) in AREAS.withIndex()) {
                addRenderableWidget(
                    KitCheckbox(controlX + i % 3 * (controlWidth / 3), y + i / 3 * (Kit.CHECKBOX_SIZE + Kit.GAP), area.title, area in bind.areas) {
                        change(bind.copy(areas = AREAS.filter { a -> if (a == area) it else a in bind.areas }))
                    },
                )
            }
            y += (Kit.CHECKBOX_SIZE + Kit.GAP) * 2 + Kit.GAP
        }
        this.labels = labels
        readoutY = y

        val bottom = panelY + panelHeight - Kit.INSET - Kit.BUTTON_HEIGHT
        val saveWidth = font.width("Save") + 16
        save = addRenderableWidget(KitButton(right - saveWidth, bottom, "Save", ::confirm, saveWidth))
        val cancelWidth = font.width("Cancel") + 16
        addRenderableWidget(KitButton(save.x - Kit.GAP - cancelWidth, bottom, "Cancel", ::onClose, cancelWidth))
        if (!isNew) {
            val deleteWidth = font.width("Delete") + 16
            delete = addRenderableWidget(KitButton(left, bottom, "Delete", ::delete, deleteWidth))
            val copyWidth = font.width("Save as Copy") + 16
            addRenderableWidget(KitButton(left + deleteWidth + Kit.GAP, bottom, "Save as Copy", ::copy, copyWidth))
        }
        if (isNew && bind.name.isEmpty()) setInitialFocus(name)
        refresh()
    }

    private fun change(next: CommandBind) {
        bind = next
        armed = false
        keys.inOrder = bind.inOrder
        children().filterIsInstance<KitStepper>().forEach { it.value = bind.priority.toString() }
        refresh()
    }

    private fun refresh() {
        val keyNames = Keys.names(bind.keys, bind.inOrder)
        val clash = CommandBinds.clashes(bind, others).firstOrNull()
        val game = bind.keys.singleOrNull()?.let(Keys::gameKey)
        keys.clash = if (clash != null) Role.BAD else if (game != null) Role.WARNING else null
        bad = bind.keys.isEmpty() || bind.message == null
        readout = fit(
            when {
                armed -> "Press Delete again to delete this bind."
                keys.recording -> if (bind.inOrder) "Press the keys in turn, then Escape." else "Hold the keys together, then let go."
                bind.message == null -> "Type a command, such as /warp end."
                bind.keys.isEmpty() -> "Press Keys, then the keys to use."
                clash != null -> "$keyNames also fires ${clash.title}."
                game != null -> "$keyNames is also the game's $game key."
                !bind.enabled -> "Off. Tick On to use it."
                else -> "$keyNames ${describe(bind)}."
            },
            WIDTH,
        )
        save.active = !bad
    }

    private fun confirm() {
        if (bad) return
        onSave(bind)
        onClose()
    }

    private fun copy() {
        if (bad) return
        onCopy(bind)
        onClose()
    }

    private fun delete() {
        if (!armed) {
            armed = true
            refresh()
            return
        }
        onDelete()
        onClose()
    }

    override fun keyPressed(event: KeyEvent): Boolean {
        if (keys.recording) {
            keys.capture(event.key())
            refresh()
            return true
        }
        if (focused is KitTextField && (event.key() == GLFW.GLFW_KEY_ENTER || event.key() == GLFW.GLFW_KEY_KP_ENTER)) {
            setFocused(null)
            return true
        }
        return super.keyPressed(event).also { refresh() }
    }

    override fun keyReleased(event: KeyEvent): Boolean {
        if (keys.recording) {
            keys.release(event.key())
            refresh()
            return true
        }
        return super.keyReleased(event)
    }

    override fun mouseClicked(event: MouseButtonEvent, doubleClick: Boolean): Boolean {
        if (keys.recording) {
            val took = keys.capture(event.button(), mouse = true)
            refresh()
            if (took || keys.isMouseOver(event.x, event.y)) return true
        }
        // A press anywhere but Delete calls a delete off.
        if (armed && delete?.isMouseOver(event.x, event.y) != true) armed = false
        return super.mouseClicked(event, doubleClick).also { refresh() }
    }

    override fun mouseReleased(event: MouseButtonEvent): Boolean {
        if (keys.recording) {
            keys.release(event.button())
            refresh()
        }
        return super.mouseReleased(event)
    }

    override fun setFocused(focused: GuiEventListener?) {
        super.setFocused(focused)
        if (::save.isInitialized) refresh()
    }

    override fun onClose() = minecraft.gui.setScreen(parent)

    override fun extractRenderState(graphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, partialTick: Float) {
        Kit.panel(graphics, panelX, panelY, WIDTH + Kit.INSET * 2, panelHeight)
        Kit.label(graphics, title.string, left, top + (Kit.BUTTON_HEIGHT - LABEL_HEIGHT) / 2)
        for ((label, x, y) in labels) Kit.label(graphics, label, x, y, Kit.Ink.MUTED)
        if (more) Kit.divider(graphics, left, panelY + Kit.INSET + ROW * 5 - Kit.GAP / 2 - 1, WIDTH)
        Kit.label(graphics, readout, left, readoutY, if (bad) Kit.Ink.MUTED else Kit.Ink.NORMAL)
        delete?.takeIf { armed }?.let { Kit.ring(graphics, it.x - 1, it.y - 1, it.width + 2, it.height + 2, Role.BAD.argb()) }
        super.extractRenderState(graphics, mouseX, mouseY, partialTick)
    }

    companion object {
        /** Whether More was left open, so the next bind opens the same way. */
        private var moreOpen = false

        private val CommandBind.advanced: Boolean
            get() = where != Where.WORLD || priority != 0 || !extraKeys || inOrder || areas.isNotEmpty()

        fun groupName(group: String) = group.ifEmpty { "General" }

        /** What a bind does, after its keys: "sends /warp end in the world, in The End". */
        fun describe(bind: CommandBind): String {
            val what = when (val message = bind.message) {
                is CommandBind.Message.Chat -> "says ${message.text}"
                else -> "sends ${bind.command.trim()}"
            }
            val where = when (bind.where) {
                Where.WORLD -> "in the world"
                Where.MENUS -> "in menus"
                Where.BOTH -> "in the world and menus"
            }
            val areas = if (bind.areas.isEmpty()) "" else ", in " + bind.areas.joinToString(" or ") { it.title }
            return "$what $where$areas"
        }
    }
}
