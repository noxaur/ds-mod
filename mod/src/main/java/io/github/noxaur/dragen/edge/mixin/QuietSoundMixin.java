package io.github.noxaur.dragen.edge.mixin;

import com.mojang.blaze3d.audio.Channel;
import io.github.noxaur.dragen.edge.QuieterAfks;
import io.github.noxaur.dragen.edge.input.InputApproved;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/** @keep The game never starts a sound whose volume works out to zero, so the mute goes on the channel, after the sound has started. */
@InputApproved(by = "noxaur", hash = "31bb5421f22a")
@Mixin(Channel.class)
abstract class QuietSoundMixin {
    @ModifyVariable(method = "setVolume", at = @At("HEAD"), argsOnly = true)
    private float dragen$quiet(float volume) {
        return QuieterAfks.volume(volume);
    }
}
