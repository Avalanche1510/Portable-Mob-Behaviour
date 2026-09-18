package com.pmb.ai;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import com.mojang.serialization.Codec;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.network.chat.Component;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;

/** Canonical command-facing schema for the five persisted PMB skills. */
public final class PmbSkillSchema {
	public enum Kind { BOOLEAN, INTEGER, FLOAT, STRING, ACTIVATION_SOURCES, AMMO_SOURCES, SKILL_IDS }
	public record Field(String name, Kind kind, Tag defaultValue, double min, double max, List<String> choices) {
		public String defaultText() { return defaultValue.toString(); }
		public String description() {
			return switch (kind) {
				case BOOLEAN -> "boolean 0b/1b; default " + defaultText();
				case INTEGER -> "integer " + rangeText() + "; default " + defaultText();
				case FLOAT -> "float with f suffix " + rangeText() + "; default " + defaultText();
				case STRING -> "quoted value " + choices + "; default " + defaultText();
				case ACTIVATION_SOURCES -> "quoted source list; default omitted";
				case AMMO_SOURCES -> "quoted ammunition-source list; default omitted";
				case SKILL_IDS -> "quoted PMB skill-id list";
			};
		}
		private String rangeText() { return "[" + formatBound(min) + ", " + formatBound(max) + "]"; }
	}

	private static final DynamicCommandExceptionType INVALID = new DynamicCommandExceptionType(
			message -> Component.literal(String.valueOf(message)));
	private static final Map<String, PmbSkillSchema> SCHEMAS = new LinkedHashMap<>();

	static {
		register(new PmbSkillSchema("shield", List.of(
				activation("offhand"), preferred("off"), bool("enable", false), flt("range", 8.0F, 0, 64), flt("shieldChance", 0.35F, 0, 1),
				integer("minUseTicks", 80, 1, 72000), integer("maxUseTicks", 160, 1, 72000),
				integer("cooldownTicks", 40, 0, 400), randomCooldownBias(), flt("blockingAngle", 30.0F, 1, 180),
				integer("axeDisableCooldownTicks", 100, 0, 600), integer("shieldToughness", 1, 1, 100),
				integer("critToughnessDamage", 1, 1, 100), integer("disableVulnerTicks", 40, 0, 72000),
				flt("vulnerDamageMultiplier", 2.0F, 1, 100), flt("disableKBMultiplier", 1.2F, 0, 100),
				flt("speedReduction", 0.5F, 0, 1))));
		register(new PmbSkillSchema("wind_charge", List.of(
				activation("mainhand", "offhand"), preferred("off"), bool("enable", false), flt("throwRange", 16.0F, 0, 64), flt("throwChance", 0.35F, 0, 1),
				integer("throwCooldownTicks", 40, 0, 72000), flt("throwAccuracy", 0.9F, 0, 1),
				flt("bounceRange", 4.0F, 0, 64), flt("bounceChance", 0.35F, 0, 1),
				integer("bounceCooldownTicks", 40, 0, 72000), randomCooldownBias(), bool("doConsume", false))));
		register(new PmbSkillSchema("air_tracking", List.of(
				bool("enable", false), skillIds("wind_charge", "mace"), flt("trackAcceleration", 0.012F, 0, 1),
				flt("trackMaxHorizontalSpeed", 0.3F, 0, 3), integer("trackDurationTicks", -1, -1, 72000),
				bool("requireEyeSight", true))));
		register(new PmbSkillSchema("mace", List.of(
				activation("mainhand"), bool("enable", false), flt("smashRange", 3.0F, 0, 64), flt("hitChance", 0.5F, 0, 1),
				flt("damageReduction", 0.5F, 0, 1), integer("smashCooldownTicks", 100, 0, 72000), randomCooldownBias())));
		register(new PmbSkillSchema("bow", List.of(
				activation("mainhand"), preferred("main"), ammo(), bool("enable", false), bool("doConsume", false), bool("requireEyeSight", true),
				string("modePriority", "line", "line", "arc"), bool("mobileWhileShooting", true),
				flt("lineMinRange", 0.0F, 0, 256), flt("lineMaxRange", 16.0F, 0, 256),
				flt("lineSafeDistance", 6.0F, 0, 256),
				integer("lineCooldownTicks", 40, 0, 72000), flt("lineShootChance", 0.8F, 0, 1),
				flt("lineShootAccuracy", 0.9F, 0, 1), integer("lineChargeTicks", 20, 0, 72000),
				flt("linePower", 1.0F, 0.1, 10), flt("arcMinRange", 16.0F, 0, 256),
				flt("arcMaxRange", 48.0F, 0, 256), flt("arcSafeDistance", 12.0F, 0, 256),
				integer("arcCooldownTicks", 60, 0, 72000), randomCooldownBias(), flt("arcShootChance", 0.8F, 0, 1),
				flt("arcShootAccuracy", 0.9F, 0, 1), integer("arcChargeTicks", 20, 0, 72000),
				flt("arcAngle", 42.0F, 1, 89), flt("arcMaxPower", 1.0F, 0.1, 10))));
		register(new PmbSkillSchema("ender_pearl", List.of(
				activation("mainhand", "offhand"), preferred("off"), bool("enable", false), flt("minThrowRange", 4.0F, 0, 256),
				flt("maxThrowRange", 16.0F, 0, 256), flt("throwChance", 0.35F, 0, 1),
				integer("throwCooldownTicks", 40, 0, 72000), flt("throwAccuracy", 0.9F, 0, 1),
				flt("maxThrowPower", 1.0F, 0.1, 10), flt("throwAngle", 30.0F, 1, 89),
				bool("doConsume", false), bool("requireEyeSight", true), randomCooldownBias())));
	}

