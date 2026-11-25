package io.github.noxaur.dragen.edge.mixin;

import io.github.noxaur.dragen.edge.EditedText;
import io.github.noxaur.dragen.edge.FloatingTexts;
import io.github.noxaur.dragen.edge.input.InputApproved;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.world.entity.Display;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** @keep The server's text reaches a text display through its synced data, and the display reads it back through `getText`
 * only to rebuild what it draws, so the edit is asked for once per text. */
@InputApproved(by = "noxaur", hash = "f55a5bf0aee3")
@Mixin(Display.TextDisplay.class)
abstract class FloatingTextMixin implements EditedText {
    @Shadow
    @Final
    private static EntityDataAccessor<Component> DATA_TEXT_ID;

    @Unique
    private Component dragen$edit;

    @Override
    public Component dragen$edit() {
        return dragen$edit;
    }

    @Inject(method = "onSyncedDataUpdated", at = @At("TAIL"))
    private void dragen$textArrived(EntityDataAccessor<?> accessor, CallbackInfo ci) {
        Display.TextDisplay self = (Display.TextDisplay) (Object) this;
        if (accessor.equals(DATA_TEXT_ID) && self.level().isClientSide()) dragen$edit = FloatingTexts.edit(self.getEntityData().get(DATA_TEXT_ID));
    }

    @Inject(method = "getText", at = @At("RETURN"), cancellable = true)
    private void dragen$editedText(CallbackInfoReturnable<Component> cir) {
        if (dragen$edit != null) cir.setReturnValue(dragen$edit);
    }
}
