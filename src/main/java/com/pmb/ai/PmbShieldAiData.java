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

public class PmbShieldAiData implements PmbSkillConfigData {
	public static final String TAG = "shield";

	private static final float DEFAULT_RANGE = 8.0F;
	private static final float DEFAULT_CHANCE = 0.35F;
	private static final int DEFAULT_MIN_USE_TICKS = 80;
	private static final int DEFAULT_MAX_USE_TICKS = 160;
	private static final int DEFAULT_COOLDOWN_TICKS = 40;
	private static final float DEFAULT_BLOCKING_ANGLE = 30.0F;
	private static final int DEFAULT_AXE_DISABLE_COOLDOWN_TICKS = 100;
	private static final int DEFAULT_SHIELD_TOUGHNESS = 1;
	private static final int DEFAULT_CRIT_TOUGHNESS_DAMAGE = 1;
	private static final int DEFAULT_DISABLE_VULNER_TICKS = 40;
	private static final float DEFAULT_VULNER_DAMAGE_MULTIPLIER = 2.0F;
	private static final float DEFAULT_DISABLE_KB_MULTIPLIER = 1.2F;
	private static final float DEFAULT_SPEED_REDUCTION = 0.5F;
	private static final int MAX_USE_TICKS = 72000;
	private static final int MAX_SHIELD_TOUGHNESS = 100;
	private static final int MAX_DISABLE_VULNER_TICKS = 72000;
	private static final float MAX_VULNER_DAMAGE_MULTIPLIER = 100.0F;
	private static final float MAX_DISABLE_KB_MULTIPLIER = 100.0F;

	private boolean configured;
	private final Set<String> explicitFields = new LinkedHashSet<>();
	private PmbPreferredHand preferredHand = PmbPreferredHand.OFF;
	private final PmbActivationSources fetchSource = new PmbActivationSources();
	private boolean enabled;
	private float range = DEFAULT_RANGE;
	private float chance = DEFAULT_CHANCE;
	private int minUseTicks = DEFAULT_MIN_USE_TICKS;
	private int maxUseTicks = DEFAULT_MAX_USE_TICKS;
	private int cooldownTicks = DEFAULT_COOLDOWN_TICKS;
	private int randomCooldownBias = PmbSkillTiming.DEFAULT_RANDOM_COOLDOWN_BIAS;
	private float blockingAngle = DEFAULT_BLOCKING_ANGLE;
	private int axeDisableCooldownTicks = DEFAULT_AXE_DISABLE_COOLDOWN_TICKS;
	private int shieldToughness = DEFAULT_SHIELD_TOUGHNESS;
	private int critToughnessDamage = DEFAULT_CRIT_TOUGHNESS_DAMAGE;
	private int disableVulnerTicks = DEFAULT_DISABLE_VULNER_TICKS;
	private float vulnerDamageMultiplier = DEFAULT_VULNER_DAMAGE_MULTIPLIER;
	private float disableKbMultiplier = DEFAULT_DISABLE_KB_MULTIPLIER;
	private float speedReduction = DEFAULT_SPEED_REDUCTION;
	private int useTicks;
	private int cooldown;
	private int disabledCooldown;
	private int remainingShieldToughness = DEFAULT_SHIELD_TOUGHNESS;
	private int vulnerableTicks;

	public boolean isConfigured() {
		return configured;
	}
	@Override public Set<String> explicitFields() { return new LinkedHashSet<>(explicitFields); }

	public boolean isEnabled() {
		return enabled;
	}
	public PmbPreferredHand preferredHand() { return preferredHand; }
	public PmbActivationSources fetchSource() { return fetchSource; }

	public boolean canUse() {
		return configured && enabled && disabledCooldown <= 0;
	}

	public float range() {
		return range;
	}

	public float chance() {
		return chance;
	}

	public int minUseTicks() {
		return minUseTicks;
	}

	public int maxUseTicks() {
		return maxUseTicks;
	}

	public int cooldownTicks() {
		return cooldownTicks;
	}
	public int randomCooldownBias() { return randomCooldownBias; }

	public float blockingAngle() {
		return blockingAngle;
	}

	public int axeDisableCooldownTicks() {
		return axeDisableCooldownTicks;
	}

	public int shieldToughness() {
		return shieldToughness;
	}

	public int critToughnessDamage() {
		return critToughnessDamage;
	}

	public int disableVulnerTicks() {
		return disableVulnerTicks;
	}

	public float vulnerDamageMultiplier() {
		return vulnerDamageMultiplier;
	}

	public float disableKbMultiplier() {
		return disableKbMultiplier;
	}

	public float speedReduction() {
		return speedReduction;
	}

	public int useTicks() {
		return useTicks;
	}

