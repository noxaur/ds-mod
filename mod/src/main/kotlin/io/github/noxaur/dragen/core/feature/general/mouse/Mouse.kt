package io.github.noxaur.dragen.core.feature.general.mouse

import io.github.noxaur.dragen.core.config.Groups
import io.github.noxaur.dragen.core.feature.general.mouse.ctrlclick.CtrlClick

internal fun Groups.mouse() {
    // @keep Built on every system, so a save off a Mac keeps the value a Mac set
    val ctrlClick = CtrlClick(c)
    if (mac) group("Mouse") {
        feature(ctrlClick)
    }
}
