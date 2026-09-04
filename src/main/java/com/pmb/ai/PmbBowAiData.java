package com.pmb.ai;

import java.util.Optional;

import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

import static com.pmb.ai.PmbAiNbtReader.clamp;
import static com.pmb.ai.PmbAiNbtReader.getBooleanOr;
import static com.pmb.ai.PmbAiNbtReader.getFloatOr;
import static com.pmb.ai.PmbAiNbtReader.getIntOr;
import static com.pmb.ai.PmbAiNbtReader.hasAnyScalarField;

public class PmbBowAiData {
	public static final String TAG = "bow";
	public static final String MODE_LINE = "line";
	public static final String MODE_ARC = "arc";

	private static final boolean DEFAULT_DO_CONSUME = false;
	private static final String DEFAULT_MODE_PRIORITY = MODE_LINE;
	private static final boolean DEFAULT_MOBILE_WHILE_SHOOTING = true;
	private static final float DEFAULT_LINE_RANGE = 16.0F;
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
	private boolean enabled;
	private boolean doConsume = DEFAULT_DO_CONSUME;
	private String modePriority = DEFAULT_MODE_PRIORITY;
	private boolean mobileWhileShooting = DEFAULT_MOBILE_WHILE_SHOOTING;
	private float lineRange = DEFAULT_LINE_RANGE;
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
	private int lineCooldown;
	private int arcCooldown;

	public boolean isConfigured() { return configured; }
	public boolean isEnabled() { return configured && enabled; }
	public boolean doConsume() { return doConsume; }
	public String modePriority() { return modePriority; }
	public boolean mobileWhileShooting() { return mobileWhileShooting; }
	public float lineRange() { return lineRange; }
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
	public boolean canCheckLine() { return lineCooldown <= 0; }
	public boolean canCheckArc() { return arcCooldown <= 0; }
	public boolean controlsLineMovement(double distance) {
		return lineShootChance > 0.0F && distance <= lineRange;
	}
	public boolean controlsArcMovement(double distance) {
		return arcShootChance > 0.0F && distance <= arcMaxRange
				&& (distance >= arcMinRange || distance < arcSafeDistance);
	}

	public void resetLineCooldown() { lineCooldown = lineCooldownTicks; }
	public void resetArcCooldown() { arcCooldown = arcCooldownTicks; }

	public void tickCooldowns() {
		if (lineCooldown > 0) lineCooldown--;
		if (arcCooldown > 0) arcCooldown--;
	}

	public void read(ValueInput aiInput) {
		Optional<ValueInput> input = aiInput.child(TAG);
		if (input.isEmpty() || !hasAnyScalarField(input.get(), "enable", "doConsume", "modePriority", "mobileWhileShooting", "lineRange", "lineSafeDistance",
				"lineCooldownTicks", "lineShootChance", "lineShootAccuracy", "lineChargeTicks", "linePower",
				"arcMinRange", "arcMaxRange", "arcSafeDistance", "arcCooldownTicks", "arcShootChance", "arcShootAccuracy",
				"arcChargeTicks", "arcAngle", "arcMaxPower")) {
			clear();
			return;
		}

		ValueInput bowInput = input.get();
		configured = true;
		enabled = getBooleanOr(bowInput, "enable", false);
		doConsume = getBooleanOr(bowInput, "doConsume", DEFAULT_DO_CONSUME);
		modePriority = normalizeMode(bowInput.getStringOr("modePriority", DEFAULT_MODE_PRIORITY));
		mobileWhileShooting = getBooleanOr(bowInput, "mobileWhileShooting", DEFAULT_MOBILE_WHILE_SHOOTING);
		lineRange = clamp(getFloatOr(bowInput, "lineRange", DEFAULT_LINE_RANGE), 0.0F, 256.0F);
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
		lineCooldown = 0;
		arcCooldown = 0;
	}

	public void write(ValueOutput output) {
		output.putBoolean("enable", enabled);
		output.putBoolean("doConsume", doConsume);
		output.putString("modePriority", modePriority);
		output.putBoolean("mobileWhileShooting", mobileWhileShooting);
		output.putFloat("lineRange", lineRange);
		output.putFloat("lineSafeDistance", lineSafeDistance);
		output.putInt("lineCooldownTicks", lineCooldownTicks);
		output.putFloat("lineShootChance", lineShootChance);
		output.putFloat("lineShootAccuracy", lineShootAccuracy);
		output.putInt("lineChargeTicks", lineChargeTicks);
		output.putFloat("linePower", linePower);
		output.putFloat("arcMinRange", arcMinRange);
		output.putFloat("arcMaxRange", arcMaxRange);
		output.putFloat("arcSafeDistance", arcSafeDistance);
		output.putInt("arcCooldownTicks", arcCooldownTicks);
		output.putFloat("arcShootChance", arcShootChance);
		output.putFloat("arcShootAccuracy", arcShootAccuracy);
		output.putInt("arcChargeTicks", arcChargeTicks);
		output.putFloat("arcAngle", arcAngle);
		output.putFloat("arcMaxPower", arcMaxPower);
	}

	public void clear() {
		configured = false;
		enabled = false;
		doConsume = DEFAULT_DO_CONSUME;
		modePriority = DEFAULT_MODE_PRIORITY;
		mobileWhileShooting = DEFAULT_MOBILE_WHILE_SHOOTING;
		lineRange = DEFAULT_LINE_RANGE;
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
		lineCooldown = 0;
		arcCooldown = 0;
	}

	private static String normalizeMode(String value) {
		return MODE_ARC.equalsIgnoreCase(value) ? MODE_ARC : MODE_LINE;
	}

}
