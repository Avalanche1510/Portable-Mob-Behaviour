package com.pmb.ai;

import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.Set;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

import static com.pmb.ai.PmbAiNbtReader.clamp;
import static com.pmb.ai.PmbAiNbtReader.getBooleanOr;
import static com.pmb.ai.PmbAiNbtReader.getFloatOr;
import static com.pmb.ai.PmbAiNbtReader.getIntOr;

/** Configuration shared by deterministic wind and pearl path-edge actions. */
public final class PmbTraversalAiData implements PmbSkillConfigData {
	public static final String WIND_TAG = "wind_charge";
	public static final String PEARL_TAG = "ender_pearl";
	private final String tag;
	private final float defaultDistance;
	private final float defaultHeight;
	private boolean configured;
	private final Set<String> explicit = new LinkedHashSet<>();
	private final PmbActivationSources fetchSource = new PmbActivationSources();
	private PmbPreferredHand preferredHand = PmbPreferredHand.OFF;
	private boolean enabled = true;
	private boolean doConsume;
	private boolean doTeleportDamage;
	private int cooldownTicks = 40;
	private int randomCooldownBias = 20;
	private float maxConnectionDistance;
	private float minHorizontalConnectionDistance;
	private float maxConnectionHeight;
	private int minSavedTicks = 10;
	private int postJumpTicks = 2;
	private float throwAngle = 30.0F;
	private float maxThrowPower = 1.0F;
	private float throwAccuracy = 1.0F;
	private int cooldown;

	private PmbTraversalAiData(String tag, float distance, float height) {
		this.tag = tag; defaultDistance = distance; defaultHeight = height;
		maxConnectionDistance = distance; maxConnectionHeight = height;
	}
	public static PmbTraversalAiData wind() { return new PmbTraversalAiData(WIND_TAG, 16, 8); }
	public static PmbTraversalAiData pearl() { return new PmbTraversalAiData(PEARL_TAG, 64, 64); }
	public String tag() { return tag; }
	@Override public boolean isConfigured() { return configured; }
	@Override public Set<String> explicitFields() { return new LinkedHashSet<>(explicit); }
	public boolean isEnabled() { return enabled; }
	public PmbActivationSources fetchSource() { return fetchSource; }
	public PmbPreferredHand preferredHand() { return preferredHand; }
	public boolean doConsume() { return doConsume; }
	public boolean doTeleportDamage() { return doTeleportDamage; }
	public float maxConnectionDistance() { return maxConnectionDistance; }
	public float minHorizontalConnectionDistance() { return minHorizontalConnectionDistance; }
	public float maxConnectionHeight() { return maxConnectionHeight; }
	public int minSavedTicks() { return minSavedTicks; }
	public int postJumpTicks() { return postJumpTicks; }
	public float throwAngle() { return throwAngle; }
	public float maxThrowPower() { return maxThrowPower; }
	public float throwAccuracy() { return throwAccuracy; }
	public boolean canCommit() { return cooldown <= 0; }
	public void committed(net.minecraft.util.RandomSource random) {
		cooldown = PmbSkillTiming.cooldown(random, cooldownTicks, randomCooldownBias);
	}
	public void tickCooldown() { if (cooldown > 0) cooldown--; }
	int cooldownRemaining() { return cooldown; }
	void restoreCooldown(int elapsed, int remaining) {
		cooldown = Math.max(0, Math.min(cooldownTicks + randomCooldownBias, Math.max(0, remaining)) - elapsed);
	}

