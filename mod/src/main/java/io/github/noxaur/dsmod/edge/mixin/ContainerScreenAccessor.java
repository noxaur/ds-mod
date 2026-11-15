package io.github.noxaur.dsmod.edge.mixin;

import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Where the open container sits on screen and how big it is. Minecraft keeps all four protected. */
@Mixin(AbstractContainerScreen.class)
public interface ContainerScreenAccessor {
    @Accessor
    int getLeftPos();

    @Accessor
    int getTopPos();

    @Accessor
    int getImageWidth();

    @Accessor
    int getImageHeight();
}
