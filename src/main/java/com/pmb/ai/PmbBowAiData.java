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
import static com.pmb.ai.PmbAiNbtReader.hasAnyScalarField;

public class PmbBowAiData implements PmbSkillConfigData {
	public static final String TAG = "bow";
	public static final String MODE_LINE = "line";
	public static final String MODE_ARC = "arc";

	private static final boolean DEFAULT_DO_CONSUME = false;
	private static final boolean DEFAULT_REQUIRE_EYE_SIGHT = true;
	private static final String DEFAULT_MODE_PRIORITY = MODE_LINE;
	private static final boolean DEFAULT_MOBILE_WHILE_SHOOTING = true;
	private static final float DEFAULT_LINE_MIN_RANGE = 0.0F;
	private static final float DEFAULT_LINE_MAX_RANGE = 16.0F;
	private static final float DEFAULT_LINE_SAFE_DISTANCE = 6.0F;
	private static final int DEFAULT_LINE_COOLDOWN_TICKS = 40;
	private static final float DEFAULT_LINE_SHOOT_CHANCE = 0.8F;
	private static final float DEFAULT_LINE_SHOOT_ACCURACY = 0.9F;
	private static final int DEFAULT_LINE_CHARGE_TICKS = 20;
	private static final float DEFAULT_LINE_POWER = 1.0F;
	private static final float DEFAULT_ARC_MIN_RANGE = 16.0F;
	private static final float DEFAULT_ARC_MAX_RANGE = 48.0F;
	private static final float DEFAULT_ARC_SAFE_DISTANCE = 12.0F;
	private static final int DEFAULT_ARC_COOLDOWN_TICKS = 60;
	private static final float DEFAULT_ARC_SHOOT_CHANCE = 0.80F;
	private static final float DEFAULT_ARC_SHOOT_ACCURACY = 0.9F;
	private static final int DEFAULT_ARC_CHARGE_TICKS = 20;
	private static final float DEFAULT_ARC_ANGLE = 42.0F;
	private static final float DEFAULT_ARC_MAX_POWER = 1.0F;
	private static final int MAX_TICKS = 72000;

	private boolean configured;
	private final Set<String> explicitFields = new LinkedHashSet<>();
	private PmbPreferredHand preferredHand = PmbPreferredHand.MAIN;
	private final PmbActivationSources fetchSource = new PmbActivationSources();
	private final PmbAmmoSources ammoSource = new PmbAmmoSources();
	private boolean enabled;
	private boolean doConsume = DEFAULT_DO_CONSUME;
	private boolean requireEyeSight = DEFAULT_REQUIRE_EYE_SIGHT;
	private String modePriority = DEFAULT_MODE_PRIORITY;
	private boolean mobileWhileShooting = DEFAULT_MOBILE_WHILE_SHOOTING;
	private float lineMinRange = DEFAULT_LINE_MIN_RANGE;
	private float lineMaxRange = DEFAULT_LINE_MAX_RANGE;
	private float lineSafeDistance = DEFAULT_LINE_SAFE_DISTANCE;
	private int lineCooldownTicks = DEFAULT_LINE_COOLDOWN_TICKS;
	private float lineShootChance = DEFAULT_LINE_SHOOT_CHANCE;
	private float lineShootAccuracy = DEFAULT_LINE_SHOOT_ACCURACY;
	private int lineChargeTicks = DEFAULT_LINE_CHARGE_TICKS;
	private float linePower = DEFAULT_LINE_POWER;
	private float arcMinRange = DEFAULT_ARC_MIN_RANGE;
	private float arcMaxRange = DEFAULT_ARC_MAX_RANGE;
	private float arcSafeDistance = DEFAULT_ARC_SAFE_DISTANCE;
	private int arcCooldownTicks = DEFAULT_ARC_COOLDOWN_TICKS;
	private float arcShootChance = DEFAULT_ARC_SHOOT_CHANCE;
	private float arcShootAccuracy = DEFAULT_ARC_SHOOT_ACCURACY;
	private int arcChargeTicks = DEFAULT_ARC_CHARGE_TICKS;
	private float arcAngle = DEFAULT_ARC_ANGLE;
	private float arcMaxPower = DEFAULT_ARC_MAX_POWER;
	private int randomCooldownBias = PmbSkillTiming.DEFAULT_RANDOM_COOLDOWN_BIAS;
	private int lineCooldown;
	private int arcCooldown;

