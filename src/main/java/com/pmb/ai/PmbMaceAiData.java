package com.pmb.ai;

import java.util.Optional;

import com.mojang.serialization.Codec;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

public class PmbMaceAiData {
	public static final String TAG = "mace";

	private static final float DEFAULT_SMASH_RANGE = 3.0F;
	private static final float DEFAULT_HIT_CHANCE = 0.5F;
	private static final float DEFAULT_DAMAGE_REDUCTION = 0.5F;
	private static final int DEFAULT_SMASH_COOLDOWN_TICKS = 100;
	private static final int MAX_SMASH_COOLDOWN_TICKS = 72000;

	private boolean configured;
	private boolean enabled;
	private float smashRange = DEFAULT_SMASH_RANGE;
	private float hitChance = DEFAULT_HIT_CHANCE;
	private float damageReduction = DEFAULT_DAMAGE_REDUCTION;
	private int smashCooldownTicks = DEFAULT_SMASH_COOLDOWN_TICKS;
	private int smashCooldown;

	public boolean isConfigured() {
		return configured;
	}

	public boolean isEnabled() {
		return configured && enabled;
	}

	public float smashRange() {
		return smashRange;
	}

	public float hitChance() {
		return hitChance;
	}

	public float damageReduction() {
		return damageReduction;
	}

	public int smashCooldownTicks() {
		return smashCooldownTicks;
	}

	public boolean canCheckSmash() {
		return smashCooldown <= 0;
	}

	public void resetSmashCooldown() {
		smashCooldown = smashCooldownTicks;
	}

	public void tickCooldown() {
		if (smashCooldown > 0) {
			smashCooldown--;
		}
	}

	public void read(ValueInput aiInput) {
		Optional<ValueInput> input = aiInput.child(TAG);
		if (input.isEmpty() || !hasAnyField(input.get(), "enable", "smashRange", "hitChance",
				"damageReduction", "smashCooldownTicks")) {
			clear();
			return;
		}

		ValueInput maceInput = input.get();
		configured = true;
		enabled = getBooleanOr(maceInput, "enable", false);
		smashRange = clamp(getFloatOr(maceInput, "smashRange", DEFAULT_SMASH_RANGE), 0.0F, 64.0F);
		hitChance = clamp(getFloatOr(maceInput, "hitChance", DEFAULT_HIT_CHANCE), 0.0F, 1.0F);
		damageReduction = clamp(getFloatOr(maceInput, "damageReduction", DEFAULT_DAMAGE_REDUCTION), 0.0F, 1.0F);
		smashCooldownTicks = clamp(getIntOr(maceInput, "smashCooldownTicks", DEFAULT_SMASH_COOLDOWN_TICKS), 0,
				MAX_SMASH_COOLDOWN_TICKS);
		smashCooldown = 0;
	}

	public void write(ValueOutput output) {
		output.putBoolean("enable", enabled);
		output.putFloat("smashRange", smashRange);
		output.putFloat("hitChance", hitChance);
		output.putFloat("damageReduction", damageReduction);
		output.putInt("smashCooldownTicks", smashCooldownTicks);
	}

	public void clear() {
		configured = false;
		enabled = false;
		smashRange = DEFAULT_SMASH_RANGE;
		hitChance = DEFAULT_HIT_CHANCE;
		damageReduction = DEFAULT_DAMAGE_REDUCTION;
		smashCooldownTicks = DEFAULT_SMASH_COOLDOWN_TICKS;
		smashCooldown = 0;
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
