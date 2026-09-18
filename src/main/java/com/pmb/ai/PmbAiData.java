package com.pmb.ai;

import com.pmb.PortableMobBehaviour;
import java.util.Optional;

import net.minecraft.core.HolderLookup;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.nbt.CompoundTag;

public class PmbAiData {
	public static final String TAG = "PmbAi";

	private final PmbShieldAiData shield = new PmbShieldAiData();
	private final PmbWindChargeAiData windCharge = new PmbWindChargeAiData();
	private final PmbMaceAiData mace = new PmbMaceAiData();
	private final PmbBowAiData bow = new PmbBowAiData();
	private final PmbEnderPearlAiData enderPearl = new PmbEnderPearlAiData();
	private final PmbAirTrackingAiData airTracking = new PmbAirTrackingAiData();
	private final PmbSkillPriorities skillPriorities = new PmbSkillPriorities();

	public PmbSkillPriorities skillPriorities() { return skillPriorities; }

	public PmbShieldAiData shield() {
		return shield;
	}

	public PmbWindChargeAiData windCharge() {
		return windCharge;
	}

	public PmbMaceAiData mace() {
		return mace;
	}

	public PmbBowAiData bow() {
		return bow;
	}

	public PmbEnderPearlAiData enderPearl() {
		return enderPearl;
	}
	public PmbAirTrackingAiData airTracking() { return airTracking; }

	public boolean isConfigured() {
		return shield.isConfigured() || windCharge.isConfigured() || mace.isConfigured() || bow.isConfigured()
				|| enderPearl.isConfigured() || airTracking.isConfigured();
	}
	public boolean hasPersistentData() { return isConfigured() || skillPriorities.hasOverrides(); }

	public PmbSkillConfigData skill(String id) {
		return switch (id) {
			case PmbShieldAiData.TAG -> shield;
			case PmbWindChargeAiData.TAG -> windCharge;
			case PmbMaceAiData.TAG -> mace;
			case PmbBowAiData.TAG -> bow;
			case PmbEnderPearlAiData.TAG -> enderPearl;
			case PmbAirTrackingAiData.TAG -> airTracking;
			default -> throw new IllegalArgumentException("Unknown PMB skill: " + id);
		};
	}

	public CompoundTag snapshot(HolderLookup.Provider registries, long gameTime) {
		try (ProblemReporter.ScopedCollector reporter = new ProblemReporter.ScopedCollector(PortableMobBehaviour.LOGGER)) {
			TagValueOutput output = TagValueOutput.createWithContext(reporter, registries);
			write(output);
			writeRuntime(output, gameTime);
			return output.buildResult();
		}
	}

	public void replaceFromSnapshot(CompoundTag snapshot, HolderLookup.Provider registries, long gameTime) {
		try (ProblemReporter.ScopedCollector reporter = new ProblemReporter.ScopedCollector(PortableMobBehaviour.LOGGER)) {
			ValueInput input = TagValueInput.create(reporter, registries, snapshot);
			read(input);
			readRuntime(input, gameTime);
		}
	}

	public void replaceSkillFromSnapshot(String id, CompoundTag snapshot, HolderLookup.Provider registries) {
		try (ProblemReporter.ScopedCollector reporter = new ProblemReporter.ScopedCollector(PortableMobBehaviour.LOGGER)) {
			ValueInput root = TagValueInput.create(reporter, registries, snapshot);
			ValueInput ai = root.childOrEmpty(TAG);
			switch (id) {
				case PmbShieldAiData.TAG -> shield.read(ai);
				case PmbWindChargeAiData.TAG -> windCharge.read(ai);
				case PmbMaceAiData.TAG -> mace.read(ai);
				case PmbBowAiData.TAG -> bow.read(ai);
				case PmbEnderPearlAiData.TAG -> enderPearl.read(ai);
				case PmbAirTrackingAiData.TAG -> airTracking.read(ai);
				default -> throw new IllegalArgumentException("Unknown PMB skill: " + id);
			}
		}
	}

