package io.github.noxaur.dsmod.core.config

import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption.ATOMIC_MOVE
import java.nio.file.StandardCopyOption.REPLACE_EXISTING
import kotlin.io.path.exists
import kotlin.io.path.moveTo
import kotlin.io.path.readText
import kotlin.io.path.writeText

/**
 * The settings on disk, as JSON keyed by the fields of [DsModConfig] and, in the same sections, by the keys of the
 * features' [Slot]s. Versions up to 0.1.0 wrote the same file with MoulConfig's Gson, and this reads it as it is: a
 * key the file lacks keeps its default, a key no field or slot has any longer is passed over, and a value that is not
 * one of its enum's any longer takes its default. A slot is read when its feature is built, so a config that is
 * loaded and then given to `SettingsTree` holds what the file said.
 */
class ConfigFile(private val file: Path) {
    /**
     * Where [load] moved a file it could not read, or null. The settings start from their defaults then, and the
     * edge tells the player once where their old file is, so a save cannot wipe it unseen.
     */
    var unreadable: Path? = null
        private set

    fun load(): DsModConfig {
        if (!file.exists()) return DsModConfig()
        return try {
            decode(file.readText())
        } catch (e: Exception) {
            val kept = file.resolveSibling("${file.fileName}.corrupt-${System.currentTimeMillis()}")
            file.moveTo(kept)
            unreadable = kept
            DsModConfig()
        }
    }

    /** Writes a temporary file and renames it over the old one, so a crash cannot leave half a file. */
    fun save(config: DsModConfig) {
        Files.createDirectories(file.parent)
        val temporary = file.resolveSibling("${file.fileName}.tmp")
        temporary.writeText(encode(config))
        temporary.moveTo(file, ATOMIC_MOVE, REPLACE_EXISTING)
    }

    companion object {
        private val json = Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
            coerceInputValues = true
        }

        /** The fields, then each store's slots in their section, a section the fields lack included. */
        fun encode(config: DsModConfig): String {
            val fields = json.encodeToJsonElement(DsModConfig.serializer(), config).jsonObject
            val sections = LinkedHashMap<String, JsonElement>(fields)
            for (store in Store.entries) {
                val slots = config.stored(store).all
                if (slots.isEmpty()) continue
                val section = LinkedHashMap<String, JsonElement>((fields[store.key] as? JsonObject).orEmpty())
                for (slot in slots) section[slot.key] = write(slot)
                sections[store.key] = JsonObject(section)
            }
            return json.encodeToString(JsonObject.serializer(), JsonObject(sections))
        }

        /** The fields now, and each slot as its feature is built. */
        fun decode(text: String): DsModConfig {
            val tree = json.parseToJsonElement(text).jsonObject
            val config = json.decodeFromJsonElement(DsModConfig.serializer(), tree)
            for (store in Store.entries) {
                val section = tree[store.key] as? JsonObject ?: continue
                config.stored(store).pending = { slot -> read(slot, section) }
                val former = HashMap(section)
                config.stored(store).held = { key, serializer -> former.remove(key)?.let { decode(serializer, it) } }
            }
            return config
        }

        private fun <T> write(slot: Slot<T>) = json.encodeToJsonElement(slot.serializer, slot.value)

        /** Sets [slot] from [section] under its key, or a key it was formerly saved under. A value its type no longer has keeps the default. */
        private fun <T> read(slot: Slot<T>, section: JsonObject) {
            val element = (listOf(slot.key) + slot.formerly).firstNotNullOfOrNull(section::get) ?: return
            slot.value = decode(slot.serializer, element) ?: slot.default
        }

        private fun <T> decode(serializer: DeserializationStrategy<T>, element: JsonElement): T? = try {
            json.decodeFromJsonElement(serializer, element)
        } catch (e: IllegalArgumentException) {
            null
        }
    }
}