	public void read(ValueInput parent) {
		Optional<ValueInput> child = parent.child(tag);
		if (child.isEmpty()) { clear(); return; }
		readNode(child.get());
	}
	public void readNode(ValueInput input) {
		configured = true; explicit.clear();
		preferredHand = PmbPreferredHand.parse(input.getStringOr("preferredHand", ""), PmbPreferredHand.OFF);
		fetchSource.read(input);
		for (String key : java.util.List.of("enable", "preferredHand", "FetchSource", "doConsume",
				"doTeleportDamage", "cooldownTicks", "randomCooldownBias", "maxConnectionDistance",
				"minHorizontalConnectionDistance",
				"maxConnectionHeight", "minSavedTicks", "postJumpTicks", "throwAngle", "maxThrowPower", "throwAccuracy"))
			if (PmbAiNbtReader.hasField(input, key)) explicit.add(key);
		enabled = getBooleanOr(input, "enable", true);
		doConsume = getBooleanOr(input, "doConsume", false);
		doTeleportDamage = tag.equals(PEARL_TAG) && getBooleanOr(input, "doTeleportDamage", false);
		cooldownTicks = clamp(getIntOr(input, "cooldownTicks", 40), 0, 72000);
		randomCooldownBias = clamp(getIntOr(input, "randomCooldownBias", 20), 0, 1200);
		maxConnectionDistance = clamp(getFloatOr(input, "maxConnectionDistance", defaultDistance), 0, 256);
		minHorizontalConnectionDistance = tag.equals(WIND_TAG)
				? clamp(getFloatOr(input, "minHorizontalConnectionDistance", 0), 0, 256) : 0;
		maxConnectionHeight = clamp(getFloatOr(input, "maxConnectionHeight", defaultHeight), 0, 256);
		minSavedTicks = clamp(getIntOr(input, "minSavedTicks", 10), 0, 72000);
		postJumpTicks = tag.equals(WIND_TAG) ? clamp(getIntOr(input, "postJumpTicks", 2), 0, 10) : 2;
		throwAngle = clamp(getFloatOr(input, "throwAngle", 30), 1, 89);
		maxThrowPower = clamp(getFloatOr(input, "maxThrowPower", 1), .1F, 10);
		throwAccuracy = clamp(getFloatOr(input, "throwAccuracy", 1), 0, 1);
		cooldown = 0;
	}
	public void writeNode(ValueOutput output) { write(output); }
	public void write(ValueOutput output) {
		if (explicit.contains("preferredHand")) output.putString("preferredHand", preferredHand.value());
		if (explicit.contains("FetchSource")) fetchSource.write(output);
		if (explicit.contains("enable")) output.putBoolean("enable", enabled);
		if (explicit.contains("doConsume")) output.putBoolean("doConsume", doConsume);
		if (tag.equals(PEARL_TAG) && explicit.contains("doTeleportDamage")) output.putBoolean("doTeleportDamage", doTeleportDamage);
		if (explicit.contains("cooldownTicks")) output.putInt("cooldownTicks", cooldownTicks);
		if (explicit.contains("randomCooldownBias")) output.putInt("randomCooldownBias", randomCooldownBias);
		if (explicit.contains("maxConnectionDistance")) output.putFloat("maxConnectionDistance", maxConnectionDistance);
		if (tag.equals(WIND_TAG) && explicit.contains("minHorizontalConnectionDistance"))
			output.putFloat("minHorizontalConnectionDistance", minHorizontalConnectionDistance);
		if (explicit.contains("maxConnectionHeight")) output.putFloat("maxConnectionHeight", maxConnectionHeight);
		if (explicit.contains("minSavedTicks")) output.putInt("minSavedTicks", minSavedTicks);
		if (tag.equals(WIND_TAG) && explicit.contains("postJumpTicks")) output.putInt("postJumpTicks", postJumpTicks);
		if (tag.equals(PEARL_TAG)) {
			if (explicit.contains("throwAngle")) output.putFloat("throwAngle", throwAngle);
			if (explicit.contains("maxThrowPower")) output.putFloat("maxThrowPower", maxThrowPower);
			if (explicit.contains("throwAccuracy")) output.putFloat("throwAccuracy", throwAccuracy);
		}
	}
	@Override public void resetRuntime() { cooldown = 0; }
	public void clear() {
		configured = false; explicit.clear(); fetchSource.clear(); preferredHand = PmbPreferredHand.OFF;
		enabled = true; doConsume = false; doTeleportDamage = false; cooldownTicks = 40;
		randomCooldownBias = 20; maxConnectionDistance = defaultDistance; maxConnectionHeight = defaultHeight;
		minHorizontalConnectionDistance = 0;
		minSavedTicks = 10; postJumpTicks = 2; throwAngle = 30; maxThrowPower = 1; throwAccuracy = 1; cooldown = 0;
	}
}
