package io.github.noxaur.dsmod.edge

import com.mojang.blaze3d.platform.NativeImage
import io.github.noxaur.dsmod.core.config.DsModConfig
import io.github.noxaur.dsmod.core.container.SlotArt
import io.github.noxaur.dsmod.core.container.SlotStyle
import io.github.noxaur.dsmod.core.text.Role
import io.github.noxaur.dsmod.edge.ui.Atlas
import net.minecraft.client.Minecraft
import net.minecraft.client.renderer.texture.DynamicTexture
import net.minecraft.resources.Identifier
import net.minecraft.util.ARGB
import org.slf4j.LoggerFactory

/** Where a rarity mark is drawn. Each has its own style, and the hotbar's sits over the world, so it is always dark. */
enum class Surface { CONTAINER, HOTBAR }

/**
 * Bakes the player's rarity style into one atlas per [Surface]: a row of 18x18 backs, one cell per [Role], and a row
 * of gems under it. A slot then costs one blit, and the atlas is painted again only when its style changes or the
 * resource packs make a chest's slots dark or light.
 */
object SlotArts {
    private class Baked(val texture: DynamicTexture, val atlas: Atlas) {
        var style: SlotStyle? = null
        var dark = false
    }

    private val width = Role.entries.size * SlotArt.SIZE
    private val height = SlotArt.SIZE + SlotArt.GEM
    private val baked = HashMap<Surface, Baked>()
    private val chest = Identifier.withDefaultNamespace("textures/gui/container/generic_54.png")
    private val log = LoggerFactory.getLogger("dsmod")
    private var packs: List<String>? = null

    /** Whether the resource packs draw a chest's slots dark, read from the pixel inside its first slot. */
    var containerDark = false
        private set

    fun style(settings: DsModConfig.Inventory, surface: Surface): SlotStyle =
        if (surface == Surface.HOTBAR && !settings.hotbarMatches) settings.hotbarStyle else settings.containerStyle()

    fun atlas(settings: DsModConfig.Inventory, surface: Surface): Atlas {
        val style = style(settings, surface)
        val dark = surface == Surface.HOTBAR || containerDark
        val art = baked.getOrPut(surface) { create(surface.name.lowercase()) }
        if (art.style != style || art.dark != dark) paint(art, style, dark)
        return art.atlas
    }

    /** Reads the chest's slot again when the selected resource packs changed. Call it as a container screen opens. */
    fun checkPacks() {
        val client = Minecraft.getInstance()
        val selected = client.resourcePackRepository.selectedIds.toList()
        if (selected == packs) return
        packs = selected
        containerDark = runCatching {
            client.resourceManager.getResource(chest).map { resource ->
                resource.open().use { stream -> NativeImage.read(stream).use { it.getPixel(CHEST_SLOT_X, CHEST_SLOT_Y) } }
            }.map { (ARGB.red(it) * 299 + ARGB.green(it) * 587 + ARGB.blue(it) * 114) / 1000 < DARK_BELOW }.orElse(false)
        }.onFailure { log.warn("Could not read the chest texture, so rarity marks assume a light slot", it) }.getOrDefault(false)
    }

    private fun create(name: String): Baked {
        val id = Identifier.fromNamespaceAndPath("dsmod", "rarity/$name")
        val texture = DynamicTexture({ "ds-mod rarity $name" }, NativeImage(width, height, true))
        Minecraft.getInstance().textureManager.register(id, texture)
        return Baked(texture, Atlas(id, width, height))
    }

    private fun paint(art: Baked, style: SlotStyle, dark: Boolean) {
        val image = art.texture.pixels ?: return
        for (role in Role.entries) {
            val left = role.ordinal * SlotArt.SIZE
            val back = SlotArt.back(style, role.rgb, dark)
            for (j in 0 until SlotArt.SIZE) for (i in 0 until SlotArt.SIZE) image.setPixel(left + i, j, back[j * SlotArt.SIZE + i])
            val gem = SlotArt.gem(role.rgb)
            for (j in 0 until SlotArt.GEM) for (i in 0 until SlotArt.GEM) image.setPixel(left + i, SlotArt.SIZE + j, gem[j * SlotArt.GEM + i])
        }
        art.texture.upload()
        art.style = style
        art.dark = dark
    }

    private const val CHEST_SLOT_X = 8
    private const val CHEST_SLOT_Y = 18
    private const val DARK_BELOW = 90
}
