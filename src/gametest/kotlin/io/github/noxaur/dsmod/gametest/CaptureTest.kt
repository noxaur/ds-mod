package io.github.noxaur.dsmod.gametest

import io.github.noxaur.dsmod.debug.DsModDebug
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext
import java.nio.file.Path
import kotlin.io.path.readLines

/** Makes a real server send each kind of interface, and checks that the capture file holds it. */
class CaptureTest : FabricClientGameTest {
    override fun runTest(context: ClientGameTestContext) {
        context.worldBuilder().create().use { world ->
            val file = context.computeOnClient<Path, Nothing> { DsModDebug.toggleCapture(it) }
            listOf(
                """scoreboard objectives add ds dummy "Dragon Sim"""",
                "scoreboard objectives setdisplay sidebar ds",
                "scoreboard players set Coins ds 5",
                """bossbar add ds:dragon "Young Dragon"""",
                "bossbar set ds:dragon players @a",
                """title @a actionbar "Mana 100"""",
                """title @a title "Fight"""",
                """give @a diamond_sword[custom_name="Aspect of the Test"]""",
                """execute at @a run summon armor_stand ~ ~ ~ {CustomName:"Hologram",CustomNameVisible:1b,equipment:{head:{id:"minecraft:dragon_head"}}}""",
                "execute at @a run playsound minecraft:entity.ender_dragon.growl master @a",
                "execute at @a run particle minecraft:flame ~ ~1 ~ 0 0 0 0 5 force",
                "execute at @a run setblock ~2 ~ ~ minecraft:end_portal_frame[eye=true]",
                """tellraw @a "RARE DROP!"""",
            ).forEach(world.server::runCommand)
            context.waitTicks(40)
            context.runOnClient<Nothing> { DsModDebug.toggleCapture(it) }

            val events = file.readLines().map { Json.parseToJsonElement(it).jsonObject }
            val byType = events.groupBy({ it.getValue("type").jsonPrimitive.content }, { it.getValue("data").toString() })
            listOf(
                "system" to "RARE DROP!",
                "actionbar" to "Mana 100",
                "title" to "Fight",
                "sidebar" to "Coins",
                "bossbars" to "Young Dragon",
                "container" to "Aspect of the Test",
                "entities" to "Hologram",
                "entities" to "minecraft:dragon_head",
                "sound" to "minecraft:entity.ender_dragon.growl",
                "particles" to "minecraft:flame",
                "block" to "minecraft:end_portal_frame[eye=true",
                "tablist" to "\"name\"",
                "status" to "\"health\"",
            ).forEach { (type, expected) ->
                check(byType[type].orEmpty().any { expected in it }) {
                    "No '$type' event containing $expected. Got: ${byType[type]}"
                }
            }
        }
    }
}
