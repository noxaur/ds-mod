package io.github.noxaur.dsmod.rules

object CallersFixture {
    const val NAMED = 1
    const val UNNAMED = 2
    const val GAP = 5
    val read = 3
    val unread = 4

    fun called() = Unit

    fun uncalled() = unreachable()

    fun unreachable() = Unit

    fun hook() = Unit

    fun ping(): Int = pong()

    fun pong(): Int = ping()

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

object CallersOther {
    const val GAP = 6
}

class CallersBuilt(val x: Int) {
    constructor(text: String) : this(text.length)
}

object CallersCaller {
    var seen = 0

    fun run() {
        CallersFixture.called()
        CallersFixture.inlined { seen = CallersFixture.NAMED + CallersFixture.read + CallersOther.GAP + CallersBuilt(1).x }
    }
}

object CallersGameTest {
    fun run() = CallersFixture.hook()
}
