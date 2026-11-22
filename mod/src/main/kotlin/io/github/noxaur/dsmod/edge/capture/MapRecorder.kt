package io.github.noxaur.dsmod.edge.capture

import io.github.noxaur.dsmod.core.capture.CaptureWriter
import io.github.noxaur.dsmod.core.capture.RawRecord
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import net.minecraft.client.multiplayer.ClientLevel
import net.minecraft.world.level.saveddata.maps.MapId
import java.io.ByteArrayOutputStream
import java.util.Base64
import java.util.zip.DeflaterOutputStream

class MapRecorder(private val writer: CaptureWriter) {
    private class Seen(val colors: ByteArray, val decorations: JsonArray)

    private val seen = HashMap<Int, Seen>()

    fun offer(level: ClientLevel, ids: Collection<MapId>) {
        for (id in ids) {
            val map = level.getMapData(id) ?: continue
            val decorations = JsonArray(
                map.decorations.map { marker ->
                    buildJsonObject {
                        put("type", marker.type().registeredName)
                        put("x", marker.x().toInt())
                        put("y", marker.y().toInt())
                        put("rot", marker.rot().toInt())
                        marker.name().ifPresent { put("name", it.string) }
                    }
                },
            )
            val last = seen[id.id()]
            val pixelsChanged = last == null || !last.colors.contentEquals(map.colors)
            if (!pixelsChanged && last?.decorations == decorations) continue
            // @keep The map's array is rewritten in place, so keep a copy for the writer thread and the next comparison.
            val colors = if (last == null || pixelsChanged) map.colors.clone() else last.colors
            seen[id.id()] = Seen(colors, decorations)
            val scale = map.scale.toInt()
            val locked = map.locked
            writer.record(RawRecord.MAP) {
                buildJsonObject {
                    put("id", id.id())
                    put("scale", scale)
                    put("locked", locked)
                    if (pixelsChanged) put("colors", deflate(colors))
                    put("decorations", decorations)
                }
            }
        }
    }

    private fun deflate(bytes: ByteArray): String {
        val out = ByteArrayOutputStream()
        DeflaterOutputStream(out).use { it.write(bytes) }
        return Base64.getEncoder().encodeToString(out.toByteArray())
    }
}
