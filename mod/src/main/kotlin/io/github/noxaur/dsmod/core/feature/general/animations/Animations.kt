package io.github.noxaur.dsmod.core.feature.general.animations

import io.github.noxaur.dsmod.core.config.Canvas
import io.github.noxaur.dsmod.core.config.DsModConfig
import io.github.noxaur.dsmod.core.config.Store
import io.github.noxaur.dsmod.core.feature.Feature
import io.github.noxaur.dsmod.core.feature.HasFeature
import io.github.noxaur.dsmod.core.game.Area
import io.github.noxaur.dsmod.core.hand.HandStyle

class Animations(config: DsModConfig) : HasFeature {
    override val feature = Feature(config, "animations", Area.ANYWHERE, Store.GENERAL, "Shape how your held item swings and sits with the settings below.")

    init {
        feature.preview("heldItemPreview", "Preview", "See your item at rest, swinging, and where it sits on screen.", Canvas.HELD_ITEM)
    }

    val style = feature.choice("swingStyle", "Style", "Hold and swing as in 1.7, as the game does today, or as a spear.", HandStyle.MODERN)

    val swingSpeed = feature.number("swingSpeed", "Speed", "Speed your swing up, or slow it down below 1.", 1f, 0.1f..3f, step = 0.1f, unit = "x")
    val turn = percent("swingTurn", "Turn", "Set how far your item pivots about its grip in a swing.")
    val reach = percent("swingReach", "Reach", "Set how far your item moves in a swing, so at 0% it only pivots.")
    val scaleSwing = feature.switch("scaleSwing", "Scale Swing", "Make the swing reach as far as Size makes the item big.", default = true)
    val noSwing = feature.switch("noSwing", "No Swing", "Keep your held item still when you swing.", default = false)
    val noBowSwing = feature.switch("noBowSwing", "No Bow Swing", "Keep a bow still when you swing it, as when it shoots on a click.", default = false)
    val ignoreHaste = feature.switch("ignoreHaste", "Ignore Haste", "Swing at the same pace under Haste or Mining Fatigue.", default = false)
    private val swing = feature.fold("swingOptions", "Swing", "Set how fast and how far your held item swings.", swingSpeed, turn, reach, scaleSwing, noSwing, noBowSwing, ignoreHaste)

    val itemSize = feature.number("itemSize", "Size", "Grow or shrink your held item.", 1f, 0.1f..2f, step = 0.05f, unit = "x")
    val itemX = offset("itemX", "X", "Move your held item right, or left below zero.", 2f)
    val itemY = offset("itemY", "Y", "Raise your held item, or lower it below zero.", 1.5f)
    val itemZ = offset("itemZ", "Z", "Push your held item away, or bring it nearer below zero.", 1f)
    val itemYaw = angle("itemYaw", "Yaw", "Turn your held item left or right about its grip, in degrees.")
    val itemPitch = angle("itemPitch", "Pitch", "Tip your held item forward or back about its grip, in degrees.")
    val itemRoll = angle("itemRoll", "Roll", "Tilt your held item sideways about its grip, in degrees.")
    val flip = feature.switch("itemFlip", "Flip", "Mirror your held item in your hand, so it points the other way.", default = false)
    val noEquipReset = feature.switch("noEquipReset", "No Equip Reset", "Keep your held item up instead of dipping after a swing or use.", default = false)
    private val held = feature.fold(
        "itemPosition", "Held Item", "Size, move, turn and mirror your held item.",
        itemSize, itemX, itemY, itemZ, itemYaw, itemPitch, itemRoll, flip, noEquipReset,
    )

    val blocking = feature.switch("oldBlocking", "1.7 Blocking", "Hold a sword up across the screen while you hold right click.", default = false)
    private val rightClick = feature.fold("rightClickOptions", "Right Click", "Block with a sword as in 1.7.", blocking)

    init {
        feature.action("resetAnimations", "Reset Animations", "Put every Animations setting back to the game's own.", button = "Reset", run = ::reset)
    }

    fun reset() = feature.rows.forEach { it.reset() }

    private fun percent(key: String, name: String, description: String) = feature.number(key, name, description, 100f, 0f..100f, step = 5f, unit = "%")

    private fun offset(key: String, name: String, description: String, reach: Float) = feature.number(key, name, description, 0f, -reach..reach, step = 0.05f)

    private fun angle(key: String, name: String, description: String) = feature.number(key, name, description, 0f, -180f..180f, step = 1f, unit = "°")
}
