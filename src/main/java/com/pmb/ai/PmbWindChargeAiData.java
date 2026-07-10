package com.pmb.ai;

import java.util.Optional;

import com.mojang.serialization.Codec;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

public class PmbWindChargeAiData {
	public static final String TAG = "wind_charge";

	private static final float DEFAULT_THROW_RANGE = 16.0F;
	private static final float DEFAULT_THROW_CHANCE = 0.35F;
	private static final int DEFAULT_THROW_COOLDOWN_TICKS = 40;
	private static final float DEFAULT_THROW_ACCURACY = 0.9F;
	private static final float DEFAULT_BOUNCE_RANGE = 4.0F;
	private static final float DEFAULT_BOUNCE_CHANCE = 0.35F;
	private static final int DEFAULT_BOUNCE_COOLDOWN_TICKS = 40;
	private static final boolean DEFAULT_DO_CONSUME = false;
	private static final float DEFAULT_IN_AIR_TRACK_STRENGTH = 0.012F;
	private static final int MAX_COOLDOWN_TICKS = 72000;

	private boolean configured;
	private boolean enabled;
	private float throwRange = DEFAULT_THROW_RANGE;
	private float throwChance = DEFAULT_THROW_CHANCE;
	private int throwCooldownTicks = DEFAULT_THROW_COOLDOWN_TICKS;
	private float throwAccuracy = DEFAULT_THROW_ACCURACY;
	private float bounceRange = DEFAULT_BOUNCE_RANGE;
	private float bounceChance = DEFAULT_BOUNCE_CHANCE;
	private int bounceCooldownTicks = DEFAULT_BOUNCE_COOLDOWN_TICKS;
	private boolean doConsume = DEFAULT_DO_CONSUME;
	private float inAirTrackStrength = DEFAULT_IN_AIR_TRACK_STRENGTH;
	private int throwCooldown;
	private int bounceCooldown;

	public boolean isConfigured() {
		return configured;
	}

	public boolean isEnabled() {
		return configured && enabled;
	}

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

	public float inAirTrackStrength() {
		return inAirTrackStrength;
	}

	public boolean canCheckThrow() {
		return throwCooldown <= 0;
	}

	public boolean canCheckBounce() {
		return bounceCooldown <= 0;
	}

	public void resetThrowCooldown() {
		throwCooldown = throwCooldownTicks;
	}

	public void resetBounceCooldown() {
		bounceCooldown = bounceCooldownTicks;
	}

	public void tickCooldowns() {
		if (throwCooldown > 0) {
			throwCooldown--;
		}
		if (bounceCooldown > 0) {
			bounceCooldown--;
		}
	}

	public void read(ValueInput aiInput) {
		Optional<ValueInput> input = aiInput.child(TAG);
		if (input.isEmpty() || !hasAnyField(input.get(), "enable", "throwRange", "throwChance",
				"throwCooldownTicks", "throwAccuracy", "bounceRange", "bounceChance", "bounceCooldownTicks",
				"doConsume", "inAirTrackStrength")) {
			clear();
			return;
		}

		ValueInput windChargeInput = input.get();
		configured = true;
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
		inAirTrackStrength = clamp(getFloatOr(windChargeInput, "inAirTrackStrength",
				DEFAULT_IN_AIR_TRACK_STRENGTH), 0.0F, 1.0F);
		throwCooldown = 0;
		bounceCooldown = 0;
	}

	public void write(ValueOutput output) {
		output.putBoolean("enable", enabled);
		output.putFloat("throwRange", throwRange);
		output.putFloat("throwChance", throwChance);
		output.putInt("throwCooldownTicks", throwCooldownTicks);
		output.putFloat("throwAccuracy", throwAccuracy);
		output.putFloat("bounceRange", bounceRange);
		output.putFloat("bounceChance", bounceChance);
		output.putInt("bounceCooldownTicks", bounceCooldownTicks);
		output.putBoolean("doConsume", doConsume);
		output.putFloat("inAirTrackStrength", inAirTrackStrength);
	}

	public void clear() {
		configured = false;
		enabled = false;
		throwRange = DEFAULT_THROW_RANGE;
		throwChance = DEFAULT_THROW_CHANCE;
		throwCooldownTicks = DEFAULT_THROW_COOLDOWN_TICKS;
		throwAccuracy = DEFAULT_THROW_ACCURACY;
		bounceRange = DEFAULT_BOUNCE_RANGE;
		bounceChance = DEFAULT_BOUNCE_CHANCE;
		bounceCooldownTicks = DEFAULT_BOUNCE_COOLDOWN_TICKS;
		doConsume = DEFAULT_DO_CONSUME;
		inAirTrackStrength = DEFAULT_IN_AIR_TRACK_STRENGTH;
		throwCooldown = 0;
		bounceCooldown = 0;
	}

	private static boolean hasAnyField(ValueInput input, String... keys) {
		for (String key : keys) {
			if (input.read(key, Codec.BOOL).isPresent() || input.read(key, Codec.BYTE).isPresent()
					|| input.getInt(key).isPresent() || input.read(key, Codec.FLOAT).isPresent()
					|| input.read(key, Codec.DOUBLE).isPresent()) {
				return true;
			}
		}
		return false;
	}

	private static boolean getBooleanOr(ValueInput input, String key, boolean defaultValue) {
		Optional<Boolean> booleanValue = input.read(key, Codec.BOOL);
		if (booleanValue.isPresent()) {
			return booleanValue.get();
		}
		Optional<Byte> byteValue = input.read(key, Codec.BYTE);
		if (byteValue.isPresent()) {
			return byteValue.get() != 0;
		}
		Optional<Integer> intValue = input.getInt(key);
		return intValue.isPresent() ? intValue.get() != 0 : input.getBooleanOr(key, defaultValue);
	}

	private static int getIntOr(ValueInput input, String key, int defaultValue) {
		return input.getInt(key).orElse(defaultValue);
	}

	private static float getFloatOr(ValueInput input, String key, float defaultValue) {
		Optional<Float> floatValue = input.read(key, Codec.FLOAT);
		if (floatValue.isPresent()) {
			return floatValue.get();
		}
		Optional<Double> doubleValue = input.read(key, Codec.DOUBLE);
		if (doubleValue.isPresent()) {
			return doubleValue.get().floatValue();
		}
		Optional<Integer> intValue = input.getInt(key);
		return intValue.isPresent() ? intValue.get() : defaultValue;
	}

	private static int clamp(int value, int min, int max) {
		return Math.max(min, Math.min(max, value));
	}

	private static float clamp(float value, float min, float max) {
		return Math.max(min, Math.min(max, value));
	}
}
