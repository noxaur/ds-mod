package io.github.noxaur.dsmod.core.feature

import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty

/** A value a feature holds for one world or one session, which `Game` builds again from [initial] when that ends. */
class Scoped<S> internal constructor(private val initial: () -> S) : ReadWriteProperty<Any?, S> {
    private var value = initial()

    internal fun reset() {
        value = initial()
    }

    override fun getValue(thisRef: Any?, property: KProperty<*>) = value

    override fun setValue(thisRef: Any?, property: KProperty<*>, value: S) {
        this.value = value
    }
}
