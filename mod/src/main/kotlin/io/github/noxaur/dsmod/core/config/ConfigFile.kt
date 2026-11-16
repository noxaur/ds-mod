package io.github.noxaur.dsmod.core.config

import kotlinx.serialization.json.Json
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption.ATOMIC_MOVE
import java.nio.file.StandardCopyOption.REPLACE_EXISTING
import kotlin.io.path.exists
import kotlin.io.path.moveTo
import kotlin.io.path.readText
import kotlin.io.path.writeText

/**
 * The settings on disk, as JSON keyed by the fields of [DsModConfig]. Versions up to 0.1.0 wrote the same file with
 * MoulConfig's Gson, and this reads it as it is: a key the file lacks keeps its default, a key no field has any
 * longer is passed over, and a value that is not one of its enum's any longer takes its default.
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

        fun encode(config: DsModConfig): String = json.encodeToString(DsModConfig.serializer(), config)

        fun decode(text: String): DsModConfig = json.decodeFromString(DsModConfig.serializer(), text)
    }
}
