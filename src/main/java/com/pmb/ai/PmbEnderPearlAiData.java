package com.pmb.ai;

import java.util.Optional;
import java.util.LinkedHashSet;
import java.util.Set;

import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

import static com.pmb.ai.PmbAiNbtReader.clamp;
import static com.pmb.ai.PmbAiNbtReader.getBooleanOr;
import static com.pmb.ai.PmbAiNbtReader.getFloatOr;
import static com.pmb.ai.PmbAiNbtReader.getIntOr;
import static com.pmb.ai.PmbAiNbtReader.hasAnyNumericField;

public class PmbEnderPearlAiData implements PmbSkillConfigData {
	public static final String TAG = "ender_pearl";

	private static final float DEFAULT_MIN_THROW_RANGE = 4.0F;
	private static final float DEFAULT_MAX_THROW_RANGE = 16.0F;
	private static final float DEFAULT_THROW_CHANCE = 0.35F;
	private static final int DEFAULT_THROW_COOLDOWN_TICKS = 40;
	private static final float DEFAULT_THROW_ACCURACY = 0.9F;
	private static final float DEFAULT_MAX_THROW_POWER = 1.0F;
	private static final float DEFAULT_THROW_ANGLE = 30.0F;
	private static final boolean DEFAULT_DO_CONSUME = false;
	private static final boolean DEFAULT_REQUIRE_EYE_SIGHT = true;
	private static final int MAX_COOLDOWN_TICKS = 72000;

	private boolean configured;
	private final Set<String> explicitFields = new LinkedHashSet<>();
	private PmbPreferredHand preferredHand = PmbPreferredHand.OFF;
	private final PmbActivationSources fetchSource = new PmbActivationSources();
	private boolean enabled;
	private float minThrowRange = DEFAULT_MIN_THROW_RANGE;
	private float maxThrowRange = DEFAULT_MAX_THROW_RANGE;
	private float throwChance = DEFAULT_THROW_CHANCE;
	private int throwCooldownTicks = DEFAULT_THROW_COOLDOWN_TICKS;
	private float throwAccuracy = DEFAULT_THROW_ACCURACY;
	private float maxThrowPower = DEFAULT_MAX_THROW_POWER;
	private float throwAngle = DEFAULT_THROW_ANGLE;
	private boolean doConsume = DEFAULT_DO_CONSUME;
	private boolean requireEyeSight = DEFAULT_REQUIRE_EYE_SIGHT;
	private int randomCooldownBias = PmbSkillTiming.DEFAULT_RANDOM_COOLDOWN_BIAS;
	private int throwCooldown;

	public boolean isConfigured() {
		return configured;
	}
	@Override public Set<String> explicitFields() { return new LinkedHashSet<>(explicitFields); }

	public boolean isEnabled() {
		return configured && enabled;
	}
	public PmbPreferredHand preferredHand() { return preferredHand; }
	public PmbActivationSources fetchSource() { return fetchSource; }

	public float minThrowRange() {
		return minThrowRange;
	}

	public float maxThrowRange() {
		return maxThrowRange;
	}

	public float throwChance() {
		return throwChance;
	}

	public int throwCooldownTicks() {
		return throwCooldownTicks;
	}

	public float throwAccuracy() {
		return throwAccuracy;
	}

	public float maxThrowPower() {
		return maxThrowPower;
	}

	public float throwAngle() {
		return throwAngle;
	}

	public boolean doConsume() {
		return doConsume;
	}

	public boolean requireEyeSight() {
		return requireEyeSight;
	}
	public int randomCooldownBias() { return randomCooldownBias; }

	public boolean canCheckThrow() {
		return throwCooldown <= 0;
	}

	public boolean canThrowAtCombatDistance(double distance) {
		return distance >= minThrowRange && distance <= maxThrowRange;
	}

	public void resetThrowCooldown(net.minecraft.util.RandomSource random) {
		throwCooldown = PmbSkillTiming.cooldown(random, throwCooldownTicks, randomCooldownBias);
	}

	public void tickCooldown() {
		if (throwCooldown > 0) {
			throwCooldown--;
		}
	}
	int cooldownRemaining() { return throwCooldown; }
	void restoreCooldown(int elapsed, int remaining) {
		throwCooldown = Math.max(0, Math.min(throwCooldownTicks + randomCooldownBias, Math.max(0, remaining)) - elapsed);
	}

