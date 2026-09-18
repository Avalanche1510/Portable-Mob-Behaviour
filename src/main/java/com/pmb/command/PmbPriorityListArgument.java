package com.pmb.command;

import com.google.gson.JsonObject;
import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import com.pmb.ai.PmbSkillPriorities;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.synchronization.ArgumentTypeInfo;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;

/** Strict mixed-list parser used by priority commands and matching persisted SNBT. */
public final class PmbPriorityListArgument implements ArgumentType<List<List<String>>> {
	private static final DynamicCommandExceptionType ERROR = new DynamicCommandExceptionType(
			message -> Component.literal(String.valueOf(message)));
	private static final List<String> IDS = PmbSkillPriorities.ids();

	@SuppressWarnings("unchecked")
	public static List<List<String>> get(CommandContext<?> context, String name) {
		return context.getArgument(name, List.class);
	}

	@Override public List<List<String>> parse(StringReader reader) throws CommandSyntaxException {
		int start = reader.getCursor();
		try {
			expect(reader, '[');
			skip(reader);
			List<List<String>> tiers = new ArrayList<>();
			if (reader.canRead() && reader.peek() == ']') throw new IllegalArgumentException("priority list cannot be empty");
			while (true) {
				if (!reader.canRead()) throw new IllegalArgumentException("expected priority entry");
				if (reader.peek() == '[') tiers.add(readGroup(reader));
				else tiers.add(List.of(readQuoted(reader)));
				skip(reader);
				if (!reader.canRead()) throw new IllegalArgumentException("expected ',' or ']'");
				char separator = reader.read();
				if (separator == ']') break;
				if (separator != ',') throw new IllegalArgumentException("expected ',' or ']'");
				skip(reader);
			}
			return PmbSkillPriorities.validate(tiers);
		} catch (IllegalArgumentException exception) {
			reader.setCursor(start);
			throw ERROR.createWithContext(reader, exception.getMessage());
		}
	}
	private static List<String> readGroup(StringReader reader) throws CommandSyntaxException {
		expect(reader, '['); skip(reader);
		List<String> group = new ArrayList<>();
		if (reader.canRead() && reader.peek() == ']') throw new IllegalArgumentException("priority group cannot be empty");
		while (true) {
			group.add(readQuoted(reader)); skip(reader);
			if (!reader.canRead()) throw new IllegalArgumentException("expected ',' or ']' in priority group");
			char separator = reader.read();
			if (separator == ']') return group;
			if (separator != ',') throw new IllegalArgumentException("expected ',' or ']' in priority group");
			skip(reader);
		}
	}
	private static String readQuoted(StringReader reader) throws CommandSyntaxException {
		if (!reader.canRead() || reader.peek() != '"') throw new IllegalArgumentException("skill ids must be quoted");
		return reader.readQuotedString();
	}
	private static void expect(StringReader reader, char value) {
		if (!reader.canRead() || reader.read() != value) throw new IllegalArgumentException("expected '" + value + "'");
	}
	private static void skip(StringReader reader) { while (reader.canRead() && Character.isWhitespace(reader.peek())) reader.skip(); }

	@Override public <S> CompletableFuture<Suggestions> listSuggestions(CommandContext<S> context, SuggestionsBuilder builder) {
		String text = builder.getRemaining();
		if (text.isEmpty()) return builder.suggest("[").buildFuture();
		if (!text.startsWith("[")) return Suggestions.empty();
		int depth = 0;
		int elementStart = 0;
		boolean inString = false;
		boolean escaped = false;
		for (int i = 0; i < text.length(); i++) {
			char value = text.charAt(i);
			if (inString) {
				if (escaped) escaped = false;
				else if (value == '\\') escaped = true;
				else if (value == '"') inString = false;
				continue;
			}
			if (value == '"') inString = true;
			else if (value == '[') { depth++; elementStart = i + 1; }
			else if (value == ']') { depth--; elementStart = i + 1; }
			else if (value == ',') elementStart = i + 1;
			if (depth < 0 || depth > 2) return Suggestions.empty();
		}
		if (depth < 1 || depth > 2) return Suggestions.empty();
		Set<String> used = new LinkedHashSet<>();
		for (String id : IDS) if (text.contains("\"" + id + "\"")) used.add(id);
		int offset = elementStart;
		while (offset < text.length() && Character.isWhitespace(text.charAt(offset))) offset++;
		SuggestionsBuilder target = builder.createOffset(builder.getStart() + offset);
		String prefix = text.substring(offset);
		for (String id : IDS) {
			String quoted = "\"" + id + "\"";
			if (!used.contains(id) && quoted.startsWith(prefix)) target.suggest(quoted);
		}
		if (depth == 1 && prefix.isEmpty()) target.suggest("[", Component.literal("start an equal-priority group"));
		return target.buildFuture();
	}

	public static final class Info implements ArgumentTypeInfo<PmbPriorityListArgument, Info.Template> {
		@Override public void serializeToNetwork(Template template, FriendlyByteBuf buffer) {}
		@Override public Template deserializeFromNetwork(FriendlyByteBuf buffer) { return new Template(); }
		@Override public void serializeToJson(Template template, JsonObject json) {}
		@Override public Template unpack(PmbPriorityListArgument argument) { return new Template(); }
		public final class Template implements ArgumentTypeInfo.Template<PmbPriorityListArgument> {
			@Override public PmbPriorityListArgument instantiate(CommandBuildContext context) { return new PmbPriorityListArgument(); }
			@Override public ArgumentTypeInfo<PmbPriorityListArgument, ?> type() { return Info.this; }
		}
	}
}
