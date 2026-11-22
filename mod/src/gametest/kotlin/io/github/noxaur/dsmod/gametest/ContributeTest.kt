package io.github.noxaur.dsmod.gametest

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import io.github.noxaur.dsmod.core.backend.Backend
import io.github.noxaur.dsmod.core.backend.Identity
import io.github.noxaur.dsmod.core.backend.Request
import io.github.noxaur.dsmod.core.brief.Brief
import io.github.noxaur.dsmod.core.game.ServerSpan
import io.github.noxaur.dsmod.core.game.ServerText
import io.github.noxaur.dsmod.core.game.TabList
import io.github.noxaur.dsmod.core.ui.Theme
import io.github.noxaur.dsmod.edge.Account
import io.github.noxaur.dsmod.edge.DsModClient
import io.github.noxaur.dsmod.edge.capture.Recorder
import io.github.noxaur.dsmod.edge.capture.Recording
import io.github.noxaur.dsmod.edge.contribute.Contribute
import io.github.noxaur.dsmod.edge.contribute.ContributeScreen
import io.github.noxaur.dsmod.edge.ui.KitButton
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext
import org.lwjgl.glfw.GLFW
import java.net.InetSocketAddress
import java.nio.file.Path
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.zip.GZIPInputStream
import kotlin.io.path.exists
import kotlin.io.path.listDirectoryEntries

/**
 * Plays the player's side of a request against a stub that answers as `api/openapi.json` says, on a loopback port
 * of its own: the offers shown and filtered, Skip, Accept, the HUD with its time left, the redacted upload, a failed
 * upload kept and sent again by `/dsmod submit`, a refused one deleted, and runs that time out, are cancelled or
 * leave the world, which send nothing and leave nothing. Mojang is the one part it stands in for.
 */
class ContributeTest : FabricClientGameTest {
    private class Upload(val path: String, val type: String?, val bytes: ByteArray)

    private val brief = Brief(
        "Golem tiers",
        listOf(
            Brief.Step("Warp to the End"),
            Brief.Step("Stand still next to the Protector until it spawns", "Do not hit it.", 90),
            Brief.Step("Kill it"),
        ),
    )
    private fun request(id: String, timeout: Int = 600, vararg areas: String) =
        """{"id":"$id","brief":{"name":"${brief.name}","steps":[{"do":"Warp to the End"},{"do":"Stand still next to the Protector until it spawns","note":"Do not hit it.","seconds":90},{"do":"Kill it"}]},""" +
            """"requirements":[${areas.joinToString(",") { """{"kind":"area","value":"$it"}""" }}],"timeoutSeconds":$timeout}"""

    private val signIn = CountDownLatch(1)
    private val skips = CopyOnWriteArrayList<String>()
    private val uploads = CopyOnWriteArrayList<Upload>()

    @Volatile
    private var uploadStatus = 200