	private final String id;
	private final LinkedHashMap<String, Field> fields = new LinkedHashMap<>();

	private PmbSkillSchema(String id, List<Field> fields) {
		this.id = id;
		for (Field field : fields) this.fields.put(field.name(), field);
	}
	public String id() { return id; }
	public Collection<Field> fields() { return fields.values(); }
	public Field field(String name) { return fields.get(name); }
	public boolean hasField(String name) { return fields.containsKey(name); }
	public static Collection<PmbSkillSchema> all() { return SCHEMAS.values(); }
	public static PmbSkillSchema byId(String id) { return SCHEMAS.get(id); }

	public CompoundTag defaultTag() {
		CompoundTag tag = new CompoundTag();
		for (Field field : fields.values()) {
			if (field.kind() != Kind.ACTIVATION_SOURCES && field.kind() != Kind.AMMO_SOURCES) tag.put(field.name(), field.defaultValue().copy());
		}
		return tag;
	}

	public void validate(CompoundTag values) throws CommandSyntaxException {
		for (Field field : fields.values()) {
			Tag tag = values.get(field.name());
			if (tag == null && field.kind() != Kind.ACTIVATION_SOURCES && field.kind() != Kind.AMMO_SOURCES) tag = field.defaultValue();
			if (tag == null) continue;
			switch (field.kind()) {
				case BOOLEAN -> {
					if (tag.getId() != Tag.TAG_BYTE || (tag.asByte().orElse((byte) -1) != 0 && tag.asByte().orElse((byte) -1) != 1))
						throw invalid(field.name() + " must be 0b or 1b");
				}
				case INTEGER -> {
					if (tag.getId() != Tag.TAG_INT) throw invalid(field.name() + " must be an integer without suffix");
					double number = tag.asDouble().orElse(Double.NaN);
					if (!Double.isFinite(number) || number < field.min() || number > field.max())
						throw invalid(field.name() + " must be in [" + formatBound(field.min()) + ", " + formatBound(field.max()) + "]");
				}
				case FLOAT -> {
					if (tag.getId() != Tag.TAG_FLOAT) throw invalid(field.name() + " must be a float with f suffix");
					double number = tag.asDouble().orElse(Double.NaN);
					if (!Double.isFinite(number) || number < field.min() || number > field.max())
						throw invalid(field.name() + " must be in [" + formatBound(field.min()) + ", " + formatBound(field.max()) + "]");
				}
				case STRING -> {
					String value = tag.asString().orElse("");
					if (!field.choices().contains(value)) throw invalid(field.name() + " must be one of " + field.choices());
				}
				case ACTIVATION_SOURCES, AMMO_SOURCES -> validateSources(tag, field.name());
				case SKILL_IDS -> validateSkillIds(tag, field.name());
			}
		}
		if (id.equals("shield") && integer(values, "minUseTicks", 80) > integer(values, "maxUseTicks", 160))
			throw invalid("minUseTicks cannot exceed maxUseTicks");
		if (id.equals("bow")) {
			if (number(values, "lineMinRange", 0) > number(values, "lineMaxRange", 16))
				throw invalid("lineMinRange cannot exceed lineMaxRange");
			if (number(values, "arcMinRange", 16) > number(values, "arcMaxRange", 48))
				throw invalid("arcMinRange cannot exceed arcMaxRange");
		}
		if (id.equals("ender_pearl") && number(values, "minThrowRange", 4) > number(values, "maxThrowRange", 16))
			throw invalid("minThrowRange cannot exceed maxThrowRange");
	}

