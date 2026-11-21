package io.github.noxaur.dsmod.core.config

import kotlinx.serialization.KSerializer

/**
 * A section of the config file that a feature's settings are saved in. The file keeps the sections MoulConfig wrote,
 * so a feature names the one its keys were always under, and a new feature takes its area's.
 */
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

    /** The section as a [Bind.field] names it, such as `BetterUi`, which is how `DsModConfig`'s own classes are named. */
    val className = key.replaceFirstChar(Char::uppercase)
}

/**
 * One saved value of a feature's, under [key] in its [Store]. Its [default] is the value a fresh config holds, and
 * [formerly] the keys older versions wrote it under, read when the file lacks [key].
 */
class Slot<T>(val store: Store, val key: String, val default: T, val serializer: KSerializer<T>, val formerly: List<String>) {
    var value: T = default

    /** This value as a row reaches it. */
    val bind: Bind<T> = Bind("${store.className}.$key", { value }, { value = it }).formerly(*formerly.toTypedArray())

    fun reset() {
        value = default
    }
}

/**
 * The slots of one [Store], made as the features that own them are built. A feature built again on the same config
 * finds its slot by key and keeps the value, which is how the game tests restart the mod. A slot read from the file
 * takes its value from [pending], the section as the file held it, when it is made.
 */
class Stored(val store: Store) {
    private val slots = LinkedHashMap<String, Slot<*>>()

    /** Set by `ConfigFile` while the file is read, and read by each slot made after. */
    internal var pending: ((Slot<*>) -> Unit)? = null

    /** Every slot made so far, in the order they were made. */
    val all: Collection<Slot<*>> get() = slots.values

    /** The slot under [key], made with [default] and [serializer] the first time, and found by key after. */
    @Suppress("UNCHECKED_CAST")
    fun <T> slot(key: String, default: T, serializer: KSerializer<T>, formerly: List<String>): Slot<T> =
        slots.getOrPut(key) { Slot(store, key, default, serializer, formerly).also { pending?.invoke(it) } } as Slot<T>

    fun reset() = slots.values.forEach { it.reset() }
}
