package io.github.noxaur.dragen.core.config

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

// @keep Versions up to 0.1.0 wrote this file with MoulConfig's Gson, and it is read as it is.
class ConfigFile(private val file: Path) {
    var unreadable: Path? = null
        private set

    fun load(): DragenConfig {
        if (!file.exists()) return DragenConfig()
        return try {
            decode(file.readText())
        } catch (e: Exception) {
            val kept = file.resolveSibling("${file.fileName}.corrupt-${System.currentTimeMillis()}")
            file.moveTo(kept)
            unreadable = kept
            DragenConfig()
        }
    }

    fun save(config: DragenConfig) {
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

        fun encode(config: DragenConfig): String {
            val fields = json.encodeToJsonElement(DragenConfig.serializer(), config).jsonObject
            val sections = LinkedHashMap<String, JsonElement>(fields)
            for (store in Store.entries) {
                val slots = config.stored(store).all
                if (slots.isEmpty()) continue
                sections[store.key] = JsonObject(slots.associate { it.key to write(it) })
            }
            return json.encodeToString(JsonObject.serializer(), JsonObject(sections))
        }

        fun decode(text: String): DragenConfig {
            val tree = json.parseToJsonElement(text).jsonObject
            val config = json.decodeFromJsonElement(DragenConfig.serializer(), tree)
            for (store in Store.entries) {
                val section = tree[store.key] as? JsonObject ?: continue
                config.stored(store).load { slot -> read(slot, section) }
                val former = HashMap(section)
                config.stored(store).held = { key, serializer -> former.remove(key)?.let { decode(serializer, it) } }
            }
            return config
        }

        private fun <T> write(slot: Slot<T>) = json.encodeToJsonElement(slot.serializer, slot.value)

        private fun <T> read(slot: Slot<T>, section: JsonObject) {
            val element = (listOf(slot.key) + slot.formerly).firstNotNullOfOrNull(section::get) ?: return
            slot.value = try {
                json.decodeFromJsonElement(slot.serializer, element)
            } catch (e: IllegalArgumentException) {
                slot.default
            }
        }

        private fun <T> decode(serializer: DeserializationStrategy<T>, element: JsonElement): T? = try {
            json.decodeFromJsonElement(serializer, element)
        } catch (e: IllegalArgumentException) {
            null
        }
    }
}
