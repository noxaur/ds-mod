package io.github.noxaur.dsmod.gametest

import io.github.noxaur.dsmod.core.text.text
import io.github.noxaur.dsmod.edge.Chat
import io.github.noxaur.dsmod.edge.toComponent
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext

class ChatTest : FabricClientGameTest {
    override fun runTest(context: ClientGameTestContext) {
        context.worldBuilder().create().use {
            val sold = text {
                body("Sold ")
                value(1204)
                body(" scales. ")
                muted("12/h")
            }
            context.runOnClient<Nothing> {
                Chat.send(sold)
                Chat.send(text {
                    good("Ready. ")
                    warning("Almost full. ")
                    bad("Open the storage menu to load it.")
                })
                Chat.send(text { action("Click to open the tracker.") })
            }
            context.waitTicks(5)
            context.takeScreenshot("chat")

            val shown = context.computeOnClient<String, Nothing> { sold.toComponent().string }
            check(shown == "Sold 1,204 scales. 12/h") { "Chat line lost text, got '$shown'" }
        }
    }
}
