package io.github.noxaur.dsmod.gametest.mixin;

import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.inventory.Slot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(AbstractContainerScreen.class)
public interface HoveredSlotAccessor {
    @Accessor
    void setHoveredSlot(Slot slot);
}
