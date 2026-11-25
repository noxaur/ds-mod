package io.github.noxaur.dsmod.edge.mixin;

import com.mojang.blaze3d.audio.Channel;
import io.github.noxaur.dsmod.edge.QuieterAfks;
import io.github.noxaur.dsmod.edge.input.InputApproved;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/** @keep The game never starts a sound whose volume works out to zero, so the mute goes on the channel, after the sound has started. */
@InputApproved(by = "noxaur", hash = "447cf506c881")
@Mixin(Channel.class)
abstract class QuietSoundMixin {
    @ModifyVariable(method = "setVolume", at = @At("HEAD"), argsOnly = true)
    private float dsmod$quiet(float volume) {
        return QuieterAfks.volume(volume);
    }
}
