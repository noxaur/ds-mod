package io.github.noxaur.dsmod.core.config

import kotlinx.serialization.KSerializer

// @keep The file keeps the sections MoulConfig wrote, so a feature names the one its keys were always under.
enum class Store(val key: String) {
    GENERAL("general"),
    HUD("hud"),
    CHAT("chat"),
    INVENTORY("inventory"),
    BETTER_UI("betterUi"),
    LOOT("loot"),
    END("end"),
    SLAYER("slayer"),
    CONTRIBUTE("contribute"),
    ;

    val className = key.replaceFirstChar(Char::uppercase)
}

class Slot<T>(val store: Store, val key: String, val default: T, val serializer: KSerializer<T>, val formerly: List<String>) {
    var value: T = default

    val bind: Bind<T> = Bind("${store.className}.$key", { value }, { value = it }).formerly(*formerly.toTypedArray())

    fun reset() {
        value = default
    }
}

class Stored(val store: Store) {
    private val slots = LinkedHashMap<String, Slot<*>>()

    internal var pending: ((Slot<*>) -> Unit)? = null

    internal var held: ((String, KSerializer<*>) -> Any?)? = null

    val all: Collection<Slot<*>> get() = slots.values

    @Suppress("UNCHECKED_CAST")
    fun <T> slot(key: String, default: T, serializer: KSerializer<T>, formerly: List<String>): Slot<T> =
        slots.getOrPut(key) { Slot(store, key, default, serializer, formerly).also { pending?.invoke(it) } } as Slot<T>

    @Suppress("UNCHECKED_CAST")
    fun <T> former(key: String, serializer: KSerializer<T>): T? = held?.invoke(key, serializer) as T?

    fun reset() = slots.values.forEach { it.reset() }
}
