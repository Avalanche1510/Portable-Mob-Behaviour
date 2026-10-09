package com.pmb.ai;

import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.Set;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

import static com.pmb.ai.PmbAiNbtReader.clamp;
import static com.pmb.ai.PmbAiNbtReader.getFloatOr;
import static com.pmb.ai.PmbAiNbtReader.getIntOr;

/** Shared pathfinding-owned airborne steering parameters. Runtime contexts are never persisted. */
public final class PmbAirTrackingAiData implements PmbSkillConfigData {
	public static final String TAG = "air_tracking";
	private boolean configured;
	private final Set<String> explicit = new LinkedHashSet<>();
	private float trackAcceleration = 0.012F;
	private float trackMaxHorizontalSpeed = 0.3F;
	private int trackDurationTicks = -1;

	@Override public boolean isConfigured() { return configured; }
	@Override public Set<String> explicitFields() { return new LinkedHashSet<>(explicit); }
	public float trackAcceleration() { return trackAcceleration; }
	public float trackMaxHorizontalSpeed() { return trackMaxHorizontalSpeed; }
	public int trackDurationTicks() { return trackDurationTicks; }
	public void set(float acceleration, float maxSpeed, int duration) {
		configured = true;
		trackAcceleration = clamp(acceleration, 0, 1);
		trackMaxHorizontalSpeed = clamp(maxSpeed, 0, 3);
		trackDurationTicks = clamp(duration, -1, 72000);
		explicit.addAll(java.util.List.of("trackAcceleration", "trackMaxHorizontalSpeed", "trackDurationTicks"));
	}

	public void readDirect(ValueInput pathfinding) {
		Optional<ValueInput> child = pathfinding.child(TAG);
		if (child.isEmpty()) { clear(); return; }
		ValueInput input = child.get();
		configured = true;
		explicit.clear();
		for (String key : java.util.List.of("trackAcceleration", "trackMaxHorizontalSpeed", "trackDurationTicks"))
			if (PmbAiNbtReader.hasField(input, key)) explicit.add(key);
		trackAcceleration = clamp(getFloatOr(input, "trackAcceleration", 0.012F), 0, 1);
		trackMaxHorizontalSpeed = clamp(getFloatOr(input, "trackMaxHorizontalSpeed", 0.3F), 0, 3);
		trackDurationTicks = clamp(getIntOr(input, "trackDurationTicks", -1), -1, 72000);
	}

	public void read(ValueInput input) { readDirect(input.childOrEmpty(PmbPathfindingData.TAG)); }
	public void writeDirect(ValueOutput pathfinding) {
		ValueOutput output = pathfinding.child(TAG);
		output.putFloat("trackAcceleration", trackAcceleration);
		output.putFloat("trackMaxHorizontalSpeed", trackMaxHorizontalSpeed);
		output.putInt("trackDurationTicks", trackDurationTicks);
	}
	public void write(ValueOutput output) { writeDirect(output); }
	@Override public void resetRuntime() {}
	public void clear() {
		configured = false;
		explicit.clear();
		trackAcceleration = 0.012F;
		trackMaxHorizontalSpeed = 0.3F;
		trackDurationTicks = -1;
	}
}
