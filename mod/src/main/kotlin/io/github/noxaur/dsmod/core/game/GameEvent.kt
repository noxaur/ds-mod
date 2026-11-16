package io.github.noxaur.dsmod.core.game

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Something the server showed the client. The edge posts these to [Game], a capture records them
 * as they are, and a test replays the recording, so a feature sees the same input in all three.
 */
@Serializable
sealed interface GameEvent

/** A line the server put in chat. On dragsim.co player chat arrives this way too. */
@Serializable
@SerialName("system")
data class SystemMessage(val text: ServerText) : GameEvent

/**
 * The client entered a world: on joining, and each time the server moves it to another one. A feature clears what
 * belongs to one world here. `Game` also runs this handler when the player switches the feature off, because a
 * feature that is off sees no events.
 */
@Serializable
@SerialName("world")
data object WorldChanged : GameEvent

/**
 * The tab list as the client draws it: down the first column, then down the next. Posted only when a row
 * changes, and only while a feature reads it.
 */
@Serializable
@SerialName("tablist")
data class TabList(val rows: List<ServerText>) : GameEvent

/**
 * The sidebar as the client draws it: its [title], then its [lines] from the top. dragsim.co blanks the number
 * beside each line, so there is none here. With no sidebar shown, the title and the lines are empty. Posted only
 * when a line changes, and only while a feature is on.
 */
@Serializable
@SerialName("sidebar")
data class Sidebar(val title: ServerText = ServerText(), val lines: List<ServerText> = emptyList()) : GameEvent

/**
 * The player opened a menu, or is back in their own inventory. Every slot is empty until a [SlotChanged]
 * says otherwise, unless the slots are [kept]: back in their own inventory, it holds what it held when the last menu
 * opened, and only the slots that changed since follow. [size] counts the player's own slots that the menu shows, and
 * [title] is null for the player's own inventory. A big menu's slots arrive over a few ticks. Posted only while a
 * feature reads it.
 */
@Serializable
@SerialName("container")
data class ContainerOpened(val id: Int, val title: ServerText? = null, val size: Int, val kept: Boolean = false) : GameEvent

/**
 * The line above the hotbar, each time the server sends it, even with the text it sent before. The server sends
 * one every half second, and one more the moment something changes it. Posted only while a feature reads it.
 */
@Serializable
@SerialName("actionbar")
data class ActionBar(val text: ServerText) : GameEvent

/**
 * The server's death event on an entity that is not a player: its [entity] type such as `minecraft:enderman`,
 * the [name] over its head and where it stood. A mob that only left the client's view has none. Posted only
 * while a feature reads it.
 */
@Serializable
@SerialName("death")
data class EntityDied(val entity: String, val name: ServerText, val x: Double, val y: Double, val z: Double) : GameEvent

/**
 * The server put a player head block at this place. A server builds markers from these, such as the head that
 * stands where the Endstone Protector will emerge. A head that came with its chunk has none. Posted only while a
 * feature reads it.
 */
@Serializable
@SerialName("head")
data class HeadPlaced(val x: Int, val y: Int, val z: Int) : GameEvent

/** A slot of the open container now holds [item], or is empty when it is null. Posted only for a slot that changed. */
@Serializable
@SerialName("slot")
data class SlotChanged(val slot: Int, val item: Item? = null) : GameEvent

/**
 * The pet nearest the player: an armor stand within a few blocks, named like `[Lvl 100] Ender Dragon`, wearing the
 * pet's [head]. dragsim.co floats each player's pet beside them, so a pet of a player standing close may be the
 * nearer one. Posted when the nearest pet changes, and only while a feature reads it.
 */
@Serializable
@SerialName("pet")
data class PetNear(val name: ServerText, val head: Item) : GameEvent

/**
 * The player's own attack landed on a living [entity] that is not an armor stand, such as `minecraft:enderman`, as the
 * client sends it. The server says nothing of a hit, so this event comes from the player rather than the server.
 * Posted only while a feature reads it.
 */
@Serializable
@SerialName("hit")
data class Hit(val entity: String) : GameEvent

/**
 * The server's clock: [gameTime] is how many ticks its world has run. The server sends it about once a second, so the
 * ticks between two of them over the time between them are its ticks per second. Posted only while a feature reads it.
 */
@Serializable
@SerialName("time")
data class ServerTime(val gameTime: Long) : GameEvent

/** The server answered a ping request after [millis], there and back. Posted only while a feature reads it. */
@Serializable
@SerialName("ping")
data class PingMeasured(val millis: Long) : GameEvent

/**
 * The frames the client drew in the last second, as the game counts them itself once a second. It comes from the
 * client rather than the server, and is posted once a second, only while a feature reads it.
 */
@Serializable
@SerialName("fps")
data class FrameRate(val fps: Int) : GameEvent
