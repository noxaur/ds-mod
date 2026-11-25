package io.github.noxaur.dragen.gametest

object ActionSpy {
    var attacks = 0
    var uses = 0

    fun clear() {
        attacks = 0
        uses = 0
    }
}
