package io.github.noxaur.dragen.edge.mixin;

import io.github.noxaur.dragen.edge.CommandKeys;
import io.github.noxaur.dragen.edge.Keys;
import io.github.noxaur.dragen.edge.input.InputApproved;
import net.minecraft.client.KeyboardHandler;
import net.minecraft.client.input.KeyEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@InputApproved(by = "noxaur", hash = "c08263968316")
@Mixin(KeyboardHandler.class)
abstract class KeyInputMixin {
    @Inject(method = "keyPress", at = @At("HEAD"))
    private void dragen$key(long handle, int action, KeyEvent event, CallbackInfo ci) {
        Keys.setAction(action);
        CommandKeys.INSTANCE.input(event.key(), action);
    }
}
