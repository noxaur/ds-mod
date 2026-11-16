package io.github.noxaur.dsmod.gametest

import io.github.noxaur.dsmod.core.capture.RawRecord
import io.github.noxaur.dsmod.core.capture.eventTypes
import io.github.noxaur.dsmod.debug.DsModDebug
import io.github.noxaur.dsmod.edge.DsModClient
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext
import net.minecraft.core.component.DataComponents
import net.minecraft.network.chat.Component
import net.minecraft.network.protocol.game.ClientboundTabListPacket
import net.minecraft.world.item.MapItem
import org.lwjgl.glfw.GLFW
import java.nio.file.Path
import kotlin.io.path.readLines

/**
 * Makes a real server send each kind of interface, and checks that the capture file holds it. Covers
 * [io.github.noxaur.dsmod.edge.SidebarPoller].
 */
class CaptureTest : FabricClientGameTest {
    /** What a line of each type must hold, by type. */
    private val expected = listOf(
        "system" to "RARE DROP!",
        "world" to "{}",
        "left" to "{}",
        "chat" to "Hello from the server",
        "hud" to "Eyes: 0/8",
        "metrics" to "dragon-timer",
        "actionbar" to "Mana 100",
        "title" to "Fight",
        "sidebar" to "Coins",
        // The server blanks the line's name and puts its text in the team, as dragsim.co does.
        "sidebar" to "\"plain\":\"Purse: 531\"",
        "bossbars" to "Young Dragon",
        "container" to "\"size\":46",
        "slot" to "Aspect of the Test",
        // What the `slot` event leaves out.
        "itemcomponents" to "dragsim:epic",
        "entities" to "Hologram",
        "entities" to "minecraft:dragon_head",
        "sound" to "minecraft:entity.ender_dragon.growl",
        "particles" to "minecraft:flame",
        "block" to "minecraft:end_portal_frame[eye=true",
        "tablist" to "\"rows\"",
        "tabheader" to "dragsim.co",
        "status" to "\"health\"",
        "swing" to "ASPECT_OF_THE_TEST",
        "use" to "Aspect of the Test",
        "death" to "Victim",
        "head" to "\"y\":",
        "pet" to "Test Pet",
        "hit" to "minecraft:husk",
        "unload" to "Leaver",
        "unload" to "Victim",
        "skulls" to "DsHead",
        "skulls" to "rotation=4",
        "skulls" to "textures",
        "blockstates" to "minecraft:obsidian",
        "blockstates" to "minecraft:stone_button",
        "entities" to "Quiz Label",
        "floating" to "Quiz Label",
        "entities" to "minecraft:silverfish",
        "entities" to "\"glowing\":true",
        "entities" to "minecraft:filled_map",
        "map" to "\"colors\"",
        "time" to "gameTime",
        // A ping is asked for only while Performance Stats shows it, which the test turns on.
        "ping" to "millis",
        "fps" to "\"fps\"",
    )

