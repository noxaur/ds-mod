package io.github.noxaur.dsmod.core.feature.general

import io.github.noxaur.dsmod.core.config.DsModConfig
import io.github.noxaur.dsmod.core.config.Store
import io.github.noxaur.dsmod.core.feature.Feature
import io.github.noxaur.dsmod.core.feature.HasFeature
import io.github.noxaur.dsmod.core.game.Area
import io.github.noxaur.dsmod.core.hand.SwingStyle

/**
 * Shapes how the held item swings and where it sits in first person. It reads nothing from the server, so all of it
 * is `HeldItems` in the edge, with the rules in `core.hand`. The edge reads these settings each frame.
 */
class Animations(config: DsModConfig) : HasFeature {
    override val feature = Feature(config, "animations", Area.ANYWHERE, Store.GENERAL, "Shape how your held item swings and sits with the settings below.")

    val swingStyle = feature.choice("swingStyle", "Swing Style", "Pick how your held item moves when you swing.", SwingStyle.VANILLA)
    val swingSpeed = feature.number("swingSpeed", "Swing Speed", "Speed your swing up, or slow it down below 1.", 1f, 0.1f..3f, step = 0.1f, unit = "x")
    val itemSize = feature.number("itemSize", "Item Size", "Grow or shrink your held item.", 1f, 0.1f..2f, step = 0.05f, unit = "x")
    val itemX = offset("itemX", "Item X", "Move your held item out to the side, or in below zero.")
    val itemY = offset("itemY", "Item Y", "Raise your held item, or lower it below zero.")
    val itemZ = offset("itemZ", "Item Z", "Push your held item away, or bring it nearer below zero.")
    val itemYaw = angle("itemYaw", "Item Yaw", "Turn your held item left or right about its grip, in degrees.")
    val itemPitch = angle("itemPitch", "Item Pitch", "Tip your held item forward or back about its grip, in degrees.")
    val itemRoll = angle("itemRoll", "Item Roll", "Tilt your held item sideways about its grip, in degrees.")
    private val itemPosition = feature.fold("itemPosition", "Item Position", "Move and turn your held item.", itemX, itemY, itemZ, itemYaw, itemPitch, itemRoll)
    val scaleSwing = feature.switch("scaleSwing", "Scale Swing", "Make the swing reach as far as Item Size makes the item big.", default = true)
    val noSwing = feature.switch("noSwing", "No Swing", "Keep your held item still when you swing.", default = false)
    val noBowSwing = feature.switch("noBowSwing", "No Bow Swing", "Keep a bow still when you swing it, as when it shoots on a click.", default = false)
    val ignoreHaste = feature.switch("ignoreHaste", "Ignore Haste", "Swing at the same pace under Haste or Mining Fatigue.", default = false)
    val noEquipReset = feature.switch("noEquipReset", "No Equip Reset", "Keep your held item up instead of dipping after a swing or use.", default = false)
    private val swingOptions = feature.fold("swingOptions", "Swing Options", "Fine-tune how your held item swings and dips.", scaleSwing, noSwing, noBowSwing, ignoreHaste, noEquipReset)

    init {
        feature.action("resetAnimations", "Reset Animations", "Put every Animations setting back to the game's own.", button = "Reset", run = ::reset)
    }

    /** Puts every setting but the switch back to its default, so the defaults are declared once. */
    fun reset() = feature.rows.forEach { it.reset() }

    private fun offset(key: String, name: String, description: String) = feature.number(key, name, description, 0f, -1f..1f, step = 0.05f)

    private fun angle(key: String, name: String, description: String) = feature.number(key, name, description, 0f, -180f..180f, step = 1f, unit = "°")
}
