package io.github.noxaur.dsmod.rules

object CallersFixture {
    const val NAMED = 1
    const val UNNAMED = 2
    val read = 3
    val unread = 4

    fun called() = Unit

    fun uncalled() = unreachable()

    fun unreachable() = Unit

    fun hook() = Unit

    inline fun inlined(block: () -> Unit) = block()

    inline fun uninlined(block: () -> Unit) = block()

    override fun toString() = "fixture"
}

class CallersShared {
    fun read() = SHARED

    companion object {
        val SHARED = listOf(1)
        val UNSHARED = listOf(2)
    }
}

object CallersCaller {
    var seen = 0

    fun run() {
        CallersFixture.called()
        CallersFixture.inlined { seen = CallersFixture.NAMED + CallersFixture.read }
    }
}

object CallersGameTest {
    fun run() = CallersFixture.hook()
}
