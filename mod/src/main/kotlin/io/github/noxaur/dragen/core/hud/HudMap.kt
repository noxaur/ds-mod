package io.github.noxaur.dragen.core.hud

class HudMap(val screenWidth: Int, val screenHeight: Int, room: Int, tall: Int) {
    val scale: Float = if (screenWidth <= 0 || screenHeight <= 0) 1f else minOf(room.toFloat() / screenWidth, tall.toFloat() / screenHeight)
    val width: Int = (screenWidth * scale).toInt()
    val height: Int = (screenHeight * scale).toInt()

    fun screen(along: Double) = (along / scale).toFloat()
}
