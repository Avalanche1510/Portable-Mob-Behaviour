package com.pmb.ai;

import java.util.Optional;

import com.mojang.serialization.Codec;
import net.minecraft.world.level.storage.ValueInput;

final class PmbAiNbtReader {
	private PmbAiNbtReader() {
	}

	static boolean hasAnyNumericField(ValueInput input, String... keys) {
		for (String key : keys) {
			if (hasNumericField(input, key)) {
				return true;
			}
		}
		return false;
	}

	static boolean hasAnyScalarField(ValueInput input, String... keys) {
		for (String key : keys) {
			if (hasNumericField(input, key) || input.getString(key).isPresent()) {
				return true;
			}
		}
		return false;
	}

	static boolean getBooleanOr(ValueInput input, String key, boolean defaultValue) {
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

	static int getIntOr(ValueInput input, String key, int defaultValue) {
		return input.getInt(key).orElse(defaultValue);
	}

	static int getIntOr(ValueInput input, int defaultValue, String... keys) {
		for (String key : keys) {
			Optional<Integer> value = input.getInt(key);
			if (value.isPresent()) {
				return value.get();
			}
		}
		return defaultValue;
	}

	static float getFloatOr(ValueInput input, String key, float defaultValue) {
		return getFloatOr(input, defaultValue, key);
	}

	static float getFloatOr(ValueInput input, float defaultValue, String... keys) {
		for (String key : keys) {
			Optional<Float> floatValue = input.read(key, Codec.FLOAT);
			if (floatValue.isPresent()) {
				return floatValue.get();
			}
			Optional<Double> doubleValue = input.read(key, Codec.DOUBLE);
			if (doubleValue.isPresent()) {
				return doubleValue.get().floatValue();
			}
			Optional<Integer> intValue = input.getInt(key);
			if (intValue.isPresent()) {
				return intValue.get();
			}
		}
		return defaultValue;
	}

	static int clamp(int value, int min, int max) {
		return Math.max(min, Math.min(max, value));
	}

	static float clamp(float value, float min, float max) {
		return Math.max(min, Math.min(max, value));
	}

	private static boolean hasNumericField(ValueInput input, String key) {
		return input.read(key, Codec.BOOL).isPresent()
				|| input.read(key, Codec.BYTE).isPresent()
				|| input.getInt(key).isPresent()
				|| input.read(key, Codec.FLOAT).isPresent()
				|| input.read(key, Codec.DOUBLE).isPresent();
	}
}
