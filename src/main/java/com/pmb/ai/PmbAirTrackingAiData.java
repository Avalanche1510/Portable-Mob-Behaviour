package com.pmb.ai;

import com.pmb.PortableMobBehaviour;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

import static com.pmb.ai.PmbAiNbtReader.clamp;
import static com.pmb.ai.PmbAiNbtReader.getBooleanOr;
import static com.pmb.ai.PmbAiNbtReader.getFloatOr;
import static com.pmb.ai.PmbAiNbtReader.getIntOr;

/** Shared airborne steering policy. Runtime sessions deliberately remain server-only. */
public final class PmbAirTrackingAiData implements PmbSkillConfigData {
	public static final String TAG = "air_tracking";
	public static final List<String> DEFAULT_ACTIVATION_SKILLS = List.of("wind_charge", "mace");
	private boolean configured;
	private final Set<String> explicit = new LinkedHashSet<>();
	private boolean enabled;
	private List<String> activationSkills = DEFAULT_ACTIVATION_SKILLS;
	private float trackAcceleration = 0.012F;
	private float trackMaxHorizontalSpeed = 0.3F;
	private int trackDurationTicks = -1;
	private boolean requireEyeSight = true;

	@Override public boolean isConfigured() { return configured; }
	@Override public Set<String> explicitFields() { return new LinkedHashSet<>(explicit); }
	public boolean isEnabled() { return configured && enabled; }
	public List<String> activationSkills() { return activationSkills; }
	public float trackAcceleration() { return trackAcceleration; }
	public float trackMaxHorizontalSpeed() { return trackMaxHorizontalSpeed; }
	public int trackDurationTicks() { return trackDurationTicks; }
	public boolean requireEyeSight() { return requireEyeSight; }

	public void read(ValueInput ai) {
		Optional<ValueInput> child = ai.child(TAG);
		if (child.isEmpty()) { clear(); return; }
		ValueInput input = child.get();
		configured = true; explicit.clear();
		for (String key : List.of("enable", "activationSkills", "trackAcceleration", "trackMaxHorizontalSpeed",
				"trackDurationTicks", "requireEyeSight")) if (PmbAiNbtReader.hasField(input, key)) explicit.add(key);
		enabled = getBooleanOr(input, "enable", false);
		activationSkills = readSkills(input);
		trackAcceleration = clamp(getFloatOr(input, "trackAcceleration", 0.012F), 0, 1);
		trackMaxHorizontalSpeed = clamp(getFloatOr(input, "trackMaxHorizontalSpeed", 0.3F), 0, 3);
		trackDurationTicks = clamp(getIntOr(input, "trackDurationTicks", -1), -1, 72000);
		requireEyeSight = getBooleanOr(input, "requireEyeSight", true);
	}
	private List<String> readSkills(ValueInput input) {
		Optional<? extends Iterable<String>> raw = input.list("activationSkills", com.mojang.serialization.Codec.STRING);
		if (raw.isEmpty()) {
			if (input.contains("activationSkills")) {
				PortableMobBehaviour.LOGGER.warn("Invalid PMB air_tracking activationSkills list; using an empty list");
				return List.of();
			}
			return DEFAULT_ACTIVATION_SKILLS;
		}
		List<String> result = new ArrayList<>();
		for (String id : raw.get()) {
			if (!PmbSkillSchema.isRegisteredSkill(id) || TAG.equals(id) || result.contains(id)) {
				PortableMobBehaviour.LOGGER.warn("Invalid PMB air_tracking activationSkills entry '{}'; using an empty list", id);
				return List.of();
			}
			result.add(id);
		}
		return List.copyOf(result);
	}
	public void write(ValueOutput output) {
		if (explicit.contains("enable")) output.putBoolean("enable", enabled);
		if (explicit.contains("activationSkills")) output.store("activationSkills", com.mojang.serialization.Codec.STRING.listOf(), activationSkills);
		if (explicit.contains("trackAcceleration")) output.putFloat("trackAcceleration", trackAcceleration);
		if (explicit.contains("trackMaxHorizontalSpeed")) output.putFloat("trackMaxHorizontalSpeed", trackMaxHorizontalSpeed);
		if (explicit.contains("trackDurationTicks")) output.putInt("trackDurationTicks", trackDurationTicks);
		if (explicit.contains("requireEyeSight")) output.putBoolean("requireEyeSight", requireEyeSight);
	}
	@Override public void resetRuntime() {}
	public void clear() {
		configured = false; explicit.clear(); enabled = false; activationSkills = DEFAULT_ACTIVATION_SKILLS;
		trackAcceleration = 0.012F; trackMaxHorizontalSpeed = 0.3F; trackDurationTicks = -1; requireEyeSight = true;
	}
}
