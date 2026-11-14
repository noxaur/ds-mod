package io.github.noxaur.dsmod.edge

import io.github.noxaur.dsmod.core.config.DsModConfig
import io.github.noxaur.dsmod.core.game.ProtectorRow
import io.github.noxaur.dsmod.core.text.text
import io.github.noxaur.dsmod.core.ui.Section
import io.github.noxaur.dsmod.core.ui.Sections
import io.github.noxaur.dsmod.edge.ui.Kit
import io.github.noxaur.dsmod.edge.ui.KitButton
import io.github.noxaur.dsmod.edge.ui.KitCheckbox
import io.github.noxaur.dsmod.edge.ui.KitRangeSlider
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.screens.Screen

private const val TITLE = "Beam Timing"
private const val WIDTH = 300
private const val LABEL_HEIGHT = 8
private const val MAX = ProtectorRow.CYCLE_KILLS

// The server raises the Protector one tier for every 50 kills.
private val TIERS = listOf(50, 100, 150)
private val TIER_NAMES = listOf("Tier 1", "Tier 2", "Tier 3", "Tier 4")
private val DEFAULT = listOf(Section(175, 200))

/**
 * Picks the parts of the Protector's cycle that show Boss Beacon's beam: any number of sections of the 200
 * zealot kills between two Protectors, and the fight itself. The screen says little, because the slider's
 * cursor and the line under it say what a press does and did. Closing saves through [onSave] and goes back to
 * [parent], which is the settings when the player came from there.
 */
class BeaconScreen(private val settings: DsModConfig.End, private val parent: Screen?, private val onSave: () -> Unit) :
    Screen(text { body(TITLE) }.toComponent()) {
    private var panelX = 0
    private var panelY = 0
    private var panelHeight = 0
    private var left = 0
    private var top = 0
    private var tiersY = 0
    private var readoutY = 0
    private var readout = ""
    private var tierX = IntArray(0)
    private lateinit var slider: KitRangeSlider
    private lateinit var reset: KitButton

    override fun init() {
        val panelWidth = WIDTH + Kit.INSET * 2
        panelHeight = Kit.INSET * 2 + Kit.BUTTON_HEIGHT * 3 + LABEL_HEIGHT * 2 + Kit.GAP * 5 + 2
        panelX = (width - panelWidth) / 2
        panelY = (height - panelHeight) / 2
        left = panelX + Kit.INSET
        top = panelY + Kit.INSET
        val right = left + WIDTH
        val doneWidth = font.width("Done") + 16
        addRenderableWidget(KitButton(right - doneWidth, top, "Done", ::onClose, doneWidth))

        val sliderY = top + Kit.BUTTON_HEIGHT + Kit.GAP
        slider = addRenderableWidget(KitRangeSlider(left, sliderY, WIDTH, TITLE, MAX, TIERS, settings.beamSections) {
            settings.beamSections = slider.sections
            refresh()
        })
        tiersY = sliderY + Kit.BUTTON_HEIGHT + 2
        // Each tier's name sits in the middle of its quarter of the track.
        tierX = IntArray(TIER_NAMES.size) { left + WIDTH * (2 * it + 1) / 8 - font.width(TIER_NAMES[it]) / 2 }
        readoutY = tiersY + LABEL_HEIGHT + Kit.GAP * 2

        val bottom = readoutY + LABEL_HEIGHT + Kit.GAP * 2
        val fight = addRenderableWidget(
            KitCheckbox(left, bottom + (Kit.BUTTON_HEIGHT - Kit.CHECKBOX_SIZE) / 2, "During the Fight", settings.beamInFight) {
                settings.beamInFight = it
                refresh()
            },
        )
        val resetWidth = font.width("Reset") + 16
        reset = addRenderableWidget(
            KitButton(right - resetWidth, bottom, "Reset", {
                settings.beamSections = DEFAULT
                settings.beamInFight = false
                fight.checked = false
                slider.sections = DEFAULT
                refresh()
            }, resetWidth),
        )
        refresh()
    }

    override fun onClose() = minecraft.gui.setScreen(parent)

    override fun removed() = onSave()

    override fun extractRenderState(graphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, partialTick: Float) {
        Kit.panel(graphics, panelX, panelY, WIDTH + Kit.INSET * 2, panelHeight)
        Kit.label(graphics, TITLE, left, top + (Kit.BUTTON_HEIGHT - LABEL_HEIGHT) / 2)
        for (i in TIER_NAMES.indices) Kit.label(graphics, TIER_NAMES[i], tierX[i], tiersY, Kit.Ink.MUTED)
        Kit.label(graphics, readout, left, readoutY, if (slider.sections.isEmpty()) Kit.Ink.MUTED else Kit.Ink.NORMAL)
        super.extractRenderState(graphics, mouseX, mouseY, partialTick)
    }

    /** The section last touched, or all of them together, in zealots killed and as a share of the cycle. */
    private fun refresh() {
        val sections = slider.sections
        val section = sections.getOrNull(slider.selected)
        readout = when {
            section != null -> "${section.from} to ${section.to} of $MAX kills (${percent(section.from)} to ${percent(section.to)})"
            sections.isEmpty() -> "Drag on the bar to add a section."
            else -> Sections.covered(sections).let { "$it of $MAX kills (${percent(it)})" }
        }
        reset.active = sections != DEFAULT || settings.beamInFight
    }

    private fun percent(kills: Int) = "${(kills * 100 + MAX / 2) / MAX}%"
}