	public void setUseTicks(int useTicks) {
		this.useTicks = Math.max(0, useTicks);
	}

	public int cooldown() {
		return cooldown;
	}

	public void setCooldown(int cooldown) {
		this.cooldown = Math.max(0, cooldown);
		if (this.cooldown == 0) {
			resetShieldToughness();
		}
	}
	public void resetCooldown(net.minecraft.util.RandomSource random) {
		setCooldown(PmbSkillTiming.cooldown(random, cooldownTicks, randomCooldownBias));
	}

	public int disabledCooldown() {
		return disabledCooldown;
	}

	public void setDisabledCooldown(int disabledCooldown) {
		this.disabledCooldown = Math.max(0, disabledCooldown);
		if (this.disabledCooldown == 0) {
			resetShieldToughness();
		}
	}

	public void tickCooldowns() {
		if (cooldown > 0) {
			cooldown--;
			if (cooldown == 0) {
				resetShieldToughness();
			}
		}

		if (disabledCooldown > 0) {
			disabledCooldown--;
			if (disabledCooldown == 0) {
				resetShieldToughness();
			}
		}
	}
	int cooldownRemaining() { return cooldown; }
	int disabledCooldownRemaining() { return disabledCooldown; }
	void restoreCooldowns(int elapsed, int cooldownRemaining, int disabledRemaining) {
		cooldown = Math.max(0, Math.min(cooldownTicks + randomCooldownBias, Math.max(0, cooldownRemaining)) - elapsed);
		disabledCooldown = Math.max(0, Math.min(axeDisableCooldownTicks, Math.max(0, disabledRemaining)) - elapsed);
	}

	public int vulnerableTicks() {
		return vulnerableTicks;
	}

	public boolean isVulnerable() {
		return vulnerableTicks > 0;
	}

	public void startShieldVulnerability() {
		vulnerableTicks = disableVulnerTicks;
	}

	public void tickShieldVulnerability() {
		if (vulnerableTicks > 0) {
			vulnerableTicks--;
		}
	}

	public boolean consumeShieldToughness(int amount) {
		if (remainingShieldToughness > 0) {
			remainingShieldToughness = Math.max(0, remainingShieldToughness - Math.max(1, amount));
		}
		return remainingShieldToughness <= 0;
	}

	public void resetShieldToughness() {
		remainingShieldToughness = shieldToughness;
	}

	public void read(ValueInput aiInput) {
		Optional<ValueInput> shieldInput = aiInput.child(TAG);
		if (shieldInput.isPresent()) {
			readNested(shieldInput.get());
			return;
		}

		readLegacyFlat(aiInput);
	}

	public void write(ValueOutput output) {
		if (explicitFields.contains("preferredHand")) output.putString("preferredHand", preferredHand.value());
		if (explicitFields.contains("FetchSource")) fetchSource.write(output);
		if (explicitFields.contains("enable")) output.putBoolean("enable", enabled);
		if (explicitFields.contains("range")) output.putFloat("range", range);
		if (explicitFields.contains("shieldChance")) output.putFloat("shieldChance", chance);
		if (explicitFields.contains("minUseTicks")) output.putInt("minUseTicks", minUseTicks);
		if (explicitFields.contains("maxUseTicks")) output.putInt("maxUseTicks", maxUseTicks);
		if (explicitFields.contains("cooldownTicks")) output.putInt("cooldownTicks", cooldownTicks);
		if (explicitFields.contains("randomCooldownBias")) output.putInt("randomCooldownBias", randomCooldownBias);
		if (explicitFields.contains("blockingAngle")) output.putFloat("blockingAngle", blockingAngle);
		if (explicitFields.contains("axeDisableCooldownTicks")) output.putInt("axeDisableCooldownTicks", axeDisableCooldownTicks);
		if (explicitFields.contains("shieldToughness")) output.putInt("shieldToughness", shieldToughness);
		if (explicitFields.contains("critToughnessDamage")) output.putInt("critToughnessDamage", critToughnessDamage);
		if (explicitFields.contains("disableVulnerTicks")) output.putInt("disableVulnerTicks", disableVulnerTicks);
		if (explicitFields.contains("vulnerDamageMultiplier")) output.putFloat("vulnerDamageMultiplier", vulnerDamageMultiplier);
		if (explicitFields.contains("disableKBMultiplier")) output.putFloat("disableKBMultiplier", disableKbMultiplier);
		if (explicitFields.contains("speedReduction")) output.putFloat("speedReduction", speedReduction);
	}

	@Override public void resetRuntime() {
		useTicks = 0; cooldown = 0; disabledCooldown = 0; vulnerableTicks = 0; resetShieldToughness();
	}