	public boolean isConfigured() { return configured; }
	@Override public Set<String> explicitFields() { return new LinkedHashSet<>(explicitFields); }
	public boolean isEnabled() { return configured && enabled; }
	public PmbPreferredHand preferredHand() { return preferredHand; }
	public PmbActivationSources fetchSource() { return fetchSource; }
	public PmbAmmoSources ammoSource() { return ammoSource; }
	public boolean doConsume() { return doConsume; }
	public boolean requireEyeSight() { return requireEyeSight; }
	public String modePriority() { return modePriority; }
	public boolean mobileWhileShooting() { return mobileWhileShooting; }
	public float lineMinRange() { return lineMinRange; }
	public float lineMaxRange() { return lineMaxRange; }
	public float lineSafeDistance() { return lineSafeDistance; }
	public int lineCooldownTicks() { return lineCooldownTicks; }
	public float lineShootChance() { return lineShootChance; }
	public float lineShootAccuracy() { return lineShootAccuracy; }
	public int lineChargeTicks() { return lineChargeTicks; }
	public float linePower() { return linePower; }
	public float arcMinRange() { return arcMinRange; }
	public float arcMaxRange() { return arcMaxRange; }
	public float arcSafeDistance() { return arcSafeDistance; }
	public int arcCooldownTicks() { return arcCooldownTicks; }
	public float arcShootChance() { return arcShootChance; }
	public float arcShootAccuracy() { return arcShootAccuracy; }
	public int arcChargeTicks() { return arcChargeTicks; }
	public float arcAngle() { return arcAngle; }
	public float arcMaxPower() { return arcMaxPower; }
	public int randomCooldownBias() { return randomCooldownBias; }
	public boolean canCheckLine() { return lineCooldown <= 0; }
	public boolean canCheckArc() { return arcCooldown <= 0; }
	public boolean canShootAtDistance(double distance) {
		return lineShootChance > 0.0F && distance >= lineMinRange && distance <= lineMaxRange
				|| arcShootChance > 0.0F && distance >= arcMinRange && distance <= arcMaxRange;
	}

	public void resetLineCooldown(net.minecraft.util.RandomSource random) {
		lineCooldown = PmbSkillTiming.cooldown(random, lineCooldownTicks, randomCooldownBias);
	}
	public void resetArcCooldown(net.minecraft.util.RandomSource random) {
		arcCooldown = PmbSkillTiming.cooldown(random, arcCooldownTicks, randomCooldownBias);
	}

	public void tickCooldowns() {
		if (lineCooldown > 0) lineCooldown--;
		if (arcCooldown > 0) arcCooldown--;
	}
	int lineCooldownRemaining() { return lineCooldown; }
	int arcCooldownRemaining() { return arcCooldown; }
	void restoreCooldowns(int elapsed, int lineRemaining, int arcRemaining) {
		lineCooldown = Math.max(0, Math.min(lineCooldownTicks + randomCooldownBias, Math.max(0, lineRemaining)) - elapsed);
		arcCooldown = Math.max(0, Math.min(arcCooldownTicks + randomCooldownBias, Math.max(0, arcRemaining)) - elapsed);
	}

