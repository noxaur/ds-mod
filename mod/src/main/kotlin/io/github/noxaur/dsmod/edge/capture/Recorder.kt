package io.github.noxaur.dsmod.edge.capture

import io.github.noxaur.dsmod.core.feature.Game
import io.github.noxaur.dsmod.core.capture.CaptureIncompleteException
import io.github.noxaur.dsmod.core.capture.RawRecord
import io.github.noxaur.dsmod.edge.Background
import io.github.noxaur.dsmod.edge.Ticks
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientEntityEvents
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents
import net.minecraft.client.Minecraft
import net.minecraft.network.protocol.Packet
import org.slf4j.LoggerFactory
import java.nio.file.Path
import java.util.concurrent.TimeUnit

object Recorder {
    private lateinit var game: Game

    fun bind(game: Game) {
        this.game = game
    }

    @Volatile
    private var session: CaptureSession? = null

    val running get() = session != null

    fun start(dir: Path): CaptureSession {
        check(session == null) { "A capture is already running." }
        return CaptureSession(dir, game).also { session = it }
    }

    fun stop(own: CaptureSession, done: (whole: Boolean) -> Unit = {}) {
        check(session === own) { "That capture is not the running one." }
        session = null
        own.detach()
        Background.io.execute { done(finish(own)) }
    }

    private fun finish(session: CaptureSession) = try {
        session.close()
        true
    } catch (e: CaptureIncompleteException) {
        LoggerFactory.getLogger("dsmod").error("The capture {} was not fully written", session.file, e)
        false
    }

    fun record(type: String, data: () -> JsonObject) {
        session?.record(RawRecord.of(type), data)
    }

    fun onPacket(packet: Packet<*>) {
        session?.packet(packet)
    }

    fun onInput(client: Minecraft, type: String) {
        session?.input(client, RawRecord.of(type))
    }

    /** @keep Events posted before this belong to the tick that just ended. Call it at the end of `END_CLIENT_TICK`, after the listeners that post. */
    fun endedClientTick() {
        session?.endedClientTick()
    }

    fun register() {
        ClientEntityEvents.ENTITY_UNLOAD.register { entity, _ -> session?.unload(entity) }
        ClientReceiveMessageEvents.CHAT.register { message, _, sender, _, _ ->
            session?.record(RawRecord.CHAT) {
                buildJsonObject {
                    sender?.let { put("sender", it.name) }
                    message.toCaptureJson().forEach { (key, value) -> put(key, value) }
                }
            }
        }
        Ticks.end { client -> session?.tick(client) }
    }

    fun quit() {
        session?.let(::stop)
        Background.io.shutdown()
        Background.io.awaitTermination(1, TimeUnit.MINUTES)
    }
}
