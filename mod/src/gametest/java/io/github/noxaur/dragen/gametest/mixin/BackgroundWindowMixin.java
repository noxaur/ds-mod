package io.github.noxaur.dragen.gametest.mixin;

import com.mojang.blaze3d.platform.Window;
import com.mojang.blaze3d.systems.GpuBackend;
import io.github.noxaur.dragen.gametest.Background;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** @keep Window hints last only until the next window is created, so they are set here, after the game's own. */
@Mixin(Window.class)
abstract class BackgroundWindowMixin {
    @Inject(
        method = "createGlfwWindow",
        at = @At(value = "INVOKE", target = "Lorg/lwjgl/glfw/GLFW;glfwCreateWindow(IILjava/lang/CharSequence;JJ)J")
    )
    private static void dragen$background(int width, int height, String title, long monitor, GpuBackend backend, CallbackInfoReturnable<Long> ci) {
        Background.beforeWindow();
    }
}
