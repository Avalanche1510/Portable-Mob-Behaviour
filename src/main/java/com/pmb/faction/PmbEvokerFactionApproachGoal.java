package com.pmb.faction;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.monster.illager.Evoker;

import java.util.EnumSet;

public final class PmbEvokerFactionApproachGoal extends Goal {
	private static final double FANG_APPROACH_RANGE = 16.0D;
	private static final double FANG_APPROACH_RANGE_SQR = FANG_APPROACH_RANGE * FANG_APPROACH_RANGE;
	private static final int REPATH_INTERVAL = 10;

	private final Evoker evoker;
	private int repathDelay;

	public PmbEvokerFactionApproachGoal(Evoker evoker) {
		this.evoker = evoker;
		setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
	}

	@Override
	public boolean canUse() {
		return shouldApproach();
	}

	@Override
	public boolean canContinueToUse() {
		return shouldApproach();
	}

	@Override
	public void start() {
		repathDelay = 0;
	}

	@Override
	public void stop() {
		evoker.getNavigation().stop();
	}

	@Override
	public boolean requiresUpdateEveryTick() {
		return true;
	}

	@Override
	public void tick() {
		LivingEntity target = PmbFactionAi.factionCombatTarget(evoker);
		if (target == null) {
			return;
		}
		evoker.getLookControl().setLookAt(target, evoker.getMaxHeadYRot(), evoker.getMaxHeadXRot());
		if (--repathDelay <= 0 || evoker.getNavigation().isDone()) {
			evoker.getNavigation().moveTo(target, 1.0D);
			repathDelay = adjustedTickDelay(REPATH_INTERVAL);
		}
	}

	private boolean shouldApproach() {
		LivingEntity target = PmbFactionAi.factionCombatTarget(evoker);
		return target != null && target.isAlive() && target.level() == evoker.level()
				&& evoker.distanceToSqr(target) > FANG_APPROACH_RANGE_SQR;
	}
}
