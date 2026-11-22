package io.github.noxaur.dsmod.core.game

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
sealed interface GameEvent

@Serializable
@SerialName("system")
data class SystemMessage(val text: ServerText) : GameEvent

/** @keep Game also runs this handler when the player switches the feature off, because a feature that is off sees no
 * events */
@Serializable
@SerialName("world")
data object WorldChanged : GameEvent

@Serializable
@SerialName("left")
data object LeftServer : GameEvent

@Serializable
@SerialName("tablist")
data class TabList(val rows: List<ServerText>) : GameEvent

// @keep dragsim.co blanks the number beside each line, so there is none here
@Serializable
@SerialName("sidebar")
data class Sidebar(val title: ServerText = ServerText(), val lines: List<ServerText> = emptyList()) : GameEvent

/** @keep A big menu's slots arrive over a few ticks. With kept, back in the player's own inventory, the slots hold
 * what they held when the last menu opened, and only the ones that changed since follow */
@Serializable
@SerialName("container")
data class ContainerOpened(val id: Int, val title: ServerText? = null, val size: Int, val kept: Boolean = false) : GameEvent

// @keep The server sends one every half second, and one more the moment something changes it
@Serializable
@SerialName("actionbar")
data class ActionBar(val text: ServerText) : GameEvent

@Serializable
@SerialName("death")
data class EntityDied(val entity: String, val name: ServerText, val x: Double, val y: Double, val z: Double) : GameEvent

@Serializable
@SerialName("head")
data class HeadPlaced(val x: Int, val y: Int, val z: Int) : GameEvent

@Serializable
@SerialName("slot")
data class SlotChanged(val slot: Int, val item: Item? = null) : GameEvent

/** @keep Posted again with the same id when the head changes or the client's reason to report it does, which a reader
 * takes as the entity now being this */
@Serializable
@SerialName("seen")
data class EntitySeen(val id: Int, val entity: String, val name: ServerText, val head: Item? = null) : GameEvent

@Serializable
@SerialName("renamed")
data class EntityRenamed(val id: Int, val name: ServerText) : GameEvent

@Serializable
@SerialName("gone")
data class EntityGone(val id: Int) : GameEvent

// @keep The server says nothing of a hit, so this event comes from the player rather than the server
@Serializable
@SerialName("hit")
data class Hit(val entity: String) : GameEvent

/** @keep Damage numbers, NPC names and holograms are all these. A handler may `hide()` it or `replace()` its text, on
 * this client only, until the server changes the text again */
@Serializable
@SerialName("floating")
data class FloatingText(val text: ServerText) : GameEvent

@Serializable
@SerialName("time")
data class ServerTime(val gameTime: Long) : GameEvent

@Serializable
@SerialName("ping")
data class PingMeasured(val millis: Long) : GameEvent

@Serializable
@SerialName("fps")
data class FrameRate(val fps: Int) : GameEvent
