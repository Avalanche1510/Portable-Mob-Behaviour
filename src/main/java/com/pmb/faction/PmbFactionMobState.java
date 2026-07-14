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
}
