package io.github.noxaur.dsmod.rules

import com.tngtech.archunit.base.DescribedPredicate
import com.tngtech.archunit.base.DescribedPredicate.describe
import com.tngtech.archunit.core.domain.JavaAccess
import com.tngtech.archunit.core.domain.JavaClass
import com.tngtech.archunit.core.domain.JavaClass.Predicates.resideInAPackage
import com.tngtech.archunit.core.domain.JavaClass.Predicates.resideOutsideOfPackage
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses
import io.github.noxaur.dsmod.core.feature.inventory.BetterUi
import io.github.noxaur.dsmod.edge.DsModClient
import io.github.noxaur.dsmod.edge.Hooks
import io.github.noxaur.dsmod.edge.mixin.ContainerScreenAccessor
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents
import net.minecraft.client.multiplayer.ClientPacketListener
import kotlin.test.Test
import kotlin.test.assertEquals

private fun containerReads(classes: DescribedPredicate<JavaClass>) = noClasses().that(classes).should()
    .dependOnClassesThat().belongToAnyOf(ContainerScreenAccessor::class.java).because(
        "ContainerScreens.kt reads a container screen's place: `containerLeft`, `containerTop`, `containerWidth`, " +
            "`containerHeight`, `slotUnderMouse`, `screenX(slot)` and `screenY(slot)`, and `screen.drawInContainer(graphics) { .. }` " +
            "draws at a slot's own x and y. If a read is missing, add it there",
    )

private fun screenHooks(classes: DescribedPredicate<JavaClass>) = noClasses().that(classes).should()
    .accessField(ScreenEvents::class.java, "AFTER_INIT").because(
        "`ScreenHooks.onEveryLayout<ScreenType> { screen, opened -> .. }` adds to a screen on every layout, because Fabric drops " +
            "a screen's listeners on each one, and `opened` is true only on the first, so state for one menu survives a resize. " +
            "For a container screen, `openedByServer` and `creative` in `ContainerScreens.kt` name which ones",
    )

private fun groups(classes: DescribedPredicate<JavaClass>) = noClasses().that(classes).should()
    .dependOnClassesThat().belongToAnyOf(BetterUi::class.java).because(
        "a feature asks only for itself, as `backButtons.feature.runs`. A feature placed inside a group in " +
            "`SettingsTree` is on only while its group is",
    )

private fun wiring(classes: DescribedPredicate<JavaClass>) = noClasses().that(classes).should()
    .dependOnClassesThat().belongToAnyOf(DsModClient::class.java).because(
        "DsModClient only wires the mod together, so pass the dependency in. An object takes the Game, its features and its " +
            "Readouts in a `bind(..)` that `DsModClient.start()` calls on every start, and what lasts the whole session, such as " +
            "the config, in its `register(..)`. A class takes them in its constructor",
    )

private fun hooks(classes: DescribedPredicate<JavaClass>) = noClasses().that(classes).should()
    .dependOnClassesThat().belongToAnyOf(Hooks::class.java).because(
        "`Hooks` is only for what Mixin and Fabric build without arguments: the mixins and the entrypoints. Pass the dependency " +
            "in through a constructor or a `bind(..)` instead",
    )

private val sendsForPlayer = describe<JavaAccess<*>>("sends a command or a chat line") { access ->
    access.target.owner.isAssignableTo(ClientPacketListener::class.java) &&
        access.target.name in setOf("sendCommand", "sendChat", "sendUnattendedCommand")
}

private fun playerCommands(classes: DescribedPredicate<JavaClass>) = noClasses().that(classes).should()
    .accessTargetWhere(sendsForPlayer).because(
        "every command and chat line the mod sends for the player leaves from one place: `PlayerCommands.send(command)` and " +
            "`PlayerCommands.chat(line)`",
    )

private class ReadsPlace { fun run(screen: Any) = (screen as ContainerScreenAccessor).leftPos }
private class HooksScreen { fun run() = ScreenEvents.AFTER_INIT }
private class AsksGroup { fun run(ui: BetterUi) = ui.feature.runs }
private class LooksUp { fun run() = DsModClient.game }
private class AsksHooks { fun run() = Hooks.fullbright() }
private class SendsCommand { fun run(connection: ClientPacketListener) = connection.sendCommand("warp hub") }
private class RefersSend { fun run(connection: ClientPacketListener): (String) -> Unit = connection::sendChat }
private class Plain { fun run(connection: ClientPacketListener) = connection.isAcceptingMessages }

class OwnersTest {
    private val outsideScreens = outside("edge/ContainerScreens.kt", "edge/mixin/ContainerScreenAccessor.java")

    @Test
    fun `the release and debug code reads a container screen's place through ContainerScreens`() {
        containerReads(outsideScreens).check(modClasses)
    }

    @Test
    fun `the release and debug code adds to a screen through ScreenHooks`() {
        screenHooks(outside("edge/ScreenHooks.kt")).check(modClasses)
    }

    @Test
    fun `only SettingsTree names the Better UI group`() {
        groups(outside("core/config/SettingsTree.kt", "core/feature/inventory/BetterUi.kt")).check(releaseClasses)
    }

    @Test
    fun `the edge and debug code sends for the player only through PlayerCommands`() {
        val edgeOrDebug = resideInAPackage("io.github.noxaur.dsmod.edge..").or(resideInAPackage("io.github.noxaur.dsmod.debug.."))
        playerCommands(edgeOrDebug.and(outside("edge/PlayerCommands.kt"))).check(modClasses)
    }

    @Test
    fun `only DsModClient reaches DsModClient, and only the mixins and the entrypoints reach Hooks`() {
        wiring(outside("edge/DsModClient.kt")).check(modClasses)
        hooks(
            resideOutsideOfPackage("io.github.noxaur.dsmod.edge.mixin..")
                .and(outside("edge/DsModClient.kt", "edge/Hooks.kt", "edge/ModMenu.kt", "debug/DsModDebug.kt")),
        ).check(modClasses)
    }

    @Test
    fun `each rule catches its Kotlin and Java fixtures and passes the rest`() {
        assertEquals(listOf(ReadsPlace::class.java), flags(::containerReads, ReadsPlace::class.java, Plain::class.java))
        assertEquals(
            listOf(HooksScreen::class.java, JavaHooksScreen::class.java),
            flags(::screenHooks, HooksScreen::class.java, JavaHooksScreen::class.java, Plain::class.java),
        )
        assertEquals(
            listOf(AsksGroup::class.java, JavaAsksGroup::class.java),
            flags(::groups, AsksGroup::class.java, JavaAsksGroup::class.java, Plain::class.java),
        )
        assertEquals(
            listOf(SendsCommand::class.java, RefersSend::class.java, JavaRefersSend::class.java),
            flags(::playerCommands, SendsCommand::class.java, RefersSend::class.java, JavaRefersSend::class.java, Plain::class.java),
        )
        assertEquals(listOf(LooksUp::class.java, JavaLooksUp::class.java), flags(::wiring, LooksUp::class.java, JavaLooksUp::class.java, Plain::class.java))
        assertEquals(listOf(AsksHooks::class.java), flags(::hooks, AsksHooks::class.java, Plain::class.java))
    }
}
