package io.github.noxaur.dragen.gametest.mixin;

import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(Screen.class)
public interface NarrationInvoker {
    @Invoker
    void invokeUpdateNarrationState(NarrationElementOutput output);
}
