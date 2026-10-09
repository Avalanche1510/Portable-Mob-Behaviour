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
	private final PmbTraversalGroupAiData traversal = new PmbTraversalGroupAiData();
	private final PmbFallRescueAiData fallRescue = new PmbFallRescueAiData();
	private final PmbPathfindingData pathfinding = new PmbPathfindingData();
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
	public PmbTraversalGroupAiData traversal() { return traversal; }
	public PmbTraversalAiData windTraversal() { return traversal.wind(); }
	public PmbTraversalAiData pearlTraversal() { return traversal.pearl(); }
	public PmbFallRescueAiData fallRescue() { return fallRescue; }
	public PmbPathfindingData pathfinding() { return pathfinding; }
	public PmbAirTrackingAiData airTracking() { return pathfinding.airTracking(); }

	public boolean isConfigured() {
		return shield.isConfigured() || windCharge.isConfigured() || mace.isConfigured() || bow.isConfigured()
				|| enderPearl.isConfigured() || traversal.isConfigured()
				|| fallRescue.isConfigured() || pathfinding.isConfigured();
	}
	public boolean hasPersistentData() { return isConfigured() || skillPriorities.hasOverrides(); }

	public PmbSkillConfigData skill(String id) {
		return switch (id) {
			case PmbShieldAiData.TAG -> shield;
			case PmbWindChargeAiData.TAG -> windCharge;
			case PmbMaceAiData.TAG -> mace;
			case PmbBowAiData.TAG -> bow;
			case PmbEnderPearlAiData.TAG -> enderPearl;
			case PmbTraversalGroupAiData.TAG -> traversal;
			case "traversal.wind_charge" -> traversal.wind();
			case "traversal.ender_pearl" -> traversal.pearl();
			case PmbFallRescueAiData.TAG -> fallRescue;
			case "fall_rescue.wind_charge" -> fallRescue.wind();
			case "fall_rescue.ender_pearl" -> fallRescue.pearl();
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
			ValueInput skill = ai.childOrEmpty("skill");
			ValueInput active = skill.childOrEmpty("active");
			ValueInput conditional = skill.childOrEmpty("conditional");
			switch (id) {
				case PmbShieldAiData.TAG -> shield.read(active);
				case PmbWindChargeAiData.TAG -> windCharge.read(active);
				case PmbMaceAiData.TAG -> mace.read(conditional);
				case PmbBowAiData.TAG -> bow.read(active);
				case PmbEnderPearlAiData.TAG -> enderPearl.read(active);
				case PmbTraversalGroupAiData.TAG -> traversal.read(conditional);
				case "traversal.wind_charge", "traversal.ender_pearl" -> {
					int other = id.endsWith("wind_charge") ? traversal.pearl().cooldownRemaining() : traversal.wind().cooldownRemaining();
					traversal.read(conditional);
					if (id.endsWith("wind_charge")) traversal.pearl().restoreCooldown(0, other);
					else traversal.wind().restoreCooldown(0, other);
				}
				case PmbFallRescueAiData.TAG -> fallRescue.read(conditional);
				case "fall_rescue.wind_charge", "fall_rescue.ender_pearl" -> {
					int other = id.endsWith("wind_charge") ? fallRescue.pearlCooldownRemaining() : fallRescue.windCooldownRemaining();
					fallRescue.read(conditional);
					if (id.endsWith("wind_charge")) fallRescue.pearl().restore(0, other);
					else fallRescue.wind().restore(0, other);
				}
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

		ValueInput skill = aiInput.get().childOrEmpty("skill");
		ValueInput active = skill.childOrEmpty("active");
		ValueInput conditional = skill.childOrEmpty("conditional");
		shield.read(active);
		windCharge.read(active);
		bow.read(active);
		enderPearl.read(active);
		mace.read(conditional);
		traversal.read(conditional);
		fallRescue.read(conditional);
		skillPriorities.read(skill);
		pathfinding.read(aiInput.get());
	}

	public void write(ValueOutput output) {
		if (!hasPersistentData()) {
			return;
		}

		ValueOutput ai = output.child(TAG);
		ValueOutput skill = ai.child("skill");
		ValueOutput active = skill.child("active");
		ValueOutput conditional = skill.child("conditional");
		skillPriorities.write(skill);
		if (shield.isConfigured()) {
			shield.write(active.child(PmbShieldAiData.TAG));
		}
		if (windCharge.isConfigured()) {
			windCharge.write(active.child(PmbWindChargeAiData.TAG));
		}
		if (mace.isConfigured()) {
			mace.write(conditional.child(PmbMaceAiData.TAG));
		}
		if (bow.isConfigured()) {
			bow.write(active.child(PmbBowAiData.TAG));
		}
		if (enderPearl.isConfigured()) {
			enderPearl.write(active.child(PmbEnderPearlAiData.TAG));
		}
		if (traversal.isConfigured()) traversal.write(conditional.child(PmbTraversalGroupAiData.TAG));
		if (fallRescue.isConfigured()) fallRescue.write(conditional.child(PmbFallRescueAiData.TAG));
		pathfinding.write(ai);
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
		runtime.putInt("WindTraversal", traversal.wind().cooldownRemaining());
		runtime.putInt("PearlTraversal", traversal.pearl().cooldownRemaining());
		runtime.putInt("FallRescueWind", fallRescue.windCooldownRemaining());
		runtime.putInt("FallRescuePearl", fallRescue.pearlCooldownRemaining());
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
		traversal.wind().restoreCooldown(elapsed, runtime.getIntOr("WindTraversal", 0));
		traversal.pearl().restoreCooldown(elapsed, runtime.getIntOr("PearlTraversal", 0));
		fallRescue.restoreCooldowns(elapsed, runtime.getIntOr("FallRescueWind", 0),
				runtime.getIntOr("FallRescuePearl", 0));
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
		traversal.clear();
		fallRescue.clear();
		pathfinding.clear();
		skillPriorities.resetAll();
	}
}