	public void read(ValueInput aiInput) {
		Optional<ValueInput> input = aiInput.child(TAG);
		if (input.isEmpty()) {
			clear();
			return;
		}

		ValueInput pearlInput = input.get();
		configured = true;
		preferredHand = PmbPreferredHand.parse(pearlInput.getStringOr("preferredHand", ""), PmbPreferredHand.OFF);
		fetchSource.read(pearlInput);
		explicitFields.clear();
		if (pearlInput.contains("preferredHand")) explicitFields.add("preferredHand");
		if (fetchSource.isExplicit()) explicitFields.add("FetchSource");
		for (String key : new String[] {"enable", "minThrowRange", "maxThrowRange", "throwChance", "throwCooldownTicks",
				"throwAccuracy", "maxThrowPower", "throwAngle", "doConsume", "requireEyeSight", "randomCooldownBias"})
			if (PmbAiNbtReader.hasField(pearlInput, key)) explicitFields.add(key);
		if (!explicitFields.contains("maxThrowRange") && PmbAiNbtReader.hasField(pearlInput, "throwRange")) explicitFields.add("maxThrowRange");
		if (!explicitFields.contains("maxThrowPower") && PmbAiNbtReader.hasField(pearlInput, "throwPower")) explicitFields.add("maxThrowPower");
		enabled = getBooleanOr(pearlInput, "enable", false);
		minThrowRange = clamp(getFloatOr(pearlInput, "minThrowRange", DEFAULT_MIN_THROW_RANGE),
				0.0F, 256.0F);
		maxThrowRange = clamp(getFloatOr(pearlInput, DEFAULT_MAX_THROW_RANGE, "maxThrowRange", "throwRange"),
				minThrowRange, 256.0F);
		throwChance = clamp(getFloatOr(pearlInput, "throwChance", DEFAULT_THROW_CHANCE), 0.0F, 1.0F);
		throwCooldownTicks = clamp(getIntOr(pearlInput, "throwCooldownTicks", DEFAULT_THROW_COOLDOWN_TICKS),
				0, MAX_COOLDOWN_TICKS);
		throwAccuracy = clamp(getFloatOr(pearlInput, "throwAccuracy", DEFAULT_THROW_ACCURACY), 0.0F, 1.0F);
		maxThrowPower = clamp(getFloatOr(pearlInput, DEFAULT_MAX_THROW_POWER, "maxThrowPower", "throwPower"),
				0.1F, 10.0F);
		throwAngle = clamp(getFloatOr(pearlInput, "throwAngle", DEFAULT_THROW_ANGLE), 1.0F, 89.0F);
		doConsume = getBooleanOr(pearlInput, "doConsume", DEFAULT_DO_CONSUME);
		requireEyeSight = getBooleanOr(pearlInput, "requireEyeSight", DEFAULT_REQUIRE_EYE_SIGHT);
		randomCooldownBias = clamp(getIntOr(pearlInput, "randomCooldownBias",
				PmbSkillTiming.DEFAULT_RANDOM_COOLDOWN_BIAS), 0, PmbSkillTiming.MAX_RANDOM_COOLDOWN_BIAS);
		throwCooldown = 0;
	}

	public void write(ValueOutput output) {
		if (explicitFields.contains("preferredHand")) output.putString("preferredHand", preferredHand.value());
		if (explicitFields.contains("FetchSource")) fetchSource.write(output);
		if (explicitFields.contains("enable")) output.putBoolean("enable", enabled);
		if (explicitFields.contains("minThrowRange")) output.putFloat("minThrowRange", minThrowRange);
		if (explicitFields.contains("maxThrowRange")) output.putFloat("maxThrowRange", maxThrowRange);
		if (explicitFields.contains("throwChance")) output.putFloat("throwChance", throwChance);
		if (explicitFields.contains("throwCooldownTicks")) output.putInt("throwCooldownTicks", throwCooldownTicks);
		if (explicitFields.contains("throwAccuracy")) output.putFloat("throwAccuracy", throwAccuracy);
		if (explicitFields.contains("maxThrowPower")) output.putFloat("maxThrowPower", maxThrowPower);
		if (explicitFields.contains("throwAngle")) output.putFloat("throwAngle", throwAngle);
		if (explicitFields.contains("doConsume")) output.putBoolean("doConsume", doConsume);
		if (explicitFields.contains("requireEyeSight")) output.putBoolean("requireEyeSight", requireEyeSight);
		if (explicitFields.contains("randomCooldownBias")) output.putInt("randomCooldownBias", randomCooldownBias);
	}

	@Override public void resetRuntime() { throwCooldown = 0; }

	public void clear() {
		preferredHand = PmbPreferredHand.OFF;
		fetchSource.clear();
		configured = false;
		explicitFields.clear();
		enabled = false;
		minThrowRange = DEFAULT_MIN_THROW_RANGE;
		maxThrowRange = DEFAULT_MAX_THROW_RANGE;
		throwChance = DEFAULT_THROW_CHANCE;
		throwCooldownTicks = DEFAULT_THROW_COOLDOWN_TICKS;
		throwAccuracy = DEFAULT_THROW_ACCURACY;
		maxThrowPower = DEFAULT_MAX_THROW_POWER;
		throwAngle = DEFAULT_THROW_ANGLE;
		doConsume = DEFAULT_DO_CONSUME;
		requireEyeSight = DEFAULT_REQUIRE_EYE_SIGHT;
		randomCooldownBias = PmbSkillTiming.DEFAULT_RANDOM_COOLDOWN_BIAS;
		throwCooldown = 0;
	}

}
