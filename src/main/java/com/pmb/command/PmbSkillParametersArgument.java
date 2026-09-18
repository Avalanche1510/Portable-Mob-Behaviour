package com.pmb.command;

import com.google.gson.JsonObject;
import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import com.pmb.ai.PmbSkillSchema;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.synchronization.ArgumentTypeInfo;
import net.minecraft.nbt.ByteTag;
import net.minecraft.nbt.FloatTag;
import net.minecraft.nbt.IntTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;

/** Strict bracket parser with schema-aware client command-tree serialization. */
public final class PmbSkillParametersArgument implements ArgumentType<PmbSkillPatch> {
	private static final DynamicCommandExceptionType ERROR = new DynamicCommandExceptionType(
			message -> Component.literal(String.valueOf(message)));
	private final String skill;
	private final boolean delete;

	public PmbSkillParametersArgument(String skill, boolean delete) {
		if (PmbSkillSchema.byId(skill) == null) throw new IllegalArgumentException("Unknown PMB skill " + skill);
		this.skill = skill;
		this.delete = delete;
	}
	public String skill() { return skill; }
	public boolean delete() { return delete; }
	public static PmbSkillPatch get(CommandContext<?> context, String name) { return context.getArgument(name, PmbSkillPatch.class); }

	@Override
	public PmbSkillPatch parse(StringReader reader) throws CommandSyntaxException {
		PmbSkillSchema schema = PmbSkillSchema.byId(skill);
		int start = reader.getCursor();
		expect(reader, '[', "Expected '['");
		skipWhitespace(reader);
		Map<String, Tag> values = new LinkedHashMap<>();
		List<String> keys = new ArrayList<>();
		if (reader.canRead() && reader.peek() == ']') { reader.skip(); return new PmbSkillPatch(values, keys); }
		while (true) {
			int keyCursor = reader.getCursor();
			String key = readKey(reader);
			PmbSkillSchema.Field field = schema.field(key);
			if (field == null) { reader.setCursor(keyCursor); throw ERROR.createWithContext(reader, "Unknown parameter for " + skill + ": " + key); }
			if (keys.contains(key)) { reader.setCursor(keyCursor); throw ERROR.createWithContext(reader, "Duplicate parameter: " + key); }
			keys.add(key);
			skipWhitespace(reader);
			if (!delete) {
				expect(reader, '=', "Expected '=' after " + key);
				skipWhitespace(reader);
				values.put(key, readValue(reader, field));
			}
			skipWhitespace(reader);
			if (!reader.canRead()) throw ERROR.createWithContext(reader, "Expected ',' or ']'");
			char separator = reader.read();
			if (separator == ']') break;
			if (separator != ',') throw ERROR.createWithContext(reader, "Expected ',' or ']'");
			skipWhitespace(reader);
			if (!reader.canRead() || reader.peek() == ']') throw ERROR.createWithContext(reader, "Expected parameter after ','");
		}
		return new PmbSkillPatch(values, keys);
	}

	private Tag readValue(StringReader reader, PmbSkillSchema.Field field) throws CommandSyntaxException {
		int start = reader.getCursor();
		return switch (field.kind()) {
			case BOOLEAN -> {
				String token = readToken(reader);
				if (!token.equals("0b") && !token.equals("1b")) failAt(reader, start, field.name() + " requires 0b or 1b");
				yield ByteTag.valueOf(token.equals("1b"));
			}
			case INTEGER -> {
				String token = readToken(reader);
				if (!token.matches("-?[0-9]+")) failAt(reader, start, field.name() + " requires an integer without suffix");
				try {
					int value = Integer.parseInt(token);
					if (value < field.min() || value > field.max()) failAt(reader, start, field.description());
					yield IntTag.valueOf(value);
				} catch (NumberFormatException exception) { failAt(reader, start, field.name() + " is outside the integer range"); yield IntTag.valueOf(0); }
			}
			case FLOAT -> {
				String token = readToken(reader);
				if (token.length() < 2 || !(token.endsWith("f") || token.endsWith("F"))) failAt(reader, start, field.name() + " requires an f suffix");
				try {
					float value = Float.parseFloat(token.substring(0, token.length() - 1));
					if (!Float.isFinite(value) || value < field.min() || value > field.max()) failAt(reader, start, field.description());
					yield FloatTag.valueOf(value);
				} catch (NumberFormatException exception) { failAt(reader, start, field.name() + " requires a finite float with f suffix"); yield FloatTag.valueOf(0); }
			}
			case STRING -> {
				if (!reader.canRead() || reader.peek() != '"') failAt(reader, start, field.name() + " requires a quoted string");
				String value = reader.readQuotedString();
				if (!field.choices().contains(value)) failAt(reader, start, field.description());
				yield StringTag.valueOf(value);
			}
			case ACTIVATION_SOURCES, AMMO_SOURCES -> readSources(reader, field.name());
			case SKILL_IDS -> readSkillIds(reader, field.name());
		};
	}

