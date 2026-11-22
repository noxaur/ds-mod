package io.github.noxaur.dsmod.core.config

import kotlinx.serialization.KSerializer
import kotlinx.serialization.serializer

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
}

class Stored(val store: Store) {
    private val slots = LinkedHashMap<String, Slot<*>>()

    private var pending: ((Slot<*>) -> Unit)? = null

    internal var held: ((String, KSerializer<*>) -> Any?)? = null

    val all: Collection<Slot<*>> get() = slots.values

    inline fun <reified T> slot(key: String, default: T, vararg formerly: String) = slot(key, default, serializer<T>(), formerly.toList())

    @Suppress("UNCHECKED_CAST")
    fun <T> slot(key: String, default: T, serializer: KSerializer<T>, formerly: List<String>): Slot<T> =
        slots.getOrPut(key) { Slot(store, key, default, serializer, formerly).also { pending?.invoke(it) } } as Slot<T>

    @Suppress("UNCHECKED_CAST")
    fun <T> former(key: String, serializer: KSerializer<T>): T? = held?.invoke(key, serializer) as T?

    internal fun load(read: (Slot<*>) -> Unit) {
        pending = read
        slots.values.forEach(read)
    }
}