	public void clear() {
		preferredHand = PmbPreferredHand.OFF;
		fetchSource.clear();
		configured = false;
		explicitFields.clear();
		enabled = false;
		range = DEFAULT_RANGE;
		chance = DEFAULT_CHANCE;
		minUseTicks = DEFAULT_MIN_USE_TICKS;
		maxUseTicks = DEFAULT_MAX_USE_TICKS;
		cooldownTicks = DEFAULT_COOLDOWN_TICKS;
		randomCooldownBias = PmbSkillTiming.DEFAULT_RANDOM_COOLDOWN_BIAS;
		blockingAngle = DEFAULT_BLOCKING_ANGLE;
		axeDisableCooldownTicks = DEFAULT_AXE_DISABLE_COOLDOWN_TICKS;
		shieldToughness = DEFAULT_SHIELD_TOUGHNESS;
		critToughnessDamage = DEFAULT_CRIT_TOUGHNESS_DAMAGE;
		disableVulnerTicks = DEFAULT_DISABLE_VULNER_TICKS;
		vulnerDamageMultiplier = DEFAULT_VULNER_DAMAGE_MULTIPLIER;
		disableKbMultiplier = DEFAULT_DISABLE_KB_MULTIPLIER;
		speedReduction = DEFAULT_SPEED_REDUCTION;
		useTicks = 0;
		cooldown = 0;
		disabledCooldown = 0;
		remainingShieldToughness = DEFAULT_SHIELD_TOUGHNESS;
		vulnerableTicks = 0;
	}

	private void readNested(ValueInput shieldInput) {
		configured = true;
		preferredHand = PmbPreferredHand.parse(shieldInput.getStringOr("preferredHand", ""), PmbPreferredHand.OFF);
		fetchSource.read(shieldInput);
		explicitFields.clear();
		if (shieldInput.contains("preferredHand")) explicitFields.add("preferredHand");
		if (fetchSource.isExplicit()) explicitFields.add("FetchSource");
		mark(shieldInput, "enable", "enable");
		mark(shieldInput, "range", "range", "shieldRange");
		mark(shieldInput, "shieldChance", "shieldChance", "chance");
		mark(shieldInput, "minUseTicks", "minUseTicks", "shieldMinUseTicks");
		mark(shieldInput, "maxUseTicks", "maxUseTicks", "shieldMaxUseTicks");
		mark(shieldInput, "cooldownTicks", "cooldownTicks", "shieldCooldownTicks");
		mark(shieldInput, "randomCooldownBias", "randomCooldownBias");
		for (String key : new String[] {"blockingAngle", "axeDisableCooldownTicks", "shieldToughness",
				"critToughnessDamage", "disableVulnerTicks", "vulnerDamageMultiplier"}) mark(shieldInput, key, key);
		mark(shieldInput, "disableKBMultiplier", "disableKBMultiplier", "disableKbMultiplier");
		mark(shieldInput, "speedReduction", "speedReduction", "shieldSpeedReduction", "movementSpeedReduction", "speedReductionPercent");
		enabled = getBooleanOr(shieldInput, "enable", false);
		range = clamp(getFloatOr(shieldInput, DEFAULT_RANGE, "range", "shieldRange"), 0.0F, 64.0F);
		chance = clamp(getFloatOr(shieldInput, DEFAULT_CHANCE, "shieldChance", "chance"), 0.0F, 1.0F);
		minUseTicks = clamp(getIntOr(shieldInput, DEFAULT_MIN_USE_TICKS, "minUseTicks", "shieldMinUseTicks"), 1,
				MAX_USE_TICKS);
		maxUseTicks = clamp(getIntOr(shieldInput, DEFAULT_MAX_USE_TICKS, "maxUseTicks", "shieldMaxUseTicks"),
				minUseTicks, MAX_USE_TICKS);
		cooldownTicks = clamp(getIntOr(shieldInput, DEFAULT_COOLDOWN_TICKS, "cooldownTicks", "shieldCooldownTicks"),
				0, 400);
		randomCooldownBias = clamp(getIntOr(shieldInput, "randomCooldownBias",
				PmbSkillTiming.DEFAULT_RANDOM_COOLDOWN_BIAS), 0, PmbSkillTiming.MAX_RANDOM_COOLDOWN_BIAS);
		blockingAngle = clamp(getFloatOr(shieldInput, DEFAULT_BLOCKING_ANGLE, "blockingAngle"), 1.0F, 180.0F);
		axeDisableCooldownTicks = clamp(getIntOr(shieldInput, DEFAULT_AXE_DISABLE_COOLDOWN_TICKS,
				"axeDisableCooldownTicks"), 0, 600);
		shieldToughness = clamp(getIntOr(shieldInput, DEFAULT_SHIELD_TOUGHNESS, "shieldToughness"), 1,
				MAX_SHIELD_TOUGHNESS);
		critToughnessDamage = clamp(getIntOr(shieldInput, DEFAULT_CRIT_TOUGHNESS_DAMAGE, "critToughnessDamage"), 1,
				MAX_SHIELD_TOUGHNESS);
		disableVulnerTicks = clamp(getIntOr(shieldInput, DEFAULT_DISABLE_VULNER_TICKS, "disableVulnerTicks"), 0,
				MAX_DISABLE_VULNER_TICKS);
		vulnerDamageMultiplier = clamp(getFloatOr(shieldInput, DEFAULT_VULNER_DAMAGE_MULTIPLIER,
				"vulnerDamageMultiplier"), 1.0F, MAX_VULNER_DAMAGE_MULTIPLIER);
		disableKbMultiplier = clamp(getFloatOr(shieldInput, DEFAULT_DISABLE_KB_MULTIPLIER,
				"disableKBMultiplier", "disableKbMultiplier"), 0.0F, MAX_DISABLE_KB_MULTIPLIER);
		speedReduction = readSpeedReduction(shieldInput);
		useTicks = 0;
		cooldown = 0;
		disabledCooldown = 0;
		vulnerableTicks = 0;
		resetShieldToughness();
	}

