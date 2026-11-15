package io.github.noxaur.dsmod.edge

import com.mojang.blaze3d.platform.InputConstants
import io.github.notenoughupdates.moulconfig.annotations.SearchTag
import io.github.notenoughupdates.moulconfig.common.IMinecraft
import io.github.notenoughupdates.moulconfig.common.RenderContext
import io.github.notenoughupdates.moulconfig.common.text.StructuredText
import io.github.notenoughupdates.moulconfig.gui.GuiContext
import io.github.notenoughupdates.moulconfig.gui.GuiElementComponent
import io.github.notenoughupdates.moulconfig.gui.GuiOptionEditor
import io.github.notenoughupdates.moulconfig.gui.KeyboardEvent
import io.github.notenoughupdates.moulconfig.gui.MouseEvent
import io.github.notenoughupdates.moulconfig.gui.MoulConfigEditor
import io.github.notenoughupdates.moulconfig.gui.editors.GuiOptionEditorAccordion
import io.github.notenoughupdates.moulconfig.managed.ManagedConfig
import io.github.notenoughupdates.moulconfig.platform.MoulConfigRenderContext
import io.github.notenoughupdates.moulconfig.processor.ProcessedCategory
import io.github.notenoughupdates.moulconfig.processor.ProcessedOption
import io.github.noxaur.dsmod.core.config.DsModConfig
import io.github.noxaur.dsmod.core.config.Favorites
import io.github.noxaur.dsmod.core.config.FavoritesView
import io.github.noxaur.dsmod.core.text.Role
import io.github.noxaur.dsmod.edge.ui.Icon
import io.github.noxaur.dsmod.edge.ui.Kit
import net.minecraft.client.Minecraft
import java.lang.reflect.Field
import java.lang.reflect.Type

/**
 * The settings screen. [open] is the one way in, for the Settings Key, `/dsmod`, `/dsm` and every button that
 * returns to the settings, so the screen that replaces MoulConfig's takes over there alone.
 *
 * Today it is MoulConfig's editor with a star on each setting and a Favorites category on top, both fed by
 * [Favorites]. MoulConfig has no favorites, and it takes any categories and options it is handed, so each category
 * and option is wrapped. One editor lasts the session, so the screen opens where the player left it.
 */
object Settings {
    /**
     * MoulConfig fits a setting's name within 5 of the left third's edges and starts its description 5 past that
     * third, so the 10 between them are always empty. The star sits there, beside the name's first row.
     */
    private fun starX(x: Int, width: Int) = x + width / 3 - 4

    private const val STAR_TOP = 6

    private const val OFF_ALPHA = 110

    /** The star takes a press a pixel around it, as a 7x7 target is easy to miss. */
    private val STAR_TARGET = Icon.STAR.width + 2

    /** The id of the category that lists the starred settings. */
    const val FAVORITES = "favorites"

    private lateinit var config: ManagedConfig<DsModConfig>
    private val starred: List<String> get() = config.instance.favorites
    private val view = FavoritesView()

    /** Built on the first open, because MoulConfig's editors need the game's fonts. */
    private val editor: Editor by lazy(::build)

    /**
     * The setting under the mouse this frame, which the Favorite Key and a press on the star act on. MoulConfig hands
     * mouse events to every setting, also one scrolled under the header, so only the part inside the pane counts.
     */
    private var hovered: Star? = null

    /** A press of the Favorite Key is followed by its character, which would land in the search. */
    private var eatChar = false

    fun register(config: ManagedConfig<DsModConfig>) {
        this.config = config
    }

    /** Opens the settings where the player left them, at [setting] of `DsModConfig` when it is given, or searched for [search]. */
    fun open(setting: Field? = null, search: String? = null) {
        view.left()
        if (setting != null) editor.goToOption(editor.getOptionFromField(setting)!!)
        if (search != null) editor.search(search)
        // MoulConfig tells only a component that it closed, and the editor is wrapped in one that does not pass it on,
        // so without this the editor never saves.
        IMinecraft.INSTANCE.openWrappedScreen(
            object : GuiContext(GuiElementComponent(editor)) {
                override fun onAfterClose() {
                    super.onAfterClose()
                    editor.onAfterClose()
                }
            },
        )
    }

    /**
     * The game's own key event, which it sends only with no screen open, so a key typed into chat is not a press.
     * The settings open on the next tick, after the game has handed out the typed character, or the search would take it.
     */
    fun pressed(key: InputConstants.Key) {
        if (Keys.matches(key, config.instance.general.settingsKey)) Minecraft.getInstance().schedule { open() }
    }

    // What the screen shows, in plain types for the game tests, because the jar relocates MoulConfig.

    /** The paths of the settings Favorites lists now. */
    val favorites: List<String> get() = editor.allCategories.getValue(FAVORITES).options.filter { it !is Empty }.map { it.path }

