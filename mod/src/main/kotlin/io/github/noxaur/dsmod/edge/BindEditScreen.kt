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

private const val WIDTH = 320
private const val COLUMN = (WIDTH - Kit.GAP * 2) / 2
private const val LABEL_HEIGHT = 8
private const val FIELD = LABEL_HEIGHT + 2 + Kit.BUTTON_HEIGHT
private const val MAX_PRIORITY = 9
private val AREAS = Area.entries - Area.ANYWHERE

/**
 * Edits one bind, as SkyOcean's hotkey editor does: its name, keys, command, group, where it works, its
 * priority, whether it is on, how its keys match, and the areas it is limited to. Nothing changes until Save,
 * which hands the bind to [onSave]. Cancel and Escape go back to [parent] without it. The line above the buttons
 * reads back what the bind will do, or what stops it. [others] are the binds it may clash with.
 */
class BindEditScreen(
    private val parent: Screen,
    original: CommandBind,
    private val isNew: Boolean,
    private val groups: List<String>,
    private val others: List<CommandBind>,
    private val onSave: (CommandBind) -> Unit,
) : Screen(text { body(if (isNew) "New Bind" else "Edit Bind") }.toComponent()) {
    private var bind = original
    private var panelX = 0
    private var panelY = 0
    private var panelHeight = 0
    private var left = 0
    private var right = 0
    private var top = 0
    private var captions = emptyList<Triple<String, Int, Int>>()
    private var readoutY = 0
    private var readout = ""
    private var bad = false
    private lateinit var keys: KitKeysButton
    private lateinit var save: KitButton

    override fun init() {
        panelHeight = Kit.INSET * 2 + Kit.BUTTON_HEIGHT * 2 + FIELD * 3 + Kit.CHECKBOX_SIZE * 3 + LABEL_HEIGHT * 2 + Kit.GAP * 12
        panelX = (width - WIDTH - Kit.INSET * 2) / 2
        panelY = (height - panelHeight) / 2
        left = panelX + Kit.INSET
        right = left + COLUMN + Kit.GAP * 2
        top = panelY + Kit.INSET
        val captions = ArrayList<Triple<String, Int, Int>>()
        fun caption(text: String, x: Int, y: Int): Int {
            captions += Triple(text, x, y)
            return y + LABEL_HEIGHT + 2
        }

        var y = top + Kit.BUTTON_HEIGHT + Kit.GAP
        val name = addRenderableWidget(KitTextField(left, caption("Name", left, y), COLUMN, "Name", bind.name, CommandBind.MAX_LENGTH) { change(bind.copy(name = it)) })
        name.setHint(text { muted("Warp to The End") }.toComponent())
        val clearX = right + COLUMN - Kit.BUTTON_HEIGHT
        keys = addRenderableWidget(KitKeysButton(right, caption("Keys", right, y), COLUMN - Kit.BUTTON_HEIGHT - Kit.GAP, "Keys", bind.keys, CommandBind.MAX_KEYS) { change(bind.copy(keys = it)) })
        keys.inOrder = bind.inOrder
        addRenderableWidget(KitButton(clearX, y + LABEL_HEIGHT + 2, Icon.CROSS, "Clear Keys") { keys.clear() })

        y += FIELD + Kit.GAP
        val command = addRenderableWidget(KitTextField(left, caption("Command", left, y), COLUMN, "Command", bind.command, CommandBind.MAX_LENGTH) { change(bind.copy(command = it)) })
        command.setHint(text { muted("/warp end") }.toComponent())
        addRenderableWidget(KitCycleButton(right, caption("Group", right, y), COLUMN, "Group", groups, bind.group, ::groupName) { change(bind.copy(group = it)) })

        y += FIELD + Kit.GAP
        addRenderableWidget(KitCycleButton(left, caption("Where", left, y), COLUMN, "Where", Where.entries, bind.where, Where::title) { change(bind.copy(where = it)) })
        val stepper = addRenderableWidget(KitStepper(right, caption("Priority", right, y), "Priority", 30) { step ->
            change(bind.copy(priority = (bind.priority + step).coerceIn(-MAX_PRIORITY, MAX_PRIORITY)))
        })
        stepper.value = bind.priority.toString()

        y += FIELD + Kit.GAP * 2
        var x = left
        for ((label, checked, set) in listOf<Triple<String, Boolean, (Boolean) -> CommandBind>>(
            Triple("On", bind.enabled) { bind.copy(enabled = it) },
            Triple("Other Keys Held Too", bind.extraKeys) { bind.copy(extraKeys = it) },
            Triple("Keys in Order", bind.inOrder) { bind.copy(inOrder = it) },
        )) {
            val box = addRenderableWidget(KitCheckbox(x, y, label, checked) { change(set(it)) })
            x += box.width + Kit.GAP * 3
        }

        y += Kit.CHECKBOX_SIZE + Kit.GAP * 2
        y = caption("Only In (none means anywhere)", left, y)
        for ((i, area) in AREAS.withIndex()) {
            val column = i % 3
            addRenderableWidget(
                KitCheckbox(left + column * (WIDTH / 3), y + i / 3 * (Kit.CHECKBOX_SIZE + Kit.GAP), area.title, area in bind.areas) {
                    change(bind.copy(areas = AREAS.filter { a -> if (a == area) it else a in bind.areas }))
                },
            )
        }
        this.captions = captions
        readoutY = y + (Kit.CHECKBOX_SIZE + Kit.GAP) * 2 + Kit.GAP

        val bottom = panelY + panelHeight - Kit.INSET - Kit.BUTTON_HEIGHT
        val saveWidth = font.width("Save") + 16
        save = addRenderableWidget(KitButton(left + WIDTH - saveWidth, bottom, "Save", ::confirm, saveWidth))
        val cancelWidth = font.width("Cancel") + 16
        addRenderableWidget(KitButton(save.x - Kit.GAP - cancelWidth, bottom, "Cancel", ::onClose, cancelWidth))
        if (isNew) setInitialFocus(name)
        refresh()
    }

    private fun change(next: CommandBind) {
        bind = next
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
        readout = when {
            keys.recording -> if (bind.inOrder) "Press the keys in turn, then Escape." else "Hold the keys together, then let go."
            bind.message == null -> "Type a command, such as /warp end, or a chat line."
            bind.keys.isEmpty() -> "Press Keys, then the keys this bind uses."
            clash != null -> "$keyNames also fires ${clash.title}. ${if (CommandBinds.first(listOf(bind, clash)) === bind) "This one" else "That one"} goes first."
            game != null -> "$keyNames is also the game's $game key."
            !bind.enabled -> "Off. Tick On to use it."
            else -> "$keyNames ${describe(bind)}."
        }
        readout = fit(readout, WIDTH)
        save.active = !bad
    }

    private fun confirm() {
        if (bad) return
        onSave(bind)
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
        for ((caption, x, y) in captions) Kit.label(graphics, caption, x, y, Kit.Ink.MUTED)
        Kit.label(graphics, readout, left, readoutY, if (bad) Kit.Ink.MUTED else Kit.Ink.NORMAL)
        super.extractRenderState(graphics, mouseX, mouseY, partialTick)
    }

    companion object {
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