	public void read(ValueInput aiInput) {
		Optional<ValueInput> input = aiInput.child(TAG);
		if (input.isEmpty()) {
			clear();
			return;
		}

		ValueInput bowInput = input.get();
		configured = true;
		preferredHand = PmbPreferredHand.parse(bowInput.getStringOr("preferredHand", ""), PmbPreferredHand.MAIN);
		fetchSource.read(bowInput);
		ammoSource.read(bowInput);
		explicitFields.clear();
		if (bowInput.contains("preferredHand")) explicitFields.add("preferredHand");
		if (fetchSource.isExplicit()) explicitFields.add("FetchSource");
		if (ammoSource.isExplicit()) explicitFields.add("AmmoSource");
		for (String key : new String[] {"enable", "doConsume", "requireEyeSight", "modePriority", "mobileWhileShooting",
				"lineMinRange", "lineMaxRange", "lineSafeDistance", "lineCooldownTicks", "lineShootChance", "lineShootAccuracy",
				"lineChargeTicks", "linePower", "arcMinRange", "arcMaxRange", "arcSafeDistance", "arcCooldownTicks",
				"arcShootChance", "arcShootAccuracy", "arcChargeTicks", "arcAngle", "arcMaxPower", "randomCooldownBias"})
			if (PmbAiNbtReader.hasField(bowInput, key)) explicitFields.add(key);
		boolean hasCanonicalLineRange = PmbAiNbtReader.hasField(bowInput, "lineMinRange")
				|| PmbAiNbtReader.hasField(bowInput, "lineMaxRange");
		boolean hasTemporaryLineRange = !hasCanonicalLineRange
				&& (PmbAiNbtReader.hasField(bowInput, "minLineRange")
				|| PmbAiNbtReader.hasField(bowInput, "maxLineRange"));
		boolean hasLegacyLineRange = !hasCanonicalLineRange && !hasTemporaryLineRange
				&& PmbAiNbtReader.hasField(bowInput, "lineRange");
		if (hasTemporaryLineRange || hasLegacyLineRange) {
			explicitFields.add("lineMinRange");
			explicitFields.add("lineMaxRange");
		}
		enabled = getBooleanOr(bowInput, "enable", false);
		doConsume = getBooleanOr(bowInput, "doConsume", DEFAULT_DO_CONSUME);
		requireEyeSight = getBooleanOr(bowInput, "requireEyeSight", DEFAULT_REQUIRE_EYE_SIGHT);
		modePriority = normalizeMode(bowInput.getStringOr("modePriority", DEFAULT_MODE_PRIORITY));
		mobileWhileShooting = getBooleanOr(bowInput, "mobileWhileShooting", DEFAULT_MOBILE_WHILE_SHOOTING);
		if (hasCanonicalLineRange) {
			lineMinRange = clamp(getFloatOr(bowInput, "lineMinRange", DEFAULT_LINE_MIN_RANGE), 0.0F, 256.0F);
			lineMaxRange = clamp(getFloatOr(bowInput, "lineMaxRange", DEFAULT_LINE_MAX_RANGE), lineMinRange, 256.0F);
		} else if (hasTemporaryLineRange) {
			lineMinRange = clamp(getFloatOr(bowInput, "minLineRange", DEFAULT_LINE_MIN_RANGE), 0.0F, 256.0F);
			lineMaxRange = clamp(getFloatOr(bowInput, "maxLineRange", DEFAULT_LINE_MAX_RANGE), lineMinRange, 256.0F);
		} else {
			lineMaxRange = clamp(getFloatOr(bowInput, "lineRange", DEFAULT_LINE_MAX_RANGE), 0.0F, 256.0F);
			lineMinRange = DEFAULT_LINE_MIN_RANGE;
		}
		lineSafeDistance = clamp(getFloatOr(bowInput, "lineSafeDistance", DEFAULT_LINE_SAFE_DISTANCE), 0.0F, 256.0F);
		lineCooldownTicks = clamp(getIntOr(bowInput, "lineCooldownTicks", DEFAULT_LINE_COOLDOWN_TICKS), 0, MAX_TICKS);
		lineShootChance = clamp(getFloatOr(bowInput, "lineShootChance", DEFAULT_LINE_SHOOT_CHANCE), 0.0F, 1.0F);
		lineShootAccuracy = clamp(getFloatOr(bowInput, "lineShootAccuracy", DEFAULT_LINE_SHOOT_ACCURACY), 0.0F, 1.0F);
		lineChargeTicks = clamp(getIntOr(bowInput, "lineChargeTicks", DEFAULT_LINE_CHARGE_TICKS), 0, MAX_TICKS);
		linePower = clamp(getFloatOr(bowInput, "linePower", DEFAULT_LINE_POWER), 0.1F, 10.0F);
		arcMinRange = clamp(getFloatOr(bowInput, "arcMinRange", DEFAULT_ARC_MIN_RANGE), 0.0F, 256.0F);
		arcMaxRange = clamp(getFloatOr(bowInput, "arcMaxRange", DEFAULT_ARC_MAX_RANGE), arcMinRange, 256.0F);
		arcSafeDistance = clamp(getFloatOr(bowInput, "arcSafeDistance", DEFAULT_ARC_SAFE_DISTANCE), 0.0F, 256.0F);
		arcCooldownTicks = clamp(getIntOr(bowInput, "arcCooldownTicks", DEFAULT_ARC_COOLDOWN_TICKS), 0, MAX_TICKS);
		arcShootChance = clamp(getFloatOr(bowInput, "arcShootChance", DEFAULT_ARC_SHOOT_CHANCE), 0.0F, 1.0F);
		arcShootAccuracy = clamp(getFloatOr(bowInput, "arcShootAccuracy", DEFAULT_ARC_SHOOT_ACCURACY), 0.0F, 1.0F);
		arcChargeTicks = clamp(getIntOr(bowInput, "arcChargeTicks", DEFAULT_ARC_CHARGE_TICKS), 0, MAX_TICKS);
		arcAngle = clamp(getFloatOr(bowInput, "arcAngle", DEFAULT_ARC_ANGLE), 1.0F, 89.0F);
		arcMaxPower = clamp(getFloatOr(bowInput, "arcMaxPower", DEFAULT_ARC_MAX_POWER), 0.1F, 10.0F);
		randomCooldownBias = clamp(getIntOr(bowInput, "randomCooldownBias",
				PmbSkillTiming.DEFAULT_RANDOM_COOLDOWN_BIAS), 0, PmbSkillTiming.MAX_RANDOM_COOLDOWN_BIAS);
		lineCooldown = 0;
		arcCooldown = 0;
	}

