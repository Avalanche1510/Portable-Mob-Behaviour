package com.pmb.faction;

import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.monster.hoglin.Hoglin;
import net.minecraft.world.entity.monster.piglin.Piglin;
import net.minecraft.world.entity.monster.piglin.PiglinBrute;

/** Chooses which intent a Faction Brain mob may publish; vanilla still executes the behavior. */
final class PmbBrainIntentCoordinator {
	enum Intent { ADMIRE, AVOID, COMBAT, VANILLA }

	private PmbBrainIntentCoordinator() {}

	static boolean supports(Mob mob) {
		return mob instanceof Piglin || mob instanceof PiglinBrute || mob instanceof Hoglin;
	}

	static Intent choose(Mob mob, PmbFactionDefinition definition,
			LivingEntity avoid, LivingEntity combat) {
		return choose(mob instanceof Piglin piglin && definition.vanillaCompatRules().piglin().greed()
				&& piglin.getBrain().hasMemoryValue(MemoryModuleType.ADMIRING_ITEM),
				avoid != null, combat != null);
	}

	static Intent choose(boolean admiring, boolean avoiding, boolean combating) {
		if (admiring) return Intent.ADMIRE;
		if (avoiding) return Intent.AVOID;
		if (combating) return Intent.COMBAT;
		return Intent.VANILLA;
	}
}