    /** The id of the category shown now. */
    val selected: String get() = editor.selectedCategory

    /** The ids of the categories listed now, which a search narrows. */
    val listed: Set<String> get() = editor.currentlyVisibleCategories.keys

    /** The id of the category that [setting] of `DsModConfig` is in. */
    fun category(setting: Field): String = editor.getOptionFromField(setting)!!.category.identifier

    /** Where the star of [setting] was last drawn, in GUI pixels. */
    fun star(setting: Field): Pair<Int, Int> = (editor.getOptionFromField(setting)!!.editor as Star).let { it.x to it.y }

    fun show(category: String) = editor.setSelectedCategory(editor.allCategories.getValue(category))

    fun search(words: String) = editor.search(words)

    /** The Favorite Key's name in a hint, or null with no key. */
    private fun keyName(): String? = config.instance.general.favoriteKey.takeIf { it >= 0 }?.let(Keys::name)

    private fun toggle(path: String) {
        config.instance.favorites = view.toggle(starred, path, here = editor.selectedCategory == FAVORITES)
    }

    private fun build(): Editor {
        val categories = LinkedHashMap<String, ProcessedCategory>()
        val every = ArrayList<ProcessedOption>()
        val favorites = FavoritesCategory { view.rows(every, starred) { it.path } }
        categories[FAVORITES] = favorites
        for ((id, real) in config.processor.allCategories) {
            val options = ArrayList<ProcessedOption>()
            val category = Wrapped(real, options)
            real.options.mapTo(options) { StarredOption(it, category) }
            every += options
            categories[id] = category
        }
        return Editor(categories).also {
            favorites.built = true
            // An empty Favorites has nothing to open on, so the first open shows the first of the player's categories.
            if (starred.isEmpty()) it.setSelectedCategory(categories.values.elementAt(1))
        }
    }

    private class Editor(categories: LinkedHashMap<String, ProcessedCategory>) : MoulConfigEditor<DsModConfig>(categories, config.instance) {
        override fun render() {
            if (selectedCategory != FAVORITES) view.left()
            hovered = null
            super.render()
        }

        override fun keyboardInput(event: KeyboardEvent): Boolean {
            if (event is KeyboardEvent.CharTyped && eatChar) return true.also { eatChar = false }
            eatChar = false
            return super.keyboardInput(event)
        }
    }

    /** One of the player's categories, holding the starred version of each of its settings. */
    private class Wrapped(real: ProcessedCategory, private val options: List<ProcessedOption>) : ProcessedCategory by real {
        override fun getOptions() = options
    }

    /**
     * The Favorites category. Its settings are the ones in the other categories, which say they belong there, so a
     * search hides Favorites and finds each setting once, in its own category, as [Favorites.listed] says. With
     * nothing starred it holds one row that says so, because MoulConfig's own words for an empty category blame a search.
     */
    private class FavoritesCategory(private val rows: () -> List<ProcessedOption>) : ProcessedCategory {
        private val empty = listOf<ProcessedOption>(Empty(this))

        /** MoulConfig shows only the options it was handed when the editor was built, so the empty row is handed over then. */
        var built = false

        override fun getDisplayName(): StructuredText = StructuredText.of("Favorites")
        override fun getDescription(): StructuredText =
            StructuredText.of(keyName()?.let { "Click a star, or press $it over a setting." } ?: "Click a setting's star to pin it here.")
        override fun getIdentifier() = FAVORITES
        override fun getParentCategoryId(): String? = null
        override fun getOptions() = if (built) rows().ifEmpty { empty } else empty + rows()
        override fun getAccordionAnchors() = emptyMap<Int, ProcessedOption>()
        override fun getDebugDeclarationLocation() = "ds-mod's favorites"
    }

    /** The row an empty Favorites shows: MoulConfig's box with a name and a line, and no control. A search never finds it. */
    private class Empty(private val category: ProcessedCategory) : ProcessedOption {
        private val editor = object : GuiOptionEditor(this) {
            override fun fulfillsSearch(word: String) = false
        }

        override fun getSearchTags() = emptyArray<SearchTag>()
        override fun getAccordionId() = -1
        override fun getEditor() = editor
        override fun getCategory() = category
        override fun getName(): StructuredText = StructuredText.of("No Favorites Yet")
        override fun getDescription(): StructuredText = StructuredText.of("The settings you star show here, in the order of their categories.")
        override fun getPath() = "$FAVORITES.empty"
        override fun getConfig(): DsModConfig = Settings.config.instance
        override fun get(): Any? = null
        override fun getType(): Type = Unit::class.java
        override fun set(value: Any?) = false
        override fun explicitNotifyChange() {}
        override fun getDebugDeclarationLocation() = "ds-mod's empty favorites"
    }