	private ListTag readSources(StringReader reader, String key) throws CommandSyntaxException {
		ListTag result = new ListTag();
		expect(reader, '[', "Expected '[' for " + key);
		skipWhitespace(reader);
		if (reader.canRead() && reader.peek() == ']') { reader.skip(); return result; }
		while (true) {
			int start = reader.getCursor();
			if (!reader.canRead() || reader.peek() != '"') failAt(reader, start, key + " entries must be quoted");
			String value = reader.readQuotedString();
			if (!PmbSkillSchema.validSource(value)) failAt(reader, start, "Invalid " + key + " entry: " + value);
			result.add(StringTag.valueOf(value));
			skipWhitespace(reader);
			if (!reader.canRead()) throw ERROR.createWithContext(reader, "Expected ',' or ']' in " + key);
			char separator = reader.read();
			if (separator == ']') break;
			if (separator != ',') throw ERROR.createWithContext(reader, "Expected ',' or ']' in " + key);
			skipWhitespace(reader);
		}
		return result;
	}
	private ListTag readSkillIds(StringReader reader, String key) throws CommandSyntaxException {
		ListTag result = new ListTag(); java.util.HashSet<String> seen = new java.util.HashSet<>();
		expect(reader, '[', "Expected '[' for " + key); skipWhitespace(reader);
		if (reader.canRead() && reader.peek() == ']') { reader.skip(); return result; }
		while (true) {
			int start = reader.getCursor();
			if (!reader.canRead() || reader.peek() != '"') failAt(reader, start, key + " entries must be quoted");
			String value = reader.readQuotedString();
			if (!PmbSkillSchema.isRegisteredSkill(value) || value.equals("air_tracking") || !seen.add(value))
				failAt(reader, start, "Invalid " + key + " entry: " + value);
			result.add(StringTag.valueOf(value)); skipWhitespace(reader);
			if (!reader.canRead()) throw ERROR.createWithContext(reader, "Expected ',' or ']' in " + key);
			char separator = reader.read(); if (separator == ']') break;
			if (separator != ',') throw ERROR.createWithContext(reader, "Expected ',' or ']' in " + key);
			skipWhitespace(reader);
		}
		return result;
	}

	@Override
	public <S> CompletableFuture<Suggestions> listSuggestions(CommandContext<S> context, SuggestionsBuilder builder) {
		String text = builder.getRemaining();
		if (text.isEmpty()) return builder.createOffset(builder.getStart()).suggest("[").buildFuture();
		if (text.charAt(0) != '[') return Suggestions.empty();
		PmbSkillSchema schema = PmbSkillSchema.byId(skill);
		Set<String> used = scanUsedKeys(text);
		int valueEquals = currentValueEquals(text);
		if (!delete && valueEquals >= 0) {
			String key = currentKey(text, valueEquals);
			PmbSkillSchema.Field field = schema.field(key);
			SuggestionsBuilder valueBuilder = builder.createOffset(builder.getStart() + valueEquals + 1);
			String remaining = text.substring(valueEquals + 1).trim();
			if (field != null && isCompleteValue(field, remaining)) return separators(builder, text.length()).buildFuture();
			if (field != null) valueBuilder = suggestValue(field, text.substring(valueEquals + 1), valueBuilder);
			return valueBuilder.buildFuture();
		}
		int offset = currentEntryOffset(text);
		SuggestionsBuilder target = builder.createOffset(builder.getStart() + offset);
		String prefix = text.substring(offset).trim();
		if (delete && schema.hasField(prefix)) return separators(builder, text.length()).buildFuture();
		for (PmbSkillSchema.Field field : schema.fields()) {
			if (!used.contains(field.name()) && field.name().startsWith(prefix))
				target.suggest(field.name() + (delete ? "" : "="), Component.literal(field.description()));
		}
		return target.buildFuture();
	}
	private SuggestionsBuilder separators(SuggestionsBuilder builder, int end) {
		SuggestionsBuilder result = builder.createOffset(builder.getStart() + end);
		result.suggest(",", Component.literal("add another parameter"));
		result.suggest("]", Component.literal("finish parameter list"));
		return result;
	}
	private boolean isCompleteValue(PmbSkillSchema.Field field, String text) {
		try {
			StringReader reader = new StringReader(text);
			readValue(reader, field);
			return !reader.canRead();
		} catch (CommandSyntaxException ignored) { return false; }
	}

