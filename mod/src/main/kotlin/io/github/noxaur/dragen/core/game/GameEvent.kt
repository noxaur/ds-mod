package io.github.noxaur.dragen.core.game

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
sealed interface GameEvent

sealed interface FeatureEvent : GameEvent

@Serializable
@SerialName("system")
data class SystemMessage(val text: ServerText) : GameEvent

@Serializable
@SerialName("world")
data object WorldChanged : FeatureEvent

@Serializable
@SerialName("left")
data object LeftServer : FeatureEvent

@Serializable
@SerialName("tablist")
data class TabList(val rows: List<ServerText>) : FeatureEvent

// @keep dragsim.co blanks the number beside each line, so there is none here
@Serializable
@SerialName("sidebar")
data class Sidebar(val title: ServerText = ServerText(), val lines: List<ServerText> = emptyList()) : FeatureEvent

/** @keep A big menu's slots arrive over a few ticks. */
@Serializable
@SerialName("container")
data class ContainerOpened(val id: Int, val title: ServerText? = null, val size: Int, val kept: Boolean = false) : FeatureEvent

// @keep The server sends one every half second, and one more the moment something changes it
@Serializable
@SerialName("actionbar")
data class ActionBar(val text: ServerText) : FeatureEvent

@Serializable
@SerialName("death")
data class EntityDied(val entity: String, val name: ServerText, val x: Double, val y: Double, val z: Double) : FeatureEvent

@Serializable
@SerialName("head")
data class HeadPlaced(val x: Int, val y: Int, val z: Int) : FeatureEvent

@Serializable
@SerialName("slot")
data class SlotChanged(val slot: Int, val item: Item? = null) : FeatureEvent

@Serializable
@SerialName("click")
data class SlotClicked(val slot: Int) : FeatureEvent

@Serializable
@SerialName("seen")
data class EntitySeen(val id: Int, val entity: String, val name: ServerText, val head: Item? = null) : FeatureEvent

@Serializable
@SerialName("renamed")
data class EntityRenamed(val id: Int, val name: ServerText) : FeatureEvent

@Serializable
@SerialName("gone")
data class EntityGone(val id: Int) : FeatureEvent

// @keep The server says nothing of a hit, so this event comes from the player rather than the server
@Serializable
@SerialName("hit")
data class Hit(val entity: String) : FeatureEvent

@Serializable
@SerialName("floating")
data class FloatingText(val text: ServerText) : FeatureEvent

@Serializable
@SerialName("time")
data class ServerTime(val gameTime: Long) : FeatureEvent

@Serializable
@SerialName("ping")
data class PingMeasured(val millis: Long) : FeatureEvent

@Serializable
@SerialName("fps")
data class FrameRate(val fps: Int) : FeatureEvent

/** @keep dragsim.co shows a mined block as bedrock until it grows back, and the client never breaks one itself. An
 * Ender Node is `minecraft:sandstone`, with portal particles around it. */
@Serializable
@SerialName("broken")
data class BlockBroken(val block: String, val x: Int, val y: Int, val z: Int, val tick: Long, val by: Breaker) : FeatureEvent

enum class Breaker { PLAYER, WITH_PLAYER, OTHER }
