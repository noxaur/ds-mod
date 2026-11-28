package io.github.noxaur.dragen.api

import io.github.noxaur.dragen.api.boss.dragon.DragonFeed
import io.github.noxaur.dragen.api.boss.dragon.Part
import io.github.noxaur.dragen.api.boss.dragon.SeenBossBar
import io.github.noxaur.dragen.api.boss.dragon.SeenEntity
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.double
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.nio.file.Path
import kotlin.io.path.useLines

class DragonReplay(private val feed: DragonFeed, private val clock: ServerClock = ServerClock(), private val onTick: (tick: Long) -> Unit = {}) {
    private val moving = LinkedHashMap<Int, JsonObject>()
    private val names = HashMap<Int, String>()
    private var tick = -1L

    fun play(capture: Path) = capture.useLines { lines -> lines.filter(String::isNotBlank).forEach(::line) }.also { end() }

    private fun end() {
        if (tick < 0) return
        val watched = feed.watch.entities
        feed.entities(moving.mapNotNull { (id, it) -> entity(id, it).takeIf { seen -> seen.type in watched } })
        feed.tick(tick)
        onTick(tick)
    }

    private fun quiet(until: Long) {
        while (tick in 0 until until - 1) {
            tick++
            end()
        }
    }

    private fun entity(id: Int, json: JsonObject) = SeenEntity(
        id,
        json.getValue("entity").jsonPrimitive.content,
        Vec(json.d("x"), json.d("y"), json.d("z")),
        json.d("yaw"),
        json.d("pitch"),
        names[id].orEmpty(),
        json["parts"]?.jsonArray?.map {
            val part = it.jsonObject
            Part(part.getValue("name").jsonPrimitive.content, Box(Vec(part.d("x"), part.d("y") + part.d("h") / 2, part.d("z")), part.d("w"), part.d("h")))
        }.orEmpty(),
    )

    private fun JsonObject.d(key: String) = getValue(key).jsonPrimitive.double

    private fun line(line: String) {
        val json = Json.parseToJsonElement(line).jsonObject
        val at = json["tick"]?.jsonPrimitive?.int?.toLong() ?: maxOf(tick, 0)
        if (at != tick) {
            end()
            quiet(at)
            tick = at
        }
        val data = json.getValue("data").jsonObject
        when (json.getValue("type").jsonPrimitive.content) {
            "world" -> {
                moving.clear()
                feed.worldChanged()
            }
            "time" -> clock.time(data.getValue("gameTime").jsonPrimitive.content.toLong(), json.getValue("t").jsonPrimitive.content.toLong())
            "system" -> feed.chat(data.getValue("text").jsonObject.getValue("plain").jsonPrimitive.content)
            "sidebar" -> feed.sidebar(data["lines"]?.jsonArray?.map { it.jsonObject.getValue("plain").jsonPrimitive.content }.orEmpty())
            "tablist" -> feed.tabList(data.getValue("rows").jsonArray.map { it.jsonObject.getValue("plain").jsonPrimitive.content })
            "bossbars" -> feed.bossBars(data.getValue("bars").jsonArray.map { SeenBossBar(it.jsonObject.getValue("text").jsonPrimitive.content, it.jsonObject.d("progress")) })
            "block" -> feed.block(data.getValue("x").jsonPrimitive.int, data.getValue("y").jsonPrimitive.int, data.getValue("z").jsonPrimitive.int, data.getValue("state").jsonPrimitive.content)
            "seen", "renamed" -> names[data.getValue("id").jsonPrimitive.int] = data.getValue("name").jsonObject.getValue("plain").jsonPrimitive.content
            "motion" -> for ((id, entry) in data) if (entry is JsonNull) moving.remove(id.toInt()) else moving[id.toInt()] = entry.jsonObject
        }
    }
}
