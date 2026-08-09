package com.pmb.ai;

import java.util.Optional;

import com.mojang.serialization.Codec;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

public class PmbEnderPearlAiData {
	public static final String TAG = "ender_pearl";

	private static final float DEFAULT_MIN_THROW_RANGE = 4.0F;
	private static final float DEFAULT_MAX_THROW_RANGE = 16.0F;
	private static final float DEFAULT_THROW_CHANCE = 0.35F;
	private static final int DEFAULT_THROW_COOLDOWN_TICKS = 40;
	private static final float DEFAULT_THROW_ACCURACY = 0.9F;
	private static final float DEFAULT_MAX_THROW_POWER = 1.0F;
	private static final float DEFAULT_THROW_ANGLE = 30.0F;
	private static final boolean DEFAULT_DO_CONSUME = false;
	private static final int MAX_COOLDOWN_TICKS = 72000;

	private boolean configured;
	private boolean enabled;
	private float minThrowRange = DEFAULT_MIN_THROW_RANGE;
	private float maxThrowRange = DEFAULT_MAX_THROW_RANGE;
	private float throwChance = DEFAULT_THROW_CHANCE;
	private int throwCooldownTicks = DEFAULT_THROW_COOLDOWN_TICKS;
	private float throwAccuracy = DEFAULT_THROW_ACCURACY;
	private float maxThrowPower = DEFAULT_MAX_THROW_POWER;
	private float throwAngle = DEFAULT_THROW_ANGLE;
	private boolean doConsume = DEFAULT_DO_CONSUME;
	private int throwCooldown;

	public boolean isConfigured() {
		return configured;
	}

	public boolean isEnabled() {
		return configured && enabled;
	}

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

	public boolean canCheckThrow() {
		return throwCooldown <= 0;
	}

	public void resetThrowCooldown() {
		throwCooldown = throwCooldownTicks;
	}

	public void tickCooldown() {
		if (throwCooldown > 0) {
			throwCooldown--;
		}
	}

	public void read(ValueInput aiInput) {
		Optional<ValueInput> input = aiInput.child(TAG);
		if (input.isEmpty() || !hasAnyField(input.get(), "enable", "minThrowRange", "maxThrowRange",
				"throwRange", "throwChance",
				"throwCooldownTicks", "throwAccuracy", "maxThrowPower", "throwPower", "throwAngle",
				"doConsume")) {
			clear();
			return;
		}

		ValueInput pearlInput = input.get();
		configured = true;
		enabled = getBooleanOr(pearlInput, "enable", false);
		minThrowRange = clamp(getFloatOr(pearlInput, "minThrowRange", DEFAULT_MIN_THROW_RANGE),
				0.0F, 256.0F);
		float legacyOrDefaultMax = getFloatOr(pearlInput, "throwRange", DEFAULT_MAX_THROW_RANGE);
		maxThrowRange = clamp(getFloatOr(pearlInput, "maxThrowRange", legacyOrDefaultMax),
				minThrowRange, 256.0F);
		throwChance = clamp(getFloatOr(pearlInput, "throwChance", DEFAULT_THROW_CHANCE), 0.0F, 1.0F);
		throwCooldownTicks = clamp(getIntOr(pearlInput, "throwCooldownTicks", DEFAULT_THROW_COOLDOWN_TICKS),
				0, MAX_COOLDOWN_TICKS);
		throwAccuracy = clamp(getFloatOr(pearlInput, "throwAccuracy", DEFAULT_THROW_ACCURACY), 0.0F, 1.0F);
		float legacyOrDefaultPower = getFloatOr(pearlInput, "throwPower", DEFAULT_MAX_THROW_POWER);
		maxThrowPower = clamp(getFloatOr(pearlInput, "maxThrowPower", legacyOrDefaultPower), 0.1F, 10.0F);
		throwAngle = clamp(getFloatOr(pearlInput, "throwAngle", DEFAULT_THROW_ANGLE), 1.0F, 89.0F);
		doConsume = getBooleanOr(pearlInput, "doConsume", DEFAULT_DO_CONSUME);
		throwCooldown = 0;
	}

	public void write(ValueOutput output) {
		output.putBoolean("enable", enabled);
		output.putFloat("minThrowRange", minThrowRange);
		output.putFloat("maxThrowRange", maxThrowRange);
		output.putFloat("throwChance", throwChance);
		output.putInt("throwCooldownTicks", throwCooldownTicks);
		output.putFloat("throwAccuracy", throwAccuracy);
		output.putFloat("maxThrowPower", maxThrowPower);
		output.putFloat("throwAngle", throwAngle);
		output.putBoolean("doConsume", doConsume);
	}

	public void clear() {
		configured = false;
		enabled = false;
		minThrowRange = DEFAULT_MIN_THROW_RANGE;
		maxThrowRange = DEFAULT_MAX_THROW_RANGE;
		throwChance = DEFAULT_THROW_CHANCE;
		throwCooldownTicks = DEFAULT_THROW_COOLDOWN_TICKS;
		throwAccuracy = DEFAULT_THROW_ACCURACY;
		maxThrowPower = DEFAULT_MAX_THROW_POWER;
		throwAngle = DEFAULT_THROW_ANGLE;
		doConsume = DEFAULT_DO_CONSUME;
		throwCooldown = 0;
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
