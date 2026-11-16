package io.github.noxaur.dsmod.core.hud

/**
 * A small copy of a [screenWidth] by [screenHeight] screen, as the settings' HUD layout draws it: as big as fits
 * [room] wide and [tall] high, keeping the screen's shape. [scale] turns screen pixels into map pixels.
 */
class HudMap(val screenWidth: Int, val screenHeight: Int, room: Int, tall: Int) {
    val scale: Float = if (screenWidth <= 0 || screenHeight <= 0) 1f else minOf(room.toFloat() / screenWidth, tall.toFloat() / screenHeight)
    val width: Int = (screenWidth * scale).toInt()
    val height: Int = (screenHeight * scale).toInt()

    /** The screen's point under a point from the map's corner, one axis at a time, so the frame's hover makes nothing. */
    fun screen(along: Double) = (along / scale).toFloat()
}
