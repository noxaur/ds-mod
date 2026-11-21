package io.github.noxaur.dsmod.edge.mixin;

import io.github.noxaur.dsmod.edge.CommandKeys;
import io.github.noxaur.dsmod.edge.Keys;
import net.minecraft.client.KeyboardHandler;
import net.minecraft.client.input.KeyEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Command Binds: every press, repeat and release of a key, with a screen open or not, before the game acts on it. */
@Mixin(KeyboardHandler.class)
abstract class KeyInputMixin {
    @Inject(method = "keyPress", at = @At("HEAD"))
    private void dsmod$key(long handle, int action, KeyEvent event, CallbackInfo ci) {
        Keys.setAction(action);
        CommandKeys.INSTANCE.input(event.key(), action);
    }
}
