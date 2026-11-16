package io.github.noxaur.dsmod.edge.mixin;

import io.github.noxaur.dsmod.edge.EditedText;
import io.github.noxaur.dsmod.edge.FloatingEdit;
import io.github.noxaur.dsmod.edge.FloatingTexts;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * The floating-text layer's hook: the server's text reaches a text display through its synced data, and the display
 * reads it back through `getText` only to rebuild what it draws, so the edit is asked for once per text. An edit with a
 * time limit is dropped on the display's tick, which rebuilds it with the server's text.
 */
@Mixin(Display.TextDisplay.class)
abstract class FloatingTextMixin extends Display implements EditedText {
    @Shadow
    @Final
    private static EntityDataAccessor<Component> DATA_TEXT_ID;

    @Unique
    private FloatingEdit dsmod$edit;

    FloatingTextMixin(EntityType<?> type, Level level) {
        super(type, level);
    }

    @Override
    public Component dsmod$edit() {
        return dsmod$edit == null ? null : dsmod$edit.text;
    }

    @Inject(method = "onSyncedDataUpdated", at = @At("TAIL"))
    private void dsmod$textArrived(EntityDataAccessor<?> accessor, CallbackInfo ci) {
        if (accessor.equals(DATA_TEXT_ID) && level().isClientSide()) dsmod$edit = FloatingTexts.edit(getEntityData().get(DATA_TEXT_ID), tickCount);
    }

    @Inject(method = "getText", at = @At("RETURN"), cancellable = true)
    private void dsmod$editedText(CallbackInfoReturnable<Component> cir) {
        if (dsmod$edit != null) cir.setReturnValue(dsmod$edit.text);
    }

    @Override
    public void tick() {
        if (dsmod$edit != null && tickCount >= dsmod$edit.untilTick) {
            dsmod$edit = null;
            updateRenderState = true;
        }
        super.tick();
    }
}
