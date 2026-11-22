package io.github.noxaur.dsmod.core.game

/** @keep A poll posts only when what it read changed, or when the capture tap did, so a capture that starts later
 * still begins with it */
class ChangeGate<T>(private var last: T, private var tap: Any?) {
    fun passes(value: T, tap: Any?): Boolean {
        if (value == last && tap === this.tap) return false
        last = value
        this.tap = tap
        return true
    }

    fun forget(value: T) {
        last = value
    }
}