	public void write(ValueOutput output) {
		if (explicitFields.contains("preferredHand")) output.putString("preferredHand", preferredHand.value());
		if (explicitFields.contains("FetchSource")) fetchSource.write(output);
		if (explicitFields.contains("AmmoSource")) ammoSource.write(output);
		if (explicitFields.contains("enable")) output.putBoolean("enable", enabled);
		if (explicitFields.contains("doConsume")) output.putBoolean("doConsume", doConsume);
		if (explicitFields.contains("requireEyeSight")) output.putBoolean("requireEyeSight", requireEyeSight);
		if (explicitFields.contains("modePriority")) output.putString("modePriority", modePriority);
		if (explicitFields.contains("mobileWhileShooting")) output.putBoolean("mobileWhileShooting", mobileWhileShooting);
		if (explicitFields.contains("lineMinRange")) output.putFloat("lineMinRange", lineMinRange);
		if (explicitFields.contains("lineMaxRange")) output.putFloat("lineMaxRange", lineMaxRange);
		if (explicitFields.contains("lineSafeDistance")) output.putFloat("lineSafeDistance", lineSafeDistance);
		if (explicitFields.contains("lineCooldownTicks")) output.putInt("lineCooldownTicks", lineCooldownTicks);
		if (explicitFields.contains("lineShootChance")) output.putFloat("lineShootChance", lineShootChance);
		if (explicitFields.contains("lineShootAccuracy")) output.putFloat("lineShootAccuracy", lineShootAccuracy);
		if (explicitFields.contains("lineChargeTicks")) output.putInt("lineChargeTicks", lineChargeTicks);
		if (explicitFields.contains("linePower")) output.putFloat("linePower", linePower);
		if (explicitFields.contains("arcMinRange")) output.putFloat("arcMinRange", arcMinRange);
		if (explicitFields.contains("arcMaxRange")) output.putFloat("arcMaxRange", arcMaxRange);
		if (explicitFields.contains("arcSafeDistance")) output.putFloat("arcSafeDistance", arcSafeDistance);
		if (explicitFields.contains("arcCooldownTicks")) output.putInt("arcCooldownTicks", arcCooldownTicks);
		if (explicitFields.contains("arcShootChance")) output.putFloat("arcShootChance", arcShootChance);
		if (explicitFields.contains("arcShootAccuracy")) output.putFloat("arcShootAccuracy", arcShootAccuracy);
		if (explicitFields.contains("arcChargeTicks")) output.putInt("arcChargeTicks", arcChargeTicks);
		if (explicitFields.contains("arcAngle")) output.putFloat("arcAngle", arcAngle);
		if (explicitFields.contains("arcMaxPower")) output.putFloat("arcMaxPower", arcMaxPower);
		if (explicitFields.contains("randomCooldownBias")) output.putInt("randomCooldownBias", randomCooldownBias);
	}

