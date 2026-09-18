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

public class PmbWindChargeAiData implements PmbSkillConfigData {
	public static final String TAG = "wind_charge";

	private static final float DEFAULT_THROW_RANGE = 16.0F;
	private static final float DEFAULT_THROW_CHANCE = 0.35F;
	private static final int DEFAULT_THROW_COOLDOWN_TICKS = 40;
	private static final float DEFAULT_THROW_ACCURACY = 0.9F;
	private static final float DEFAULT_BOUNCE_RANGE = 4.0F;
	private static final float DEFAULT_BOUNCE_CHANCE = 0.35F;
	private static final int DEFAULT_BOUNCE_COOLDOWN_TICKS = 40;
	private static final boolean DEFAULT_DO_CONSUME = false;
	private static final int MAX_COOLDOWN_TICKS = 72000;

	private boolean configured;
	private final Set<String> explicitFields = new LinkedHashSet<>();
	private PmbPreferredHand preferredHand = PmbPreferredHand.OFF;
	private final PmbActivationSources fetchSource = new PmbActivationSources();
	private boolean enabled;
	private float throwRange = DEFAULT_THROW_RANGE;
	private float throwChance = DEFAULT_THROW_CHANCE;
	private int throwCooldownTicks = DEFAULT_THROW_COOLDOWN_TICKS;
	private float throwAccuracy = DEFAULT_THROW_ACCURACY;
	private float bounceRange = DEFAULT_BOUNCE_RANGE;
	private float bounceChance = DEFAULT_BOUNCE_CHANCE;
	private int bounceCooldownTicks = DEFAULT_BOUNCE_COOLDOWN_TICKS;
	private boolean doConsume = DEFAULT_DO_CONSUME;
	private int randomCooldownBias = PmbSkillTiming.DEFAULT_RANDOM_COOLDOWN_BIAS;
	private int throwCooldown;
	private int bounceCooldown;

	public boolean isConfigured() {
		return configured;
	}
	@Override public Set<String> explicitFields() { return new LinkedHashSet<>(explicitFields); }

	public boolean isEnabled() {
		return configured && enabled;
	}
	public PmbPreferredHand preferredHand() { return preferredHand; }
	public PmbActivationSources fetchSource() { return fetchSource; }

