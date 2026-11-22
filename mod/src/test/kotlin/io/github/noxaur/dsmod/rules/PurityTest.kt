package io.github.noxaur.dsmod.rules

import com.tngtech.archunit.base.DescribedPredicate
import com.tngtech.archunit.core.domain.JavaClass
import com.tngtech.archunit.core.domain.JavaClass.Predicates.resideInAPackage
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses
import io.github.noxaur.dsmod.core.text.Text
import net.minecraft.client.Minecraft
import kotlin.test.Test
import kotlin.test.assertEquals

private fun pure(classes: DescribedPredicate<JavaClass>) = noClasses().that(classes).should().dependOnClassesThat()
    .resideInAnyPackage("net.minecraft..", "com.mojang..", "net.fabricmc..", "io.github.noxaur.dsmod.edge..")
    .because(
        "core takes plain data and returns plain models. Read Minecraft in the edge package and pass the values in, " +
            "and return what the player should see for the edge to draw",
    )

private class Calls {
    fun run() = Minecraft.getInstance()
}

private class Holds(val edge: io.github.noxaur.dsmod.edge.Chat?)

private class Takes {
    fun run(command: com.mojang.brigadier.Command<*>) = command
}

private class Fine {
    fun run(name: String, text: Text) = name to text
}

class PurityTest {
    @Test
    fun `core names nothing from Minecraft, Fabric, Mojang or edge`() {
        pure(resideInAPackage("io.github.noxaur.dsmod.core..")).check(releaseClasses)
    }

    @Test
    fun `the rule catches a call, a field and a parameter, and passes plain types`() {
        val impure = flags(::pure, Calls::class.java, Holds::class.java, Takes::class.java, Fine::class.java)
        assertEquals(listOf(Calls::class.java, Holds::class.java, Takes::class.java), impure)
    }
}
