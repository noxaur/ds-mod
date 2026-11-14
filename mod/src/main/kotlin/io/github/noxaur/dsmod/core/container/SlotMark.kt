package io.github.noxaur.dsmod.core.container

import io.github.noxaur.dsmod.core.text.Role
import io.github.noxaur.dsmod.core.text.Text

/** A highlight behind one slot of the open container, with an optional label in its corner. */
class SlotMark(val role: Role, val label: Text? = null)
