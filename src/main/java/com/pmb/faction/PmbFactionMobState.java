package com.pmb.faction;

import net.minecraft.world.entity.LivingEntity;

public interface PmbFactionMobState {
	LivingEntity pmb$getFactionCombatTarget();

	void pmb$setFactionCombatTarget(LivingEntity target);

	LivingEntity pmb$getFactionAvoidTarget();

	void pmb$setFactionAvoidTarget(LivingEntity target);

	int pmb$getFactionHurtTimestamp();

	void pmb$setFactionHurtTimestamp(int timestamp);

	boolean pmb$wasFactionAvoiding();

	void pmb$setFactionAvoiding(boolean avoiding);

	boolean pmb$isFactionGroupRevenge();

	void pmb$setFactionGroupRevenge(boolean groupRevenge);

	boolean pmb$wasFactionAdmiring();
	void pmb$setFactionAdmiring(boolean admiring);

	LivingEntity pmb$getLastBrainCombatTarget();
	void pmb$setLastBrainCombatTarget(LivingEntity target);
	LivingEntity pmb$getSuppressedTarget();
	void pmb$setSuppressedTarget(LivingEntity target);
	long pmb$getSuppressedUntil();
	void pmb$setSuppressedUntil(long tick);
	net.minecraft.world.phys.Vec3 pmb$getSuppressedPosition();
	void pmb$setSuppressedPosition(net.minecraft.world.phys.Vec3 position);
}