    private class StarredOption(private val real: ProcessedOption, private val category: ProcessedCategory) :
        ProcessedOption by real,
        ProcessedOption.HasField {
        // An accordion is found by its editor's class, so it keeps its own and gets no star.
        private val editor = real.editor.let { if (it is GuiOptionEditorAccordion) it else Star(it) }

        override fun getEditor(): GuiOptionEditor = editor
        override fun getCategory() = category
        override fun getField(): Field = (real as ProcessedOption.HasField).field
    }

    /**
     * A setting's own editor with a star beside its name. A press on the star, or the Favorite Key over the setting,
     * turns it on or off, and the setting's own editor sees neither.
     */
    private class Star(private val inner: GuiOptionEditor) : GuiOptionEditor(inner.option) {
        /** Where the star was last drawn, for the game test that presses it. */
        var x = 0
        var y = 0

        private var context: GuiContext? = null

        private val on: Boolean get() = option.path in starred

        /** Whether the mouse is on the star of the box at [x], [y], [width] wide. */
        private fun onStar(x: Int, y: Int, width: Int, mouseX: Int, mouseY: Int) =
            mouseX - (starX(x, width) - 1) in 0 until STAR_TARGET && mouseY - (y + STAR_TOP - 1) in 0 until STAR_TARGET

        override fun render(context: RenderContext, x: Int, y: Int, width: Int) {
            inner.activeConfigGUI = activeConfigGUI
            inner.render(context, x, y, width)
            this.x = starX(x, width)
            this.y = y + STAR_TOP
            val mc = IMinecraft.INSTANCE
            // MoulConfig draws the settings inside the pane's scissor, so the scissor is the pane. The rectangle is asked
            // itself, because the game's own containsPointInScissor answers true outside it while the settings draw.
            val pane = (context as MoulConfigRenderContext).drawContext.scissorStack.peek()
            val inPane = pane != null && pane.containsPoint(mc.mouseX, mc.mouseY)
            if (inPane && mc.mouseX - x in 0 until width && mc.mouseY - y in 0 until height) hovered = this
            val onIt = hovered === this && onStar(x, y, width, mc.mouseX, mc.mouseY)
            // An unstarred star is faint, so a row of them does not crowd the names, and lights up under the mouse.
            val color = when {
                on -> Role.ACTION.argb()
                onIt -> Role.BODY.argb()
                else -> Role.MUTED.argb(OFF_ALPHA)
            }
            Kit.icon(context, Icon.STAR, this.x, this.y, color)
            if (onIt) {
                val verb = if (on) "unstar" else "star"
                val hint = keyName()?.let { "Click or press $it to $verb." } ?: "Click to $verb."
                context.scheduleDrawTooltip(mc.mouseX, mc.mouseY, listOf(StructuredText.of(hint)))
            }
        }

        /**
         * Whether the Favorite Key may act: never while a text field has focus, nor with Ctrl or Alt held, so typing and
         * MoulConfig's Ctrl+F still work, whether the key is on the keyboard or the mouse.
         */
        private fun keyFree() = Minecraft.getInstance().let { context?.focusedElement == null && !it.hasControlDown() && !it.hasAltDown() }

        override fun mouseInput(x: Int, y: Int, width: Int, mouseX: Int, mouseY: Int, event: MouseEvent): Boolean {
            val key = config.instance.general.favoriteKey
            val press = event is MouseEvent.Click && event.mouseState
            if (event is MouseEvent.Click &&
                hovered === this &&
                (event.mouseButton == 0 && onStar(x, y, width, mouseX, mouseY) || Keys.isMouse(key) && event.mouseButton == key && keyFree())
            ) {
                if (press) toggle(option.path)
                return true
            }
            return inner.mouseInput(x, y, width, mouseX, mouseY, event)
        }

        override fun keyboardInput(event: KeyboardEvent): Boolean {
            if (inner.keyboardInput(event)) return true
            val key = config.instance.general.favoriteKey
            if (event !is KeyboardEvent.KeyPressed || !event.pressed || Keys.isMouse(key) || event.keycode != key || hovered !== this) return false
            if (!keyFree()) return false
            toggle(option.path)
            eatChar = true
            return true
        }

        override fun getHeight() = inner.height
        override fun getDescriptionHeight() = inner.descriptionHeight
        override fun mouseInputOverlay(x: Int, y: Int, width: Int, mouseX: Int, mouseY: Int, event: MouseEvent) =
            inner.mouseInputOverlay(x, y, width, mouseX, mouseY, event)
        override fun renderOverlay(context: RenderContext, x: Int, y: Int, width: Int) = inner.renderOverlay(context, x, y, width)
        override fun fulfillsSearch(word: String) = inner.fulfillsSearch(word)
        override fun setGuiContext(context: GuiContext) {
            this.context = context
            inner.setGuiContext(context)
        }
    }
}
