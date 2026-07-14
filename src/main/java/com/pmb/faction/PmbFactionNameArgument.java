package com.pmb.faction;

import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;

import java.util.Collection;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public final class PmbFactionNameArgument implements ArgumentType<String> {
	private static final SimpleCommandExceptionType EMPTY_NAME = new SimpleCommandExceptionType(
			Component.literal("Faction name cannot be empty"));

	private PmbFactionNameArgument() {
	}

	public static PmbFactionNameArgument factionName() {
		return new PmbFactionNameArgument();
	}

	public static String getFactionName(CommandContext<?> context, String name) {
		return context.getArgument(name, String.class);
	}

	@Override
	public String parse(StringReader reader) throws CommandSyntaxException {
		int start = reader.getCursor();
		while (reader.canRead() && !Character.isWhitespace(reader.peek())) {
			reader.skip();
		}
		if (reader.getCursor() == start) {
			throw EMPTY_NAME.createWithContext(reader);
		}
		return reader.getString().substring(start, reader.getCursor());
	}



	@Override
	public <S> CompletableFuture<Suggestions> listSuggestions(CommandContext<S> context, SuggestionsBuilder builder) {
		if (context.getSource() instanceof CommandSourceStack source) {
			return SharedSuggestionProvider.suggest(PmbFactionSavedData.get(source.getServer()).factions().keySet(), builder);
		}
		return Suggestions.empty();
	}
}
