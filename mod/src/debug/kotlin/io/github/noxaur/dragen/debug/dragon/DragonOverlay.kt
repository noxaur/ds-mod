package io.github.noxaur.dragen.debug.dragon

import io.github.noxaur.dragen.api.Vec
import io.github.noxaur.dragen.api.boss.dragon.DragonApi
import io.github.noxaur.dragen.core.config.DevTools
import io.github.noxaur.dragen.core.config.DragenConfig
import io.github.noxaur.dragen.core.config.Sections
import io.github.noxaur.dragen.core.config.SettingsTree
import io.github.noxaur.dragen.core.config.Store
import io.github.noxaur.dragen.core.feature.Feature
import io.github.noxaur.dragen.core.feature.Game
import io.github.noxaur.dragen.core.feature.HasFeature
import io.github.noxaur.dragen.core.game.Area
import io.github.noxaur.dragen.core.text.Role
import io.github.noxaur.dragen.debug.zone.ZoneTool
import io.github.noxaur.dragen.edge.DragonFeeds
import io.github.noxaur.dragen.edge.Ticks
import net.minecraft.client.Minecraft

class DragonOverlay(config: DragenConfig, api: () -> DragonApi) : HasFeature {
    private val probe = DragonProbe(api)
    private val view = DragonView(api, ::eye) { probe.recent }

    override val feature = Feature(
        config, "dragon-api", Area.ANYWHERE, Store.DEV, "Show what the dragon API knows, on screen and in the world.", default = false, name = "Dragon API",
        hud = { view.block() }, marks = { view.marks() },
    )

    private fun eye() = Minecraft.getInstance().player?.let { Vec(it.x, it.eyeY, it.z) }

    fun tick() {
        val on = feature.runs
        probe.update(on)
        if (on) {
            view.observe()
            feature.redraw()
        }
    }
}

object DevOverlays : DevTools {
    private var overlay: DragonOverlay? = null

    var zones: ZoneTool? = null
        private set

    override fun declare(sections: Sections) = sections.section("Dev", Role.WARNING) {
        group(DragonOverlay(c) { DragonFeeds.system.api })
        group(ZoneTool(c))
    }

    override fun started(tree: SettingsTree, game: Game) {
        overlay = tree.owners.filterIsInstance<DragonOverlay>().single()
        zones = tree.owners.filterIsInstance<ZoneTool>().single()
    }

    fun register() = Ticks.start { client ->
        overlay?.tick()
        zones?.tick(client)
    }
}