	private void readLegacyFlat(ValueInput aiInput) {
		if (!hasAnyNumericField(aiInput, "shield", "shieldRange", "shieldChance", "shieldMinUseTicks",
				"shieldMaxUseTicks", "shieldCooldownTicks", "shieldSpeedReduction", "speedReduction",
				"speedReductionPercent")) {
			clear();
			return;
		}

		configured = true;
		preferredHand = PmbPreferredHand.OFF;
		fetchSource.clear();
		explicitFields.clear();
		mark(aiInput, "enable", "shield");
		mark(aiInput, "range", "shieldRange");
		mark(aiInput, "shieldChance", "shieldChance");
		mark(aiInput, "minUseTicks", "shieldMinUseTicks");
		mark(aiInput, "maxUseTicks", "shieldMaxUseTicks");
		mark(aiInput, "cooldownTicks", "shieldCooldownTicks");
		mark(aiInput, "speedReduction", "shieldSpeedReduction", "speedReduction", "speedReductionPercent");
		enabled = getBooleanOr(aiInput, "shield", false);
		range = clamp(getFloatOr(aiInput, DEFAULT_RANGE, "shieldRange"), 0.0F, 64.0F);
		chance = clamp(getFloatOr(aiInput, DEFAULT_CHANCE, "shieldChance"), 0.0F, 1.0F);
		minUseTicks = clamp(getIntOr(aiInput, DEFAULT_MIN_USE_TICKS, "shieldMinUseTicks"), 1, MAX_USE_TICKS);
		maxUseTicks = clamp(getIntOr(aiInput, DEFAULT_MAX_USE_TICKS, "shieldMaxUseTicks"), minUseTicks, MAX_USE_TICKS);
		cooldownTicks = clamp(getIntOr(aiInput, DEFAULT_COOLDOWN_TICKS, "shieldCooldownTicks"), 0, 400);
		randomCooldownBias = PmbSkillTiming.DEFAULT_RANDOM_COOLDOWN_BIAS;
		blockingAngle = DEFAULT_BLOCKING_ANGLE;
		axeDisableCooldownTicks = DEFAULT_AXE_DISABLE_COOLDOWN_TICKS;
		shieldToughness = DEFAULT_SHIELD_TOUGHNESS;
		critToughnessDamage = DEFAULT_CRIT_TOUGHNESS_DAMAGE;
		disableVulnerTicks = DEFAULT_DISABLE_VULNER_TICKS;
		vulnerDamageMultiplier = DEFAULT_VULNER_DAMAGE_MULTIPLIER;
		disableKbMultiplier = DEFAULT_DISABLE_KB_MULTIPLIER;
		speedReduction = readSpeedReduction(aiInput);
		useTicks = 0;
		cooldown = 0;
		disabledCooldown = 0;
		vulnerableTicks = 0;
		resetShieldToughness();
	}

	private void mark(ValueInput input, String canonical, String... names) {
		for (String name : names) if (PmbAiNbtReader.hasField(input, name)) { explicitFields.add(canonical); return; }
	}

	private static float readSpeedReduction(ValueInput input) {
		float value = getFloatOr(input, DEFAULT_SPEED_REDUCTION, "speedReduction", "shieldSpeedReduction",
				"movementSpeedReduction", "speedReductionPercent");
		if (value > 1.0F) {
			value /= 100.0F;
		}

		return clamp(value, 0.0F, 1.0F);
	}

}