    override fun runTest(context: ClientGameTestContext) {
        // `BriefTest` checks `step`, which only a brief records.
        val unchecked = eventTypes + RawRecord.entries.map { it.type } - expected.map { it.first }.toSet() - RawRecord.STEP.type
        check(unchecked.isEmpty()) { "No check for the capture types $unchecked. Make the server send each one here, and add what its line must hold to `expected`." }
        lateinit var file: Path
        context.worldBuilder().create().use { world ->
            // The server owns map data, so make a real one for the frame to show.
            var mapId = 0
            world.server.runOnServer<Nothing> { mapId = MapItem.create(it.overworld(), 0, 0, 0.toByte(), false, false).get(DataComponents.MAP_ID)!!.id() }
            // These exist before the capture starts, so only a scan can find them.
            listOf(
                """execute at @a run setblock ~3 ~ ~1 minecraft:player_head[rotation=4]{profile:{id:[I;1,2,3,4],name:"DsHead",properties:[{name:"textures",value:"eyJ0ZXh0dXJlcyI6e319"}]}}""",
                "execute at @a run setblock ~3 ~ ~2 minecraft:obsidian",
                "execute at @a run setblock ~3 ~ ~3 minecraft:stone_button[face=floor]",
            ).forEach(world.server::runCommand)
            context.waitTicks(40)
            context.runOnClient<Nothing> { DsModClient.settings.general.performanceStats = true }
            file = context.computeOnClient<Path, Nothing> { DsModDebug.toggleCapture(it)!! }
            context.enterTheEnd()
            listOf(
                """scoreboard objectives add ds dummy "Dragon Sim"""",
                "scoreboard objectives setdisplay sidebar ds",
                "scoreboard players set Coins ds 5",
                "team add ds_line",
                """team modify ds_line prefix {"text":"Purse: "}""",
                """team modify ds_line suffix {"text":"531"}""",
                "team join ds_line Purse",
                "scoreboard players set Purse ds 4",
                "scoreboard players display name Purse ds \"\"",
                """bossbar add ds:dragon "Young Dragon"""",
                "bossbar set ds:dragon players @a",
                """title @a actionbar "Mana 100"""",
                """title @a title "Fight"""",
                """give @a diamond_sword[custom_name="Aspect of the Test",tooltip_style="dragsim:epic",custom_data={item_data:{item_id:"ASPECT_OF_THE_TEST"}}]""",
                """execute at @a run summon armor_stand ~ ~ ~ {CustomName:"Hologram",CustomNameVisible:1b,equipment:{head:{id:"minecraft:dragon_head"}}}""",
                "execute at @a run playsound minecraft:entity.ender_dragon.growl master @a",
                "execute at @a run particle minecraft:flame ~ ~1 ~ 0 0 0 0 5 force",
                "execute at @a run setblock ~2 ~ ~ minecraft:end_portal_frame[eye=true]",
                "execute at @a run setblock ~-3 ~ ~1 minecraft:player_head",
                """execute at @a run summon text_display ~ ~2 ~ {text:"Quiz Label"}""",
                "execute at @a run summon silverfish ~-2 ~ ~ {NoAI:1b,Glowing:1b}",
                """execute at @a run summon item_frame ~ ~ ~4 {Facing:3b,Item:{id:"minecraft:filled_map",count:1,components:{"minecraft:map_id":$mapId}}}""",
                """execute at @a run summon armor_stand ~1 ~ ~ {CustomName:"[Lvl 100] Test Pet",Invisible:1b,NoGravity:1b,equipment:{head:{id:"minecraft:player_head"}}}""",
                """execute at @a run summon pig ~ ~ ~-3 {CustomName:"Victim",NoAI:1b,Silent:1b}""",
                """execute at @a run summon pig ~ ~ ~-4 {CustomName:"Leaver",NoAI:1b,Silent:1b}""",
                """tellraw @a "RARE DROP!"""",
                "say Hello from the server",
                """tellraw @a "§5✦ §dThe Egg has Spawned!"""",
            ).forEach(world.server::runCommand)
            context.waitTicks(20)
            // One mob dies and one is carried far away, which the server reports as a removal without a death.
            listOf(
                "kill @e[name=Victim]",
                "execute as @e[name=Leaver] run tp @s ~ ~ ~5000",
            ).forEach(world.server::runCommand)
            // A mob just ahead of the crosshair, for the left press to hit.
            world.server.runCommand("execute as @a at @s anchored eyes positioned ^ ^ ^2 run summon husk ~ ~-1 ~ {NoAI:1b,Silent:1b}")
            context.waitTicks(5)
            context.input.pressMouse(GLFW.GLFW_MOUSE_BUTTON_LEFT)
            context.input.pressMouse(GLFW.GLFW_MOUSE_BUTTON_RIGHT)
            world.server.runOnServer<Nothing> { it.playerList.broadcastAll(ClientboundTabListPacket(Component.literal("Dragon Sim"), Component.literal("dragsim.co"))) }
            context.waitTicks(40)
            // Last, because a new world drops the entities and blocks the checks above read.
            world.server.runCommand("execute in minecraft:the_nether run tp @a 0 100 0")
            context.waitTicks(20)
        }
        // Leaving the world leaves its server, so the capture runs on past it to record that. Fabric says so on the
        // network thread, and the features hear it at the end of the next client tick.
        context.waitTick()
        // The file is finished off the client thread, and the toggle says so when it is whole.
        var said: String? = null
        context.runOnClient<Nothing> { client -> DsModDebug.toggleCapture(client) { said = it } }
        context.waitFor { said != null }
        context.runOnClient<Nothing> { DsModClient.settings.general.performanceStats = false }
        check(said == "Capture saved: $file") { "The capture ended with '$said'" }

        val events = file.readLines().map { Json.parseToJsonElement(it).jsonObject }
        val byType = events.groupBy({ it.getValue("type").jsonPrimitive.content }, { it.getValue("data").toString() })
        expected.forEach { (type, part) ->
            check(byType[type].orEmpty().any { part in it }) {
                "No '$type' event containing $part. Got: ${byType[type]}"
            }
        }
        check(byType["death"].orEmpty().none { "Leaver" in it }) { "The mob that left was recorded as dead: ${byType["death"]}" }
        check(byType["unload"].orEmpty().none { "Leaver" in it && "KILLED" in it }) { "The mob that left was recorded as killed: ${byType["unload"]}" }
    }
}
