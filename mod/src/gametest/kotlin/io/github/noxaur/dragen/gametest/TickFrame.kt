package io.github.noxaur.dragen.gametest

/** @keep Vanilla's menu blur takes 150 ms of each frame on CI's software renderer, three times the rest of a frame. A
 * screenshot keeps it, because Fabric draws the screenshot's frame outside `renderFrame`. */
object TickFrame {
    @JvmField
    var drawing = false
}
