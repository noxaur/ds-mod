package io.github.noxaur.dsmod.edge.mixin;

import net.minecraft.client.gui.components.CommandSuggestions;
import net.minecraft.client.renderer.Rect2i;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(CommandSuggestions.SuggestionsList.class)
public interface SuggestionsListAccessor {
    @Accessor
    Rect2i getRect();
}
