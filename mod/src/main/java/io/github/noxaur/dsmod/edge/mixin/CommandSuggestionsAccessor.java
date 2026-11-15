package io.github.noxaur.dsmod.edge.mixin;

import java.util.List;
import net.minecraft.client.gui.components.CommandSuggestions;
import net.minecraft.util.FormattedCharSequence;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * The open list of command suggestions, and the usage lines drawn in its place when none is open, with their left
 * edge and width. Minecraft keeps all of them private.
 */
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
