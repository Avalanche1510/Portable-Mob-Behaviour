package com.pmb.ai;

/** Narrow projectile state used to opt Mob-thrown pearls into vanilla-style teleport damage. */
public interface PmbEnderPearlDamageHolder {
	void pmb$setTeleportDamage(boolean enabled);
	boolean pmb$doesTeleportDamage();
}
