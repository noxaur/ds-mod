package io.github.noxaur.dragen.gametest

import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext
import java.awt.image.BufferedImage
import java.nio.file.Path
import javax.imageio.ImageIO
import kotlin.math.abs

fun image(shot: Path): BufferedImage = ImageIO.read(shot.toFile())

fun brightness(rgb: Int) = (rgb shr 16 and 0xFF) + (rgb shr 8 and 0xFF) + (rgb and 0xFF)

fun distance(a: Int, b: Int) = (0..16 step 8).sumOf { abs((a shr it and 0xFF) - (b shr it and 0xFF)) }

fun countNear(shot: Path, rgb: Int, middle: Boolean = false, tolerance: Int = 60): Int {
    val image = image(shot)
    val xs = if (middle) image.width / 4 until image.width * 3 / 4 else 0 until image.width
    val ys = if (middle) image.height / 4 until image.height * 3 / 4 else 0 until image.height
    return ys.sumOf { y -> xs.count { x -> distance(image.getRGB(x, y), rgb) < tolerance } }
}

fun diff(a: BufferedImage, b: BufferedImage, tolerance: Int = 0, where: (Int, Int) -> Boolean = { _, _ -> true }): Int {
    check(a.width == b.width && a.height == b.height) { "The frames are different sizes" }
    var count = 0
    for (y in 0 until a.height) for (x in 0 until a.width) {
        val p = a.getRGB(x, y)
        val q = b.getRGB(x, y)
        if (p != q && where(x, y) && (0..16 step 8).any { abs((p shr it and 0xFF) - (q shr it and 0xFF)) > tolerance }) count++
    }
    return count
}

fun ClientGameTestContext.guiPixel(shot: Path, x: Number, y: Number): Int {
    val scale = computeOnClient<Int, Nothing> { it.window.guiScale }
    return image(shot).getRGB((x.toDouble() * scale).toInt(), (y.toDouble() * scale).toInt()) and 0xFFFFFF
}
