package io.github.noxaur.dsmod.edge

import com.mojang.blaze3d.platform.NativeImage
import io.github.noxaur.dsmod.core.feature.inventory.ItemRarity
import io.github.noxaur.dsmod.core.container.SlotArt
import io.github.noxaur.dsmod.core.container.SlotStyle
import io.github.noxaur.dsmod.core.text.Role
import io.github.noxaur.dsmod.edge.ui.Atlas
import io.github.noxaur.dsmod.edge.ui.Kit
import net.minecraft.client.Minecraft
import net.minecraft.client.renderer.texture.DynamicTexture
import net.minecraft.resources.Identifier
import org.slf4j.LoggerFactory

enum class Surface { CONTAINER, HOTBAR }

object SlotArts {
    private class Baked(val texture: DynamicTexture, val atlas: Atlas)

    internal class Ready(val atlas: Atlas, val style: SlotStyle, val dark: Boolean)

    private val width = Role.entries.size * SlotArt.SIZE
    private val height = SlotArt.VARIANTS * SlotArt.SIZE
    private val baked = HashMap<Surface, Baked>()
    internal val ready = HashMap<Surface, Ready>()
    internal val wantedStyle = arrayOfNulls<SlotStyle>(Surface.entries.size)
    private val wantedDark = BooleanArray(Surface.entries.size)
    private val chest = Identifier.withDefaultNamespace("textures/gui/container/generic_54.png")
    private val log = LoggerFactory.getLogger("dsmod")
    private var packs: List<String>? = null
    private var packDark = false

    var containerDark = false
        private set

    fun style(settings: ItemRarity, surface: Surface): SlotStyle =
        if (surface == Surface.HOTBAR) settings.hotbarLook() else settings.containerStyle()

    fun atlas(settings: ItemRarity, surface: Surface): Atlas? {
        val style = style(settings, surface)
        val dark = surface == Surface.HOTBAR || containerDark
        wantedStyle[surface.ordinal] = style
        wantedDark[surface.ordinal] = dark
        return ready[surface]?.atlas
    }

    /* @keep A texture made or uploaded while a frame is being built broke every frame after it in the game tests, so the
     * paint happens at the end of a tick, between frames. */
    fun tick(bake: (Surface, SlotStyle, Boolean) -> Atlas = ::paintAndUpload) {
        for (surface in Surface.entries) {
            val style = wantedStyle[surface.ordinal] ?: continue
            val dark = wantedDark[surface.ordinal]
            val have = ready[surface]
            if (have != null && have.style.mark == style.mark && have.style.sparkles == style.sparkles && have.dark == dark) continue
            ready[surface] = Ready(bake(surface, style, dark), style, dark)
        }
    }

    fun register() {
        Ticks.end { client ->
            checkSlots(client)
            tick()
        }
    }

    private fun checkSlots(client: Minecraft) {
        val selected = client.resourcePackRepository.selectedIds.toList()
        if (selected != packs && client.gui.overlay() == null) {
            packs = selected
            packDark = runCatching {
                client.resourceManager.getResource(chest).map { resource ->
                    resource.open().use { stream -> NativeImage.read(stream).use { it.getPixel(CHEST_SLOT_X, CHEST_SLOT_Y) } }
                }.map { Kit.dark(it) }.orElse(false)
            }.onFailure { log.warn("Could not read the chest texture, so rarity marks assume a light slot", it) }.getOrDefault(false)
        }
        containerDark = if (MenuSkin.themed) MenuSkin.glassAlpha >= 0 || Kit.dark(Kit.theme.slot) else packDark
    }

    private fun paintAndUpload(surface: Surface, style: SlotStyle, dark: Boolean): Atlas {
        val art = baked.getOrPut(surface) { create(surface.name.lowercase()) }
        paint(art, style, dark)
        return art.atlas
    }

    private fun create(name: String): Baked {
        val id = Identifier.fromNamespaceAndPath("dsmod", "rarity/$name")
        val texture = DynamicTexture({ "ds-mod rarity $name" }, NativeImage(width, height, true))
        Minecraft.getInstance().textureManager.register(id, texture)
        return Baked(texture, Atlas(id, width, height))
    }

    private fun paint(art: Baked, style: SlotStyle, dark: Boolean) {
        val image = art.texture.pixels ?: return
        for (role in Role.RARITIES) {
            val left = role.ordinal * SlotArt.SIZE
            for (variant in 0 until SlotArt.VARIANTS) {
                val back = SlotArt.back(style, role.rgb, dark, variant)
                val top = variant * SlotArt.SIZE
                for (j in 0 until SlotArt.SIZE) for (i in 0 until SlotArt.SIZE) image.setPixel(left + i, top + j, back[j * SlotArt.SIZE + i])
            }
        }
        art.texture.upload()
    }

    private const val CHEST_SLOT_X = 8
    private const val CHEST_SLOT_Y = 18
}