	private static SuggestionsBuilder suggestValue(PmbSkillSchema.Field field, String remaining, SuggestionsBuilder builder) {
		String prefix = remaining.trim();
		SuggestionsBuilder result = builder;
		switch (field.kind()) {
			case BOOLEAN -> { if ("0b".startsWith(prefix)) builder.suggest("0b"); if ("1b".startsWith(prefix)) builder.suggest("1b"); }
			case STRING -> field.choices().stream().map(value -> "\"" + value + "\"").filter(value -> value.startsWith(prefix)).forEach(builder::suggest);
			case INTEGER, FLOAT -> { if (field.defaultText().startsWith(prefix)) builder.suggest(field.defaultText(), Component.literal(field.description())); }
			case ACTIVATION_SOURCES, AMMO_SOURCES -> {
				String[] choices = {"\"mainhand\"", "\"offhand\"", "\"inventory\"", "\"inventory:1..9\"",
						"\"inventory:7..16\"", "\"inventory:256\""};
				int leadingWhitespace = leadingWhitespace(remaining);
				String sourceText = remaining.substring(leadingWhitespace);
				int sourceBase = builder.getStart() + leadingWhitespace;
				int entry = sourceEntryOffset(sourceText);
				String sourcePrefix = sourceText.substring(entry);
				SuggestionsBuilder sourceBuilder;
				if (isCompleteSource(sourcePrefix.trim())) {
					sourceBuilder = builder.createOffset(sourceBase + sourceText.length());
					sourceBuilder.suggest(",", Component.literal("add another " + field.name() + " entry"));
					sourceBuilder.suggest("]", Component.literal("finish " + field.name() + " list"));
				} else {
					sourceBuilder = builder.createOffset(sourceBase + entry);
					for (String choice : choices) if (choice.startsWith(sourcePrefix)) sourceBuilder.suggest(choice);
				}
				result = sourceBuilder;
			}
			case SKILL_IDS -> {
				String[] choices = PmbSkillSchema.all().stream().map(PmbSkillSchema::id)
						.filter(id -> !id.equals("air_tracking")).toArray(String[]::new);
				int leadingWhitespace = leadingWhitespace(remaining); String listText = remaining.substring(leadingWhitespace);
				int base = builder.getStart() + leadingWhitespace; int entry = sourceEntryOffset(listText);
				String prefixEntry = listText.substring(entry);
				SuggestionsBuilder listBuilder;
				if (isCompleteSkillId(prefixEntry.trim())) {
					listBuilder = builder.createOffset(base + listText.length()); listBuilder.suggest(","); listBuilder.suggest("]");
				} else { listBuilder = builder.createOffset(base + entry);
					for (String choice : choices) { String quoted = "\"" + choice + "\""; if (quoted.startsWith(prefixEntry)) listBuilder.suggest(quoted); }
				}
				result = listBuilder;
			}
		}
		return result;
	}
	private static int leadingWhitespace(String text) {
		int count = 0;
		while (count < text.length() && Character.isWhitespace(text.charAt(count))) count++;
		return count;
	}
	private static boolean isCompleteSource(String text) {
		try {
			StringReader reader = new StringReader(text);
			if (!reader.canRead() || reader.peek() != '"') return false;
			String value = reader.readQuotedString();
			return !reader.canRead() && PmbSkillSchema.validSource(value);
		} catch (CommandSyntaxException ignored) { return false; }
	}
	private static boolean isCompleteSkillId(String text) {
		try { StringReader reader = new StringReader(text); if (!reader.canRead() || reader.peek() != '"') return false;
			String value = reader.readQuotedString(); return !reader.canRead() && PmbSkillSchema.isRegisteredSkill(value)
					&& !value.equals("air_tracking"); } catch (CommandSyntaxException ignored) { return false; }
	}
	private static int sourceEntryOffset(String text) {
		int offset = text.startsWith("[") ? 1 : 0; boolean quoted = false;
		for (int i = 0; i < text.length(); i++) {
			char c = text.charAt(i);
			if (c == '"' && (i == 0 || text.charAt(i - 1) != '\\')) quoted = !quoted;
			if (!quoted && c == ',') offset = i + 1;
		}
		while (offset < text.length() && Character.isWhitespace(text.charAt(offset))) offset++;
		return offset;
	}

