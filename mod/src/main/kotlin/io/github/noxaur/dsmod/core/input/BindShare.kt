package io.github.noxaur.dsmod.core.input

import com.google.gson.GsonBuilder
import com.google.gson.annotations.Expose
import io.github.noxaur.dsmod.core.game.Area
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.Base64
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream

/**
 * A group of binds as text a player can paste to another, as SkyOcean shares its hotkeys: JSON, gzipped and in
 * Base64, after a prefix that tells it apart from anything else on the clipboard.
 */
object BindShare {
    private const val PREFIX = "dsmod-binds:"
    private const val MAX_BINDS = 100
    private const val MAX_BYTES = 1 shl 16

    /** The longest group name. */
    const val GROUP_LENGTH = 32

    data class Shared(@Expose val group: String = "", @Expose val binds: List<CommandBind> = emptyList())

    private val gson = GsonBuilder().excludeFieldsWithoutExposeAnnotation().create()

    fun encode(group: String, binds: List<CommandBind>): String {
        val bytes = ByteArrayOutputStream()
        GZIPOutputStream(bytes).use { it.write(gson.toJson(Shared(group, binds.map { bind -> bind.copy(group = "") })).toByteArray()) }
        return PREFIX + Base64.getEncoder().encodeToString(bytes.toByteArray())
    }

    /**
     * The group in [text], or null when it holds none. Every bind comes in switched off, so the player reads each
     * command before a key can send it, and anything the text left out or got wrong takes its default.
     */
    @Suppress("USELESS_CAST", "SENSELESS_COMPARISON") // Gson writes null into a field the text gets wrong.
    fun decode(text: String): Shared? {
        val body = text.trim().takeIf { it.startsWith(PREFIX) }?.removePrefix(PREFIX) ?: return null
        val shared = runCatching {
            val json = GZIPInputStream(ByteArrayInputStream(Base64.getDecoder().decode(body))).use { it.readNBytes(MAX_BYTES) }
            gson.fromJson(String(json), Shared::class.java)
        }.getOrNull() ?: return null
        val binds = (shared.binds as List<CommandBind?>?).orEmpty().filterNotNull().take(MAX_BINDS).map {
            CommandBind(
                name = (it.name as String?).orEmpty().take(CommandBind.MAX_LENGTH),
                command = (it.command as String?).orEmpty().take(CommandBind.MAX_LENGTH),
                keys = (it.keys as List<Int?>?).orEmpty().filterNotNull().take(CommandBind.MAX_KEYS),
                enabled = false,
                where = (it.where as Where?) ?: Where.WORLD,
                areas = (it.areas as List<*>?).orEmpty().filterIsInstance<Area>(),
                priority = it.priority,
                extraKeys = it.extraKeys,
                inOrder = it.inOrder,
            )
        }
        return Shared((shared.group as String?).orEmpty().trim().take(GROUP_LENGTH), binds)
    }
}
