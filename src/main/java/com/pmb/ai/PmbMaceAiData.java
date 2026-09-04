package com.pmb.ai;

import java.util.Optional;

import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

import static com.pmb.ai.PmbAiNbtReader.clamp;
import static com.pmb.ai.PmbAiNbtReader.getBooleanOr;
import static com.pmb.ai.PmbAiNbtReader.getFloatOr;
import static com.pmb.ai.PmbAiNbtReader.getIntOr;
import static com.pmb.ai.PmbAiNbtReader.hasAnyNumericField;

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
		if (input.isEmpty() || !hasAnyNumericField(input.get(), "enable", "smashRange", "hitChance",
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

}