	private static int currentEntryOffset(String text) {
		int depth = 0; boolean quoted = false; int offset = text.startsWith("[") ? 1 : 0;
		for (int i = 0; i < text.length(); i++) {
			char c = text.charAt(i);
			if (c == '"' && (i == 0 || text.charAt(i - 1) != '\\')) quoted = !quoted;
			if (quoted) continue;
			if (c == '[') depth++; else if (c == ']') depth--; else if (c == ',' && depth == 1) offset = i + 1;
		}
		while (offset < text.length() && Character.isWhitespace(text.charAt(offset))) offset++;
		return offset;
	}
	private static int currentValueEquals(String text) {
		int start = currentEntryOffset(text); int depth = 0; boolean quoted = false;
		for (int i = start; i < text.length(); i++) {
			char c = text.charAt(i);
			if (c == '"' && (i == 0 || text.charAt(i - 1) != '\\')) quoted = !quoted;
			if (!quoted) { if (c == '[') depth++; else if (c == ']') depth--; else if (c == '=' && depth == 0) return i; }
		}
		return -1;
	}
	private static String currentKey(String text, int equals) { return text.substring(currentEntryOffset(text), equals).trim(); }
	private static Set<String> scanUsedKeys(String text) {
		Set<String> result = new LinkedHashSet<>();
		int depth = 0; boolean quoted = false; int start = text.startsWith("[") ? 1 : 0;
		for (int i = 0; i < text.length(); i++) {
			char c = text.charAt(i);
			if (c == '"' && (i == 0 || text.charAt(i - 1) != '\\')) quoted = !quoted;
			if (!quoted) {
				if (c == '[') depth++; else if (c == ']') depth--;
				if (c == ',' && depth == 1) {
					String entry = text.substring(start, i).trim(); int equals = entry.indexOf('=');
					String key = (equals < 0 ? entry : entry.substring(0, equals)).trim(); if (!key.isEmpty()) result.add(key);
					start = i + 1;
				}
			}
		}
		return result;
	}
	private static String readKey(StringReader reader) {
		int start = reader.getCursor(); while (reader.canRead() && (Character.isLetterOrDigit(reader.peek()) || reader.peek() == '_')) reader.skip();
		return reader.getString().substring(start, reader.getCursor());
	}
	private static String readToken(StringReader reader) {
		int start = reader.getCursor(); while (reader.canRead() && reader.peek() != ',' && reader.peek() != ']' && !Character.isWhitespace(reader.peek())) reader.skip();
		return reader.getString().substring(start, reader.getCursor());
	}
	private static void skipWhitespace(StringReader reader) { while (reader.canRead() && Character.isWhitespace(reader.peek())) reader.skip(); }
	private static void expect(StringReader reader, char expected, String message) throws CommandSyntaxException {
		if (!reader.canRead() || reader.read() != expected) throw ERROR.createWithContext(reader, message);
	}
	private static void failAt(StringReader reader, int cursor, String message) throws CommandSyntaxException {
		reader.setCursor(cursor); throw ERROR.createWithContext(reader, message);
	}

	public static final class Info implements ArgumentTypeInfo<PmbSkillParametersArgument, Info.Template> {
		@Override public void serializeToNetwork(Template template, FriendlyByteBuf buffer) { buffer.writeUtf(template.skill); buffer.writeBoolean(template.delete); }
		@Override public Template deserializeFromNetwork(FriendlyByteBuf buffer) { return new Template(buffer.readUtf(), buffer.readBoolean()); }
		@Override public void serializeToJson(Template template, JsonObject json) { json.addProperty("skill", template.skill); json.addProperty("delete", template.delete); }
		@Override public Template unpack(PmbSkillParametersArgument argument) { return new Template(argument.skill, argument.delete); }
		public final class Template implements ArgumentTypeInfo.Template<PmbSkillParametersArgument> {
			private final String skill; private final boolean delete;
			private Template(String skill, boolean delete) { this.skill = skill; this.delete = delete; }
			@Override public PmbSkillParametersArgument instantiate(CommandBuildContext context) { return new PmbSkillParametersArgument(skill, delete); }
			@Override public ArgumentTypeInfo<PmbSkillParametersArgument, ?> type() { return Info.this; }
		}
	}
}
