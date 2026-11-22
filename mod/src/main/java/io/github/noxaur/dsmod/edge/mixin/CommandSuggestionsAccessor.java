package io.github.noxaur.dsmod.edge.mixin;

import java.util.List;
import net.minecraft.client.gui.components.CommandSuggestions;
import net.minecraft.util.FormattedCharSequence;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(CommandSuggestions.class)
public interface CommandSuggestionsAccessor {
    @Accessor
    CommandSuggestions.SuggestionsList getSuggestions();

    @Accessor
    List<FormattedCharSequence> getCommandUsage();

    @Accessor
    int getCommandUsagePosition();

    @Accessor
    int getCommandUsageWidth();
}