	public void read(ValueInput input) {
		Optional<ValueInput> aiInput = input.child(TAG);
		if (aiInput.isEmpty()) {
			clear();
			return;
		}

		shield.read(aiInput.get());
		windCharge.read(aiInput.get());
		mace.read(aiInput.get());
		bow.read(aiInput.get());
		enderPearl.read(aiInput.get());
		airTracking.read(aiInput.get());
		skillPriorities.read(aiInput.get());
	}

	public void write(ValueOutput output) {
		if (!hasPersistentData()) {
			return;
		}

		ValueOutput ai = output.child(TAG);
		skillPriorities.write(ai);
		if (shield.isConfigured()) {
			shield.write(ai.child(PmbShieldAiData.TAG));
		}
		if (windCharge.isConfigured()) {
			windCharge.write(ai.child(PmbWindChargeAiData.TAG));
		}
		if (mace.isConfigured()) {
			mace.write(ai.child(PmbMaceAiData.TAG));
		}
		if (bow.isConfigured()) {
			bow.write(ai.child(PmbBowAiData.TAG));
		}
		if (enderPearl.isConfigured()) {
			enderPearl.write(ai.child(PmbEnderPearlAiData.TAG));
		}
		if (airTracking.isConfigured()) airTracking.write(ai.child(PmbAirTrackingAiData.TAG));
	}

	public void writeRuntime(ValueOutput output, long gameTime) {
		if (!isConfigured()) return;
		ValueOutput runtime = output.child("PmbSkillRuntime");
		runtime.putLong("SavedAt", gameTime);
		runtime.putInt("Shield", shield.cooldownRemaining());
		runtime.putInt("ShieldDisabled", shield.disabledCooldownRemaining());
		runtime.putInt("WindThrow", windCharge.throwCooldownRemaining());
		runtime.putInt("WindBounce", windCharge.bounceCooldownRemaining());
		runtime.putInt("Mace", mace.cooldownRemaining());
		runtime.putInt("BowLine", bow.lineCooldownRemaining());
		runtime.putInt("BowArc", bow.arcCooldownRemaining());
		runtime.putInt("Pearl", enderPearl.cooldownRemaining());
	}

	public void readRuntime(ValueInput input, long gameTime) {
		ValueInput runtime = input.childOrEmpty("PmbSkillRuntime");
		long savedAt = runtime.getLongOr("SavedAt", gameTime);
		long safeSavedAt = Math.max(0L, Math.min(gameTime, savedAt));
		int elapsed = (int) Math.min(Integer.MAX_VALUE, gameTime - safeSavedAt);
		shield.restoreCooldowns(elapsed, runtime.getIntOr("Shield", 0), runtime.getIntOr("ShieldDisabled", 0));
		windCharge.restoreCooldowns(elapsed, runtime.getIntOr("WindThrow", 0), runtime.getIntOr("WindBounce", 0));
		mace.restoreCooldown(elapsed, runtime.getIntOr("Mace", 0));
		bow.restoreCooldowns(elapsed, runtime.getIntOr("BowLine", 0), runtime.getIntOr("BowArc", 0));
		enderPearl.restoreCooldown(elapsed, runtime.getIntOr("Pearl", 0));
	}

	public void copyTo(PmbAiData target, long gameTime, HolderLookup.Provider registries) {
		try (ProblemReporter.ScopedCollector reporter = new ProblemReporter.ScopedCollector(PortableMobBehaviour.LOGGER)) {
			TagValueOutput output = TagValueOutput.createWithContext(reporter, registries);
			write(output);
			writeRuntime(output, gameTime);
			ValueInput input = TagValueInput.create(reporter, registries, output.buildResult());
			target.read(input);
			target.readRuntime(input, gameTime);
		}
	}

	private void clear() {
		shield.clear();
		windCharge.clear();
		mace.clear();
		bow.clear();
		enderPearl.clear();
		airTracking.clear();
		skillPriorities.resetAll();
	}
}
