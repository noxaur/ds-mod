package io.github.noxaur.dsmod.rules

import io.github.noxaur.dsmod.core.config.SettingsTree
import io.github.noxaur.dsmod.core.game.Area
import java.nio.file.Path
import kotlin.io.path.readText
import kotlin.test.Test
import kotlin.test.assertEquals

private const val DESCRIPTION_MAX = 67

private val FOLDERS = mapOf(
    "village" to Area.VILLAGE,
    "crypts" to Area.CRYPTS,
    "end" to Area.END,
    "sepulture" to Area.VOID_SEPULTURE,
    "dungeonhub" to Area.DUNGEON_HUB,
    "dungeon" to Area.CATACOMBS,
)

class RulesTest {
    @Test
    fun `every feature has a section in the player docs`() {
        val docs = Path.of("../docs/user/features.md").readText()
        val missing = SettingsTree().features.map { it.title }.filter { "### $it\n" !in docs }
        assertEquals(emptyList(), missing, "docs/user/features.md needs a `### <Name>` section that tells the player what each of these shows: $missing\n")
    }

    @Test
    fun `every feature has a line in the README`() {
        val readme = Path.of("../README.md").readText()
        val missing = SettingsTree().features.map { it.title }.filter { "- **$it** – " !in readme }
        assertEquals(emptyList(), missing, "README.md needs a line `- **<Name>** – <one short sentence>` under a group in Features for each of these. Screenshots go in docs/user/features.md: $missing\n")
    }

    @Test
    fun `every setting's description fits the settings screen at full size`() {
        val long = SettingsTree().sections.flatMap { it.walk() }.filter { it.description.length > DESCRIPTION_MAX }.map { "${it.name}: ${it.description}" }
        assertEquals(emptyList(), long, "Cut each description in SettingsTree to one short sentence of at most $DESCRIPTION_MAX characters. The player docs hold the rest: $long\n")
    }

    @Test
    fun `a feature in an area's folder is locked to that area`() {
        val wrong = SettingsTree().owners.filter { owner ->
            val folder = owner.javaClass.packageName.removePrefix("io.github.noxaur.dsmod.core.feature.").substringBefore('.')
            owner.feature.area != (FOLDERS[folder] ?: Area.ANYWHERE)
        }.map { "${it.javaClass.simpleName} is ${it.feature.area}" }
        assertEquals(
            emptyList(),
            wrong,
            "A feature in core/feature/<folder>/ passes the `Area` with that folder to `Feature(..)`, and a feature locked to an " +
                "area lives in its folder. One that runs everywhere lives in another folder and passes `Area.ANYWHERE`: $wrong\n",
        )
    }
}