	public static boolean validSource(String value) {
		if (value.equals("mainhand") || value.equals("offhand") || value.equals("inventory")) return true;
		if (!value.startsWith("inventory:")) return false;
		String range = value.substring("inventory:".length());
		try {
			int split = range.indexOf("..");
			int first = split < 0 ? Integer.parseInt(range) : Integer.parseInt(range.substring(0, split));
			int last = split < 0 ? first : Integer.parseInt(range.substring(split + 2));
			return first >= 1 && last <= PmbInventory.MAX_SLOTS && first <= last;
		} catch (NumberFormatException ignored) { return false; }
	}
	public static boolean isRegisteredSkill(String value) { return SCHEMAS.containsKey(value); }
	private static void validateSkillIds(Tag tag, String name) throws CommandSyntaxException {
		ListTag list = tag.asList().orElseThrow(() -> invalid(name + " must be a string list"));
		java.util.HashSet<String> seen = new java.util.HashSet<>();
		for (Tag entry : list) {
			String value = entry.asString().orElseThrow(() -> invalid(name + " must contain quoted strings"));
			if (!isRegisteredSkill(value) || value.equals("air_tracking") || !seen.add(value))
				throw invalid("Invalid " + name + " entry: " + value);
		}
	}

	private static void validateSources(Tag tag, String name) throws CommandSyntaxException {
		ListTag list = tag.asList().orElseThrow(() -> invalid(name + " must be a string list"));
		for (Tag entry : list) {
			String value = entry.asString().orElseThrow(() -> invalid(name + " must contain quoted strings"));
			if (!validSource(value)) throw invalid("Invalid " + name + " entry: " + value);
		}
	}
	private static int integer(CompoundTag tag, String key, int fallback) { return tag.getIntOr(key, fallback); }
	private static double number(CompoundTag tag, String key, double fallback) {
		Tag value = tag.get(key); return value == null ? fallback : value.asDouble().orElse(fallback);
	}
	private static Field activation(String... sources) {
		ListTag values = new ListTag(); for (String source : sources) values.add(StringTag.valueOf(source));
		return new Field("FetchSource", Kind.ACTIVATION_SOURCES, values, 0, 0, List.of());
	}
	private static Field preferred(String value) {
		return string("preferredHand", value, "main", "off", "main-enforce", "off-enforce");
	}
	private static Field randomCooldownBias() {
		return integer("randomCooldownBias", PmbSkillTiming.DEFAULT_RANDOM_COOLDOWN_BIAS,
				0, PmbSkillTiming.MAX_RANDOM_COOLDOWN_BIAS);
	}
	private static Field ammo() {
		return new Field("AmmoSource", Kind.AMMO_SOURCES, new ListTag(), 0, 0, List.of());
	}
	private static Field skillIds(String... values) {
		ListTag list = new ListTag(); for (String value : values) list.add(StringTag.valueOf(value));
		return new Field("activationSkills", Kind.SKILL_IDS, list, 0, 0, List.of());
	}
	private static Field bool(String name, boolean value) { return new Field(name, Kind.BOOLEAN, net.minecraft.nbt.ByteTag.valueOf(value), 0, 1, List.of()); }
	private static Field integer(String name, int value, double min, double max) { return new Field(name, Kind.INTEGER, net.minecraft.nbt.IntTag.valueOf(value), min, max, List.of()); }
	private static Field flt(String name, float value, double min, double max) { return new Field(name, Kind.FLOAT, net.minecraft.nbt.FloatTag.valueOf(value), min, max, List.of()); }
	private static Field string(String name, String value, String... choices) { return new Field(name, Kind.STRING, StringTag.valueOf(value), 0, 0, List.of(choices)); }
	private static void register(PmbSkillSchema schema) { SCHEMAS.put(schema.id, schema); }
	private static CommandSyntaxException invalid(String message) { return INVALID.create(message); }
	private static String formatBound(double value) { return value == Math.rint(value) ? Long.toString((long) value) : Double.toString(value); }
}
