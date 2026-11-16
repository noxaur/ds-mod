package io.github.noxaur.dsmod.core.game

/** Lets a poll post only when what it read changed, or when the capture tap did, so a capture that starts later still begins with it. */
class ChangeGate<T>(private var last: T, private var tap: Any?) {
    /** Whether [value] or [tap] differs from the last pass, remembering both when it does. */
    fun passes(value: T, tap: Any?): Boolean {
        if (value == last && tap === this.tap) return false
        last = value
        this.tap = tap
        return true
    }

    /** Sets the last value to [value], as if a poll had passed it, so the next [passes] succeeds on anything else. */
    fun forget(value: T) {
        last = value
    }
}
