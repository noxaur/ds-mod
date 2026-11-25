package io.github.noxaur.dragen.gametest

import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext
import net.minecraft.gizmos.Gizmos
import java.nio.file.Path

fun ClientGameTestContext.screenshot(name: String): Path {
    val frame = computeOnClient<Gizmos.TemporaryCollection, Nothing> { it.levelExtractor.collectPerFrameMainThreadGizmos() }
    try {
        return takeScreenshot(name)
    } finally {
        runOnClient<Nothing> { frame.close() }
    }
}
