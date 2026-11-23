package io.github.noxaur.dsmod.core.config

import com.google.gson.GsonBuilder
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.Base64
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream

/** @keep A list the player copies out of the mod is its prefix and then the JSON gzipped as Base64, so a pasted share
 * says what it holds and stays short. Only `@Expose` fields travel, and decoding reads at most 1 MB,
 * so a bad paste cannot fill the memory. Gson writes null into a field the text gets wrong, so the caller cleans each one. */
object Share {
    private const val MAX_BYTES = 1 shl 20

    private val gson = GsonBuilder().excludeFieldsWithoutExposeAnnotation().create()

    fun encode(prefix: String, value: Any): String {
        val bytes = ByteArrayOutputStream()
        GZIPOutputStream(bytes).use { it.write(gson.toJson(value).toByteArray()) }
        return prefix + Base64.getEncoder().encodeToString(bytes.toByteArray())
    }

    fun <T> decode(prefix: String, text: String, type: Class<T>): T? {
        val body = text.trim().takeIf { it.startsWith(prefix) }?.removePrefix(prefix) ?: return null
        return runCatching {
            val json = GZIPInputStream(ByteArrayInputStream(Base64.getDecoder().decode(body))).use { it.readNBytes(MAX_BYTES) }
            gson.fromJson(String(json), type)
        }.getOrNull()
    }
}
