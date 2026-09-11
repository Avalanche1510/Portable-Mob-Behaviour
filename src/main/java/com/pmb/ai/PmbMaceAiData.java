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

public class PmbMaceAiData implements PmbSkillConfigData {
	public static final String TAG = "mace";

	private static final float DEFAULT_SMASH_RANGE = 3.0F;
	private static final float DEFAULT_HIT_CHANCE = 0.5F;
	private static final float DEFAULT_DAMAGE_REDUCTION = 0.5F;
	private static final int DEFAULT_SMASH_COOLDOWN_TICKS = 100;
	private static final int MAX_SMASH_COOLDOWN_TICKS = 72000;

	private boolean configured;
	private final Set<String> explicitFields = new LinkedHashSet<>();
	private final PmbActivationSources fetchSource = new PmbActivationSources();
	private boolean enabled;
	private float smashRange = DEFAULT_SMASH_RANGE;
	private float hitChance = DEFAULT_HIT_CHANCE;
	private float damageReduction = DEFAULT_DAMAGE_REDUCTION;
	private int smashCooldownTicks = DEFAULT_SMASH_COOLDOWN_TICKS;
	private int randomCooldownBias = PmbSkillTiming.DEFAULT_RANDOM_COOLDOWN_BIAS;
	private int smashCooldown;

	public boolean isConfigured() {
		return configured;
	}
	@Override public Set<String> explicitFields() { return new LinkedHashSet<>(explicitFields); }

	public boolean isEnabled() {
		return configured && enabled;
	}
	public PmbActivationSources fetchSource() { return fetchSource; }

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
	public int randomCooldownBias() { return randomCooldownBias; }

	public boolean canCheckSmash() {
		return smashCooldown <= 0;
	}

	public void resetSmashCooldown(net.minecraft.util.RandomSource random) {
		smashCooldown = PmbSkillTiming.cooldown(random, smashCooldownTicks, randomCooldownBias);
	}

	public void tickCooldown() {
		if (smashCooldown > 0) {
			smashCooldown--;
		}
	}
	int cooldownRemaining() { return smashCooldown; }
	void restoreCooldown(int elapsed, int remaining) {
		smashCooldown = Math.max(0, Math.min(smashCooldownTicks + randomCooldownBias, Math.max(0, remaining)) - elapsed);
	}

	public void read(ValueInput aiInput) {
		Optional<ValueInput> input = aiInput.child(TAG);
		if (input.isEmpty()) {
			clear();
			return;
		}

		ValueInput maceInput = input.get();
		configured = true;
		fetchSource.read(maceInput);
		explicitFields.clear();
		if (fetchSource.isExplicit()) explicitFields.add("FetchSource");
		for (String key : new String[] {"enable", "smashRange", "hitChance", "damageReduction", "smashCooldownTicks", "randomCooldownBias"})
			if (PmbAiNbtReader.hasField(maceInput, key)) explicitFields.add(key);
		enabled = getBooleanOr(maceInput, "enable", false);
		smashRange = clamp(getFloatOr(maceInput, "smashRange", DEFAULT_SMASH_RANGE), 0.0F, 64.0F);
		hitChance = clamp(getFloatOr(maceInput, "hitChance", DEFAULT_HIT_CHANCE), 0.0F, 1.0F);
		damageReduction = clamp(getFloatOr(maceInput, "damageReduction", DEFAULT_DAMAGE_REDUCTION), 0.0F, 1.0F);
		smashCooldownTicks = clamp(getIntOr(maceInput, "smashCooldownTicks", DEFAULT_SMASH_COOLDOWN_TICKS), 0,
				MAX_SMASH_COOLDOWN_TICKS);
		randomCooldownBias = clamp(getIntOr(maceInput, "randomCooldownBias",
				PmbSkillTiming.DEFAULT_RANDOM_COOLDOWN_BIAS), 0, PmbSkillTiming.MAX_RANDOM_COOLDOWN_BIAS);
		smashCooldown = 0;
	}

	public void write(ValueOutput output) {
		if (explicitFields.contains("FetchSource")) fetchSource.write(output);
		if (explicitFields.contains("enable")) output.putBoolean("enable", enabled);
		if (explicitFields.contains("smashRange")) output.putFloat("smashRange", smashRange);
		if (explicitFields.contains("hitChance")) output.putFloat("hitChance", hitChance);
		if (explicitFields.contains("damageReduction")) output.putFloat("damageReduction", damageReduction);
		if (explicitFields.contains("smashCooldownTicks")) output.putInt("smashCooldownTicks", smashCooldownTicks);
		if (explicitFields.contains("randomCooldownBias")) output.putInt("randomCooldownBias", randomCooldownBias);
	}

	@Override public void resetRuntime() { smashCooldown = 0; }

	public void clear() {
		fetchSource.clear();
		configured = false;
		explicitFields.clear();
		enabled = false;
		smashRange = DEFAULT_SMASH_RANGE;
		hitChance = DEFAULT_HIT_CHANCE;
		damageReduction = DEFAULT_DAMAGE_REDUCTION;
		smashCooldownTicks = DEFAULT_SMASH_COOLDOWN_TICKS;
		randomCooldownBias = PmbSkillTiming.DEFAULT_RANDOM_COOLDOWN_BIAS;
		smashCooldown = 0;
	}

}
