package com.pmb.ai;

import java.util.Optional;

import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

public class PmbAiData {
	public static final String TAG = "PmbAi";

	private final PmbShieldAiData shield = new PmbShieldAiData();
	private final PmbWindChargeAiData windCharge = new PmbWindChargeAiData();
	private final PmbMaceAiData mace = new PmbMaceAiData();

	public PmbShieldAiData shield() {
		return shield;
	}

	public PmbWindChargeAiData windCharge() {
		return windCharge;
	}

	public PmbMaceAiData mace() {
		return mace;
	}

	public boolean isConfigured() {
		return shield.isConfigured() || windCharge.isConfigured() || mace.isConfigured();
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
	}

	private void clear() {
		shield.clear();
		windCharge.clear();
		mace.clear();
	}
}
