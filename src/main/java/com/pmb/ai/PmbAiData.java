package com.pmb.ai;

import java.util.Optional;

import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

public class PmbAiData {
	public static final String TAG = "PmbAi";

	private final PmbShieldAiData shield = new PmbShieldAiData();

	public PmbShieldAiData shield() {
		return shield;
	}

	public boolean isConfigured() {
		return shield.isConfigured();
	}

	public void read(ValueInput input) {
		Optional<ValueInput> aiInput = input.child(TAG);
		if (aiInput.isEmpty()) {
			clear();
			return;
		}

		shield.read(aiInput.get());
	}

	public void write(ValueOutput output) {
		if (!isConfigured()) {
			return;
		}

		ValueOutput ai = output.child(TAG);
		if (shield.isConfigured()) {
			shield.write(ai.child(PmbShieldAiData.TAG));
		}
	}

	private void clear() {
		shield.clear();
	}
}
