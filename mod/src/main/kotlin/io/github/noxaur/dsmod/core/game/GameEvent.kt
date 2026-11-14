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

/** The client entered a world: on joining, and each time the server moves it to another one. */
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
 * The player opened a menu, or is back in their own inventory. Every slot is empty until a [SlotChanged]
 * says otherwise. [size] counts the player's own slots that the menu shows, and [title] is null for the
 * player's own inventory. Posted only while a feature reads it.
 */
@Serializable
@SerialName("container")
data class ContainerOpened(val id: Int, val title: ServerText? = null, val size: Int) : GameEvent

/** A slot of the open container now holds [item], or is empty when it is null. Posted only for a slot that changed. */
@Serializable
@SerialName("slot")
data class SlotChanged(val slot: Int, val item: Item? = null) : GameEvent
