package io.github.noxaur.dragen.gametest.mixin;

import net.minecraft.client.MouseHandler;
import net.minecraft.client.input.MouseButtonInfo;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(MouseHandler.class)
public interface MouseButtonInvoker {
    @Invoker
    void invokeOnButton(long handle, MouseButtonInfo button, int action);
}
