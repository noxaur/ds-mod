package io.github.noxaur.dragen.core.game

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
