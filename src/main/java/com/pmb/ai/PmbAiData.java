package com.pmb.ai;

import java.util.Optional;

import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

public class PmbAiData {
	public static final String TAG = "PmbAi";

	private final PmbShieldAiData shield = new PmbShieldAiData();
	private final PmbWindChargeAiData windCharge = new PmbWindChargeAiData();
	private final PmbMaceAiData mace = new PmbMaceAiData();
	private final PmbBowAiData bow = new PmbBowAiData();
	private final PmbEnderPearlAiData enderPearl = new PmbEnderPearlAiData();

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

	public boolean isConfigured() {
		return shield.isConfigured() || windCharge.isConfigured() || mace.isConfigured() || bow.isConfigured()
				|| enderPearl.isConfigured();
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
	}

	public void write(ValueOutput output) {
		if (!isConfigured()) {
			return;
		}

		ValueOutput ai = output.child(TAG);
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
	}

	private void clear() {
		shield.clear();
		windCharge.clear();
		mace.clear();
		bow.clear();
		enderPearl.clear();
	}
}
