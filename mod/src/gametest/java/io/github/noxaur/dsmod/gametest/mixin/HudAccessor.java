package io.github.noxaur.dsmod.gametest.mixin;

import net.minecraft.client.gui.Hud;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** The ticks left on the held item's name over the hotbar. A test clears it instead of waiting for the name to fade. */
@Mixin(Hud.class)
public interface HudAccessor {
    @Accessor
    void setToolHighlightTimer(int ticks);
}
