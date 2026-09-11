package com.pmb.ai;

import net.minecraft.world.InteractionHand;

/** Destination preference, independent of the ordered item fetch locations. */
public enum PmbPreferredHand {
	MAIN("main", InteractionHand.MAIN_HAND, false),
	OFF("off", InteractionHand.OFF_HAND, false),
	MAIN_ENFORCE("main-enforce", InteractionHand.MAIN_HAND, true),
	OFF_ENFORCE("off-enforce", InteractionHand.OFF_HAND, true);

	private final String value;
	private final InteractionHand hand;
	private final boolean enforced;
	PmbPreferredHand(String value, InteractionHand hand, boolean enforced) {
		this.value = value; this.hand = hand; this.enforced = enforced;
	}
	public String value() { return value; }
	public InteractionHand hand() { return hand; }
	public boolean enforced() { return enforced; }
	public static PmbPreferredHand parse(String value, PmbPreferredHand fallback) {
		for (PmbPreferredHand preference : values()) if (preference.value.equals(value)) return preference;
		return fallback;
	}
}
