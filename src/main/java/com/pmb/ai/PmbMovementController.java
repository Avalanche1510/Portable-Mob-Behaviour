package com.pmb.ai;

import com.pmb.faction.PmbFactionMobState;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.BooleanSupplier;
import net.minecraft.world.entity.Mob;

/** Movement effects never participate in skill admission or item ownership. */
public final class PmbMovementController {
	public enum Type { LOCOMOTION, VELOCITY_MODIFIER, HARD_STOP }
	public enum Tier { PASSIVE, ACTIVE, RETREAT }
	private record Intent(String owner, Type type, Tier tier, PmbSkillScheduler.Category category, int rank,
			int epoch, BooleanSupplier valid, Runnable apply) {}
	private final List<Intent> intents = new ArrayList<>();
	private String winner;
	private int winnerEpoch = -1;
	private boolean applying;
	public void submit(Mob mob, String owner, Type type, Tier tier, PmbSkillScheduler.Category category,
			int rank, BooleanSupplier valid, Runnable apply) {
		intents.removeIf(intent -> intent.owner().equals(owner) && intent.type() == type);
		intents.add(new Intent(owner, type, tier, category, rank, mob.tickCount, valid, apply));
	}
	public void cancel(String owner) {
		intents.removeIf(intent -> intent.owner().equals(owner));
		if (owner.equals(winner)) winner = null;
	}
	public void clear() { intents.clear(); winner = null; }
	public static boolean hasCurrentAuthority(Mob mob, net.minecraft.world.entity.LivingEntity target, boolean evasive) {
		net.minecraft.world.entity.LivingEntity avoid = ((PmbFactionMobState) mob).pmb$getFactionAvoidTarget();
		boolean hasAvoid = avoid != null && avoid.isAlive() && avoid.level() == mob.level();
		return evasive ? hasAvoid && avoid == target : !hasAvoid && mob.getTarget() == target;
	}
	/** Runs after vanilla navigation/Brain and immediately before the actual MoveControl tick. */
	public void apply(Mob mob, PmbSkillScheduler scheduler) {
		winner = null;
		if (!mob.isAlive() || mob.isNoAi()) { clear(); return; }
		if (((PmbAiHolder) mob).pmb$getAiData().shield().isVulnerable()) {
			clear();
			mob.getNavigation().stop();
			mob.getMoveControl().setWait();
			mob.setXxa(0); mob.setZza(0); mob.setYya(0);
			return;
		}
		intents.removeIf(intent -> mob.tickCount - intent.epoch() > 1 || !intent.valid().getAsBoolean());
		Comparator<Intent> order = Comparator.comparingInt((Intent intent) -> intent.tier().ordinal()).reversed()
				.thenComparing(Comparator.comparingInt((Intent intent) -> scheduler.categoryPriority(intent.category())).reversed())
				.thenComparing(Comparator.comparingInt(Intent::rank).reversed()).thenComparing(Intent::owner);
		Intent hardStop = intents.stream().filter(intent -> intent.type() == Type.HARD_STOP).min(order).orElse(null);
		Intent locomotion = hardStop != null ? hardStop : intents.stream()
				.filter(intent -> intent.type() == Type.LOCOMOTION).min(order).orElse(null);
		applying = true;
		try {
			if (locomotion != null) {
				winner = locomotion.owner(); winnerEpoch = mob.tickCount;
				locomotion.apply().run();
			}
			if (hardStop == null) intents.stream().filter(intent -> intent.type() == Type.VELOCITY_MODIFIER).sorted(order)
					.forEach(intent -> intent.apply().run());
		} finally { applying = false; }
		// Effects are consumed once. A continuing skill/faction explicitly submits its next intent.
		intents.clear();
	}
	public boolean blocksVanillaBowMovement(Mob mob) {
		return !applying && "bow".equals(winner) && winnerEpoch == mob.tickCount
				&& mob.isAlive() && !mob.isNoAi() && mob.getTarget() != null
				&& ((PmbFactionMobState) mob).pmb$getFactionAvoidTarget() == null;
	}
}