	@Override public void resetRuntime() { lineCooldown = 0; arcCooldown = 0; }

	public void clear() {
		preferredHand = PmbPreferredHand.MAIN;
		fetchSource.clear();
		ammoSource.clear();
		configured = false;
		explicitFields.clear();
		enabled = false;
		doConsume = DEFAULT_DO_CONSUME;
		requireEyeSight = DEFAULT_REQUIRE_EYE_SIGHT;
		modePriority = DEFAULT_MODE_PRIORITY;
		mobileWhileShooting = DEFAULT_MOBILE_WHILE_SHOOTING;
		lineMinRange = DEFAULT_LINE_MIN_RANGE;
		lineMaxRange = DEFAULT_LINE_MAX_RANGE;
		lineSafeDistance = DEFAULT_LINE_SAFE_DISTANCE;
		lineCooldownTicks = DEFAULT_LINE_COOLDOWN_TICKS;
		lineShootChance = DEFAULT_LINE_SHOOT_CHANCE;
		lineShootAccuracy = DEFAULT_LINE_SHOOT_ACCURACY;
		lineChargeTicks = DEFAULT_LINE_CHARGE_TICKS;
		linePower = DEFAULT_LINE_POWER;
		arcMinRange = DEFAULT_ARC_MIN_RANGE;
		arcMaxRange = DEFAULT_ARC_MAX_RANGE;
		arcSafeDistance = DEFAULT_ARC_SAFE_DISTANCE;
		arcCooldownTicks = DEFAULT_ARC_COOLDOWN_TICKS;
		arcShootChance = DEFAULT_ARC_SHOOT_CHANCE;
		arcShootAccuracy = DEFAULT_ARC_SHOOT_ACCURACY;
		arcChargeTicks = DEFAULT_ARC_CHARGE_TICKS;
		arcAngle = DEFAULT_ARC_ANGLE;
		arcMaxPower = DEFAULT_ARC_MAX_POWER;
		randomCooldownBias = PmbSkillTiming.DEFAULT_RANDOM_COOLDOWN_BIAS;
		lineCooldown = 0;
		arcCooldown = 0;
	}

	private static String normalizeMode(String value) {
		return MODE_ARC.equalsIgnoreCase(value) ? MODE_ARC : MODE_LINE;
	}

}