	public float throwRange() {
		return throwRange;
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

	public float bounceRange() {
		return bounceRange;
	}

	public float bounceChance() {
		return bounceChance;
	}

	public int bounceCooldownTicks() {
		return bounceCooldownTicks;
	}

	public boolean doConsume() {
		return doConsume;
	}

	public int randomCooldownBias() { return randomCooldownBias; }

	public boolean canCheckThrow() {
		return throwCooldown <= 0;
	}

	public boolean canCheckBounce() {
		return bounceCooldown <= 0;
	}

	public void resetThrowCooldown(net.minecraft.util.RandomSource random) {
		throwCooldown = PmbSkillTiming.cooldown(random, throwCooldownTicks, randomCooldownBias);
	}

	public void resetBounceCooldown(net.minecraft.util.RandomSource random) {
		bounceCooldown = PmbSkillTiming.cooldown(random, bounceCooldownTicks, randomCooldownBias);
	}

	public void tickCooldowns() {
		if (throwCooldown > 0) {
			throwCooldown--;
		}
		if (bounceCooldown > 0) {
			bounceCooldown--;
		}
	}
	int throwCooldownRemaining() { return throwCooldown; }
	int bounceCooldownRemaining() { return bounceCooldown; }
	void restoreCooldowns(int elapsed, int throwRemaining, int bounceRemaining) {
		throwCooldown = Math.max(0, Math.min(throwCooldownTicks + randomCooldownBias, Math.max(0, throwRemaining)) - elapsed);
		bounceCooldown = Math.max(0, Math.min(bounceCooldownTicks + randomCooldownBias, Math.max(0, bounceRemaining)) - elapsed);
	}

	public void read(ValueInput aiInput) {
		Optional<ValueInput> input = aiInput.child(TAG);
		if (input.isEmpty()) {
			clear();
			return;
		}

		ValueInput windChargeInput = input.get();
		configured = true;
		preferredHand = PmbPreferredHand.parse(windChargeInput.getStringOr("preferredHand", ""), PmbPreferredHand.OFF);
		fetchSource.read(windChargeInput);
		explicitFields.clear();
		if (windChargeInput.contains("preferredHand")) explicitFields.add("preferredHand");
		if (fetchSource.isExplicit()) explicitFields.add("FetchSource");
		for (String key : new String[] {"enable", "throwRange", "throwChance", "throwCooldownTicks", "throwAccuracy",
				"bounceRange", "bounceChance", "bounceCooldownTicks", "doConsume", "randomCooldownBias"})
			if (PmbAiNbtReader.hasField(windChargeInput, key)) explicitFields.add(key);
		enabled = getBooleanOr(windChargeInput, "enable", false);
		throwRange = clamp(getFloatOr(windChargeInput, "throwRange", DEFAULT_THROW_RANGE), 0.0F, 64.0F);
		throwChance = clamp(getFloatOr(windChargeInput, "throwChance", DEFAULT_THROW_CHANCE), 0.0F, 1.0F);
		throwCooldownTicks = clamp(getIntOr(windChargeInput, "throwCooldownTicks", DEFAULT_THROW_COOLDOWN_TICKS),
				0, MAX_COOLDOWN_TICKS);
		throwAccuracy = clamp(getFloatOr(windChargeInput, "throwAccuracy", DEFAULT_THROW_ACCURACY), 0.0F, 1.0F);
		bounceRange = clamp(getFloatOr(windChargeInput, "bounceRange", DEFAULT_BOUNCE_RANGE), 0.0F, 64.0F);
		bounceChance = clamp(getFloatOr(windChargeInput, "bounceChance", DEFAULT_BOUNCE_CHANCE), 0.0F, 1.0F);
		bounceCooldownTicks = clamp(getIntOr(windChargeInput, "bounceCooldownTicks",
				DEFAULT_BOUNCE_COOLDOWN_TICKS), 0, MAX_COOLDOWN_TICKS);
		doConsume = getBooleanOr(windChargeInput, "doConsume", DEFAULT_DO_CONSUME);
		randomCooldownBias = clamp(getIntOr(windChargeInput, "randomCooldownBias",
				PmbSkillTiming.DEFAULT_RANDOM_COOLDOWN_BIAS), 0, PmbSkillTiming.MAX_RANDOM_COOLDOWN_BIAS);
		throwCooldown = 0;
		bounceCooldown = 0;
	}

	public void write(ValueOutput output) {
		if (explicitFields.contains("preferredHand")) output.putString("preferredHand", preferredHand.value());
		if (explicitFields.contains("FetchSource")) fetchSource.write(output);
		if (explicitFields.contains("enable")) output.putBoolean("enable", enabled);
		if (explicitFields.contains("throwRange")) output.putFloat("throwRange", throwRange);
		if (explicitFields.contains("throwChance")) output.putFloat("throwChance", throwChance);
		if (explicitFields.contains("throwCooldownTicks")) output.putInt("throwCooldownTicks", throwCooldownTicks);
		if (explicitFields.contains("throwAccuracy")) output.putFloat("throwAccuracy", throwAccuracy);
		if (explicitFields.contains("bounceRange")) output.putFloat("bounceRange", bounceRange);
		if (explicitFields.contains("bounceChance")) output.putFloat("bounceChance", bounceChance);
		if (explicitFields.contains("bounceCooldownTicks")) output.putInt("bounceCooldownTicks", bounceCooldownTicks);
		if (explicitFields.contains("doConsume")) output.putBoolean("doConsume", doConsume);
		if (explicitFields.contains("randomCooldownBias")) output.putInt("randomCooldownBias", randomCooldownBias);
	}

	@Override public void resetRuntime() { throwCooldown = 0; bounceCooldown = 0; }

	public void clear() {
		preferredHand = PmbPreferredHand.OFF;
		fetchSource.clear();
		configured = false;
		explicitFields.clear();
		enabled = false;
		throwRange = DEFAULT_THROW_RANGE;
		throwChance = DEFAULT_THROW_CHANCE;
		throwCooldownTicks = DEFAULT_THROW_COOLDOWN_TICKS;
		throwAccuracy = DEFAULT_THROW_ACCURACY;
		bounceRange = DEFAULT_BOUNCE_RANGE;
		bounceChance = DEFAULT_BOUNCE_CHANCE;
		bounceCooldownTicks = DEFAULT_BOUNCE_COOLDOWN_TICKS;
		doConsume = DEFAULT_DO_CONSUME;
		randomCooldownBias = PmbSkillTiming.DEFAULT_RANDOM_COOLDOWN_BIAS;
		throwCooldown = 0;
		bounceCooldown = 0;
	}

}
