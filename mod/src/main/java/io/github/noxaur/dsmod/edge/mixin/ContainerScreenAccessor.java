package io.github.noxaur.dsmod.edge.mixin;

import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Where the open container's slots start on screen. Minecraft keeps both protected. */
@Mixin(AbstractContainerScreen.class)
public interface ContainerScreenAccessor {
    @Accessor
    int getLeftPos();

    @Accessor
    int getTopPos();
}
