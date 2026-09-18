package com.pmb.ai;

import com.mojang.serialization.Codec;
import com.mojang.serialization.Dynamic;
import com.pmb.PortableMobBehaviour;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/** Persisted per-strategy skill ordering. Each inner list is one equal-priority tier. */
public final class PmbSkillPriorities {
	public static final String TAG = "skillPriorities";
	public static final String VANILLA = "vanilla";
	private static final List<String> IDS = List.of("mace", "ender_pearl", "wind_charge", "air_tracking", "bow", "shield", VANILLA);
	private static final Map<PmbSkillScheduler.Strategy, List<List<String>>> DEFAULTS = Map.of(
			PmbSkillScheduler.Strategy.COMBAT, tiers("mace", "ender_pearl", "wind_charge", "air_tracking", "bow", "shield", VANILLA),
			PmbSkillScheduler.Strategy.RETREAT, tiers("ender_pearl", "wind_charge", "air_tracking", VANILLA),
			PmbSkillScheduler.Strategy.IDLE, tiers(VANILLA));
	private final EnumMap<PmbSkillScheduler.Strategy, List<List<String>>> overrides =
			new EnumMap<>(PmbSkillScheduler.Strategy.class);

	public List<List<String>> effective(PmbSkillScheduler.Strategy strategy) {
		if (strategy == PmbSkillScheduler.Strategy.BLOCKED) return List.of();
		return overrides.getOrDefault(strategy, DEFAULTS.get(strategy));
	}

	public boolean hasOverride(PmbSkillScheduler.Strategy strategy) { return overrides.containsKey(strategy); }
	public static List<String> ids() { return IDS; }
	public boolean hasOverrides() { return !overrides.isEmpty(); }
	public void reset(PmbSkillScheduler.Strategy strategy) { overrides.remove(strategy); }
	public void resetAll() { overrides.clear(); }
	public void set(PmbSkillScheduler.Strategy strategy, List<List<String>> tiers) {
		if (strategy == PmbSkillScheduler.Strategy.BLOCKED) throw new IllegalArgumentException("BLOCKED has no priority list");
		overrides.put(strategy, validate(tiers));
	}

	public int tier(PmbSkillScheduler.Strategy strategy, String owner) {
		List<List<String>> tiers = effective(strategy);
		for (int i = 0; i < tiers.size(); i++) if (tiers.get(i).contains(owner)) return i;
		return -1;
	}

	public void read(ValueInput ai) {
		overrides.clear();
		ValueInput input = ai.childOrEmpty(TAG);
		for (PmbSkillScheduler.Strategy strategy : List.of(PmbSkillScheduler.Strategy.COMBAT,
				PmbSkillScheduler.Strategy.RETREAT, PmbSkillScheduler.Strategy.IDLE)) {
			input.read(key(strategy), Codec.PASSTHROUGH).ifPresent(dynamic -> {
				try {
					Tag tag = (Tag) dynamic.convert(NbtOps.INSTANCE).getValue();
					overrides.put(strategy, parse(tag));
				} catch (RuntimeException exception) {
					PortableMobBehaviour.LOGGER.warn("Invalid PMB {} priority list; using default: {}",
							key(strategy), exception.getMessage());
				}
			});
		}
	}

	public void write(ValueOutput ai) {
		if (overrides.isEmpty()) return;
		ValueOutput output = ai.child(TAG);
		for (var entry : overrides.entrySet())
			output.store(key(entry.getKey()), Codec.PASSTHROUGH,
					new Dynamic<>(NbtOps.INSTANCE, toTag(entry.getValue())));
	}

	public static List<List<String>> parse(Tag tag) {
		ListTag outer = tag.asList().orElseThrow(() -> new IllegalArgumentException("priority value must be a list"));
		List<List<String>> tiers = new ArrayList<>();
		for (Tag entry : outer) {
			if (entry.asString().isPresent()) tiers.add(List.of(entry.asString().orElseThrow()));
			else {
				ListTag group = entry.asList().orElseThrow(() -> new IllegalArgumentException("priority entry must be a quoted skill or list"));
				if (group.isEmpty()) throw new IllegalArgumentException("priority group cannot be empty");
				List<String> values = new ArrayList<>();
				for (Tag member : group) values.add(member.asString()
						.orElseThrow(() -> new IllegalArgumentException("priority group entries must be quoted skills")));
				tiers.add(List.copyOf(values));
			}
		}
		return validate(tiers);
	}

	public static ListTag toTag(List<List<String>> tiers) {
		ListTag outer = new ListTag();
		for (List<String> tier : validate(tiers)) {
			if (tier.size() == 1) outer.add(StringTag.valueOf(tier.getFirst()));
			else {
				ListTag group = new ListTag();
				for (String id : tier) group.add(StringTag.valueOf(id));
				outer.add(group);
			}
		}
		return outer;
	}

	public static List<List<String>> validate(List<List<String>> tiers) {
		if (tiers.isEmpty()) throw new IllegalArgumentException("priority list cannot be empty");
		Set<String> seen = new HashSet<>();
		List<List<String>> copy = new ArrayList<>();
		for (List<String> tier : tiers) {
			if (tier.isEmpty()) throw new IllegalArgumentException("priority group cannot be empty");
			List<String> group = new ArrayList<>();
			for (String id : tier) {
				if (!IDS.contains(id)) throw new IllegalArgumentException("unknown skill id: " + id);
				if (!seen.add(id)) throw new IllegalArgumentException("duplicate priority entry: " + id);
				group.add(id);
			}
			copy.add(List.copyOf(group));
		}
		if (!seen.contains(VANILLA)) throw new IllegalArgumentException("priority list must contain vanilla exactly once");
		return List.copyOf(copy);
	}

	private static String key(PmbSkillScheduler.Strategy strategy) { return strategy.name().toLowerCase(java.util.Locale.ROOT); }
	private static List<List<String>> tiers(String... ids) {
		List<List<String>> result = new ArrayList<>();
		for (String id : ids) result.add(List.of(id));
		return List.copyOf(result);
	}
}
