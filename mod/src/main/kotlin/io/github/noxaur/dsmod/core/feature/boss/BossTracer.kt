package io.github.noxaur.dsmod.core.feature.boss

import io.github.noxaur.dsmod.core.config.DsModConfig
import io.github.noxaur.dsmod.core.game.Boss
import io.github.noxaur.dsmod.core.text.Role
import io.github.noxaur.dsmod.core.world.Tracer

/** A line from the middle of the screen to each boss the player picks, to follow while looking away from it. */
class BossTracer(config: DsModConfig) :
    BossMarks(config, "boss-tracer", "Draw a line from your crosshair to each boss you pick.", "tracer", "Trace") {
    override fun mark(boss: Boss) = Tracer(boss.id, Role.BAD)
}
