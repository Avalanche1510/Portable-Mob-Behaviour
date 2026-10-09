package com.pmb.ai;

import com.mojang.serialization.Codec;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/** One conditional scene with ordered, independently configured traversal methods. */
public final class PmbTraversalGroupAiData implements PmbSkillConfigData {
	public static final String TAG = "traversal";
	public static final List<String> DEFAULT_METHODS = List.of("wind_charge", "ender_pearl");
	private final Set<String> explicit = new LinkedHashSet<>();
	private final PmbTraversalAiData wind = PmbTraversalAiData.wind();
	private final PmbTraversalAiData pearl = PmbTraversalAiData.pearl();
	private boolean configured;
	private boolean enabled;
	private List<String> methods = DEFAULT_METHODS;

	@Override public boolean isConfigured() { return configured; }
	@Override public Set<String> explicitFields() { return new LinkedHashSet<>(explicit); }
	public boolean isEnabled() { return configured && enabled; }
	public List<String> methods() { return methods; }
	public PmbTraversalAiData wind() { return wind; }
	public PmbTraversalAiData pearl() { return pearl; }

	public void read(ValueInput parent) {
		Optional<ValueInput> child = parent.child(TAG);
		if (child.isEmpty()) { clear(); return; }
		ValueInput input = child.get();
		configured = true;
		explicit.clear();
		enabled = PmbAiNbtReader.getBooleanOr(input, "enable", false);
		if (input.contains("enable")) explicit.add("enable");
		methods = readMethods(input);
		if (input.contains("methods")) explicit.add("methods");
		wind.clear(); pearl.clear();
		input.child("wind_charge").ifPresent(value -> wind.readNode(value));
		input.child("ender_pearl").ifPresent(value -> pearl.readNode(value));
	}

	private static List<String> readMethods(ValueInput input) {
		var list = input.list("methods", Codec.STRING);
		if (list.isEmpty()) return DEFAULT_METHODS;
		List<String> result = new ArrayList<>();
		for (String id : list.get())
			if (DEFAULT_METHODS.contains(id) && !result.contains(id)) result.add(id);
		return List.copyOf(result);
	}

	public void write(ValueOutput output) {
		if (explicit.contains("enable")) output.putBoolean("enable", enabled);
		if (explicit.contains("methods")) output.store("methods", Codec.STRING.listOf(), methods);
		if (wind.isConfigured()) wind.writeNode(output.child("wind_charge"));
		if (pearl.isConfigured()) pearl.writeNode(output.child("ender_pearl"));
	}

	@Override public void resetRuntime() { wind.resetRuntime(); pearl.resetRuntime(); }
	public void clear() {
		configured = false; enabled = false; methods = DEFAULT_METHODS; explicit.clear();
		wind.clear(); pearl.clear();
	}
}