    private val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0).apply {
        createContext("/") { exchange -> respond(exchange) }
        start()
    }

    private fun respond(exchange: HttpExchange) {
        val path = exchange.requestURI.path
        val body = exchange.requestBody.readBytes()
        val signedIn = exchange.requestHeaders.getFirst("Authorization") == "Bearer s1"
        val (status, answer) = when {
            path == "/api/auth/minecraft/challenge" -> 200.also { signIn.await() } to """{"serverId":"${"0".repeat(40)}"}"""
            path == "/api/auth/minecraft/verify" -> 200 to """{"token":"s1"}"""
            !signedIn -> 401 to ""
            path == "/v1/contrib/requests" -> 200 to "[${request("end", 600, "The End")},${request("elsewhere", 600, "Crimson Isle")},${request("free")}]"
            path.endsWith("/skip") -> 204.also { skips += path } to ""
            path.endsWith("/submissions") -> uploadStatus.also {
                uploads += Upload(exchange.requestURI.toString(), exchange.requestHeaders.getFirst("Content-Type"), body)
            } to (if (uploadStatus == 200) """{"id":"s"}""" else "")
            else -> 404 to ""
        }
        val bytes = answer.toByteArray()
        exchange.sendResponseHeaders(status, if (bytes.isEmpty()) -1 else bytes.size.toLong())
        if (bytes.isNotEmpty()) exchange.responseBody.use { it.write(bytes) }
        exchange.close()
    }

    override fun runTest(context: ClientGameTestContext) {
        Account.backend = Backend("http://127.0.0.1:${server.address.port}", object : Identity {
            override val name = "Steve"
            override fun join(serverId: String) {}
        }, "test")
        val dir = Contribute.dir()
        val name = context.computeOnClient<String, Nothing> { it.user.name }
        try {
            context.worldBuilder().create().use {
                context.runOnClient<Nothing> { DsModClient.game.post(0, TabList(listOf(ServerText(listOf(ServerSpan("Area: The End")))))) }
                offers(context)
                longBrief(context)
                val upload = recorded(context, dir)
                check(upload.path == "/v1/contrib/requests/free/submissions?modVersion=test" && upload.type == "application/octet-stream") { "Uploaded to ${upload.path} as ${upload.type}" }
                val lines = GZIPInputStream(upload.bytes.inputStream()).bufferedReader().readLines()
                check(lines.count { "\"type\":\"step\"" in it } == 4) { "The upload's steps are ${lines.filter { "\"type\":\"step\"" in it }}" }
                check(lines.none { "\"type\":\"chat\"" in it }) { "The upload holds chat" }
                check(lines.none { name in it }) { "The upload names the player $name" }
                // Kept in the run folder, so the bytes can be sent through the real backend and pulled with dsctl.
                java.nio.file.Files.write(dir.resolveSibling("contribute-upload.jsonl.gz"), upload.bytes)

                keptAndResent(context, dir)
                refused(context, dir)
                discarded(context, dir, "timed out") { context.waitTicks(60) }
                discarded(context, dir, "cancelled") { context.runOnClient<Nothing> { Contribute.cancel() } }
                start(context, request("left"))
                press(context)
            }
            // Leaving the world is leaving the server, which ends the run and deletes its capture.
            waitUntil(context, "the run that left the world was deleted") { files(dir).isEmpty() && !Recorder.running }
            check(uploads.none { "/left/" in it.path }) { "A run that left the world was sent" }
        } finally {
            server.stop(0)
        }
    }

    private fun offers(context: ClientGameTestContext) {
        context.runOnClient<Nothing> { Contribute.open() }
        context.waitTicks(2)
        context.takeScreenshot("contribute-signing-in")
        signIn.countDown()
        waitUntil(context, "the offers arrived") { buttons().size == 3 }
        context.inTheme(Theme.VANILLA) { context.takeScreenshot("contribute-offer-vanilla") }
        context.takeScreenshot("contribute-offer-charcoal")
        // The End is offered first, since the player has been there, and the Crimson Isle never, since they have not.
        click(context, "Skip")
        waitUntil(context, "the skip reached the backend") { skips == listOf("/v1/contrib/requests/end/skip") }
        click(context, "Accept")
        check(context.computeOnClient<Boolean, Nothing> { it.gui.screen() == null && Recording.busy }) { "Accept did not start the run" }
    }

    /** A brief far longer than a window at Minecraft's smallest GUI height, 240, shows what fits and keeps its buttons on screen. */
    private fun longBrief(context: ClientGameTestContext) {
        val long = Request("long", Brief("Every tier", (1..30).map { Brief.Step("Do the thing for tier $it and wait for the next one", if (it % 3 == 0) "Do not hit it." else null) }), emptyList(), 600)
        for (theme in listOf(Theme.CHARCOAL, Theme.VANILLA)) context.inTheme(theme) {
            context.setScreen { ContributeScreen(signingIn = false).also { it.offer(listOf(long)) } }
            context.waitTicks(2)
            context.takeScreenshot("contribute-long-${theme.name.lowercase()}")
        }
        context.runOnClient<Nothing> { client ->
            val screen = client.gui.screen()!!
            check(screen.height <= 240) { "The window is ${screen.height} GUI pixels high, which is no small window" }
            check(buttons().map { it.message.string } == listOf("Accept", "Skip", "Close")) { "The buttons are ${buttons().map { it.message.string }}" }
            buttons().forEach { check(it.y >= 0 && it.y + it.height <= screen.height) { "${it.message.string} is at ${it.y} on a screen ${screen.height} high" } }
        }
        click(context, "Close")
        context.runOnClient<Nothing> { client -> check(client.gui.screen() == null) { "Close left ${client.gui.screen()} open instead of the game" } }
    }

    /** Plays the accepted run through with the key, shows the HUD, and returns what reached the backend. */
    private fun recorded(context: ClientGameTestContext, dir: Path): Upload {
        press(context)
        context.takeScreenshot("contribute-recording")
        repeat(3) { press(context) }
        waitUntil(context, "the run was sent and its files deleted") { uploads.isNotEmpty() && files(dir).isEmpty() }
        return uploads.single()
    }

    private fun keptAndResent(context: ClientGameTestContext, dir: Path) {
        uploadStatus = 503
        start(context, request("kept"))
        press(context)
        context.runOnClient<Nothing> { Contribute.submit() }
        waitUntil(context, "the failed upload was kept, redacted") { uploads.size == 2 && files(dir).map { it.fileName.toString() }.singleOrNull()?.startsWith("kept~") == true }
        uploadStatus = 200
        context.runOnClient<Nothing> { Contribute.submit() }
        waitUntil(context, "the kept recording was sent again and deleted") { uploads.size == 3 && files(dir).isEmpty() }
        check(uploads[1].bytes.contentEquals(uploads[2].bytes)) { "The resend sent another file" }
    }

    private fun refused(context: ClientGameTestContext, dir: Path) {
        uploadStatus = 409
        start(context, request("closed"))
        press(context)
        context.runOnClient<Nothing> { Contribute.submit() }
        waitUntil(context, "the refused recording was deleted") { uploads.size == 4 && files(dir).isEmpty() }
        uploadStatus = 200
    }

    private fun discarded(context: ClientGameTestContext, dir: Path, how: String, end: () -> Unit) {
        val before = uploads.size
        start(context, request(how.replace(' ', '-'), timeout = 2))
        press(context)
        check(context.computeOnClient<Boolean, Nothing> { Recorder.running }) { "The first press did not start the capture" }
        end()
        waitUntil(context, "the run that $how left nothing") { !Recording.busy && !Recorder.running && files(dir).isEmpty() }
        context.waitTicks(10)
        check(uploads.size == before) { "A run that $how was sent" }
    }

    private fun start(context: ClientGameTestContext, json: String) {
        val request = kotlinx.serialization.json.Json.decodeFromString(Request.serializer(), json)
        check(context.computeOnClient<Boolean, Nothing> { Contribute.accept(request) }) { "${request.id} was not accepted" }
    }

    private fun buttons() = (net.minecraft.client.Minecraft.getInstance().gui.screen() as? ContributeScreen)?.children()?.filterIsInstance<KitButton>().orEmpty()

    private fun click(context: ClientGameTestContext, label: String) {
        context.click(context.computeOnClient<KitButton, Nothing> { buttons().single { it.message.string == label } }, settle = 0)
        context.waitTicks(2)
    }

    private fun press(context: ClientGameTestContext) {
        context.input.pressKey(GLFW.GLFW_KEY_RIGHT_BRACKET)
        context.waitTicks(2)
    }

    private fun files(dir: Path): List<Path> = if (dir.exists()) dir.listDirectoryEntries() else emptyList()

    /** Waits for work on other threads, up to ten seconds. */
    private fun waitUntil(context: ClientGameTestContext, what: String, done: () -> Boolean) {
        repeat(200) {
            if (context.computeOnClient<Boolean, Nothing> { done() }) return
            context.waitTicks(1)
        }
        error("Timed out waiting until $what")
    }
}
