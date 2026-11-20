package io.github.noxaur.dsmod.gametest.mixin;

import net.minecraft.client.MouseHandler;
import net.minecraft.client.input.MouseButtonInfo;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** A press with modifiers, as the system sends one. The game test's own press carries none. */
@Mixin(MouseHandler.class)
public interface MouseButtonInvoker {
    @Invoker
    void invokeOnButton(long handle, MouseButtonInfo button, int action);
}
