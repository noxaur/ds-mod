package io.github.noxaur.dsmod.core.feature.end.dragon

import io.github.noxaur.dsmod.core.config.Groups
import io.github.noxaur.dsmod.core.feature.end.dragon.dragontimer.DragonTimer
import io.github.noxaur.dsmod.core.feature.end.dragon.eyeguard.EyeGuard
import io.github.noxaur.dsmod.core.feature.end.dragon.fireballalert.FireballAlert

internal fun Groups.dragon() = group("Dragon") {
    feature(DragonTimer(c))
    feature(FireballAlert(c))
    feature(EyeGuard(c))
}
