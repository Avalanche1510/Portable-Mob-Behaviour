package com.pmb.ai;

import com.pmb.faction.PmbFactionMobState;
import java.util.EnumMap;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/** Single server-tick entry point for all PMB skills. */
public final class PmbSkillScheduler {
	public enum Strategy { IDLE, COMBAT, RETREAT, BLOCKED }
	public enum Category { MAIN, OFF, THROW, FOOD, BLOCK }
	public enum Resource { MAIN_HAND, OFF_HAND, USE_ITEM, LOOK, SMASH, SHAKE }
	private record Candidate(String owner, String label, Category category, int rank, Supplier<EnumSet<Resource>> resources,
			BooleanSupplier preClaim, Runnable won) {}
	private final PmbMovementController movement = new PmbMovementController();
	public PmbMovementController movement() { return movement; }
	private Strategy strategy = Strategy.IDLE;
	private UUID authorityTarget;
	private final Map<String, PmbSkillItemAccess.ActionBinding> bindings = new HashMap<>();
	private final Map<Resource, String> claimedThisTick = new EnumMap<>(Resource.class);
	private final List<Candidate> candidates = new ArrayList<>();
	private List<PmbSkillDebugSnapshot.CandidateAttempt> candidateAttempts;
	private int executingCandidate = -1;
	private boolean executingCandidateMarked;
	private PmbSkillDebugSnapshot lastDebugSnapshot;

	public Strategy strategy() { return strategy; }
	public UUID authorityTarget() { return authorityTarget; }
	public boolean hasBinding(String skill) { return bindings.containsKey(skill); }
	public boolean bind(String skill, PmbSkillItemAccess.ActionBinding lease) {
		if (lease == null || bindings.containsKey(skill)) return false;
		bindings.put(skill, lease);
		return true;
	}
	public boolean release(Mob mob, String skill) { bindings.remove(skill); return true; }
	public void clearBindings(Mob mob) { bindings.clear(); movement.clear(); sustained.clear(); }
	private final Map<Resource, String> sustained = new EnumMap<>(Resource.class);
	public boolean handAvailable(String owner, InteractionHand hand) {
		String current = sustained.get(PmbSkillItemAccess.resource(hand));
		return current == null || current.equals(owner);
	}
	public boolean claim(String owner, Resource... resources) {
		for (Resource resource : resources) {
			String current = claimedThisTick.get(resource);
			if (current != null && !current.equals(owner)) return false;
		}
		for (Resource resource : resources) claimedThisTick.put(resource, owner);
		return true;
	}
	public boolean canClaim(String owner, Resource resource) {
		String current = claimedThisTick.get(resource);
		return current == null || current.equals(owner);
	}
	public void unclaim(String owner, Resource... resources) {
		for (Resource resource : resources) if (owner.equals(claimedThisTick.get(resource))) claimedThisTick.remove(resource);
	}
	public void offer(String owner, Category category, int rank, BooleanSupplier preClaim, Runnable won,
			Resource... resources) {
		offer(owner, owner, category, rank, preClaim, won, resources);
	}
	public void offer(String owner, String label, Category category, int rank, BooleanSupplier preClaim, Runnable won,
			Resource... resources) {
		EnumSet<Resource> set = resources.length == 0 ? EnumSet.noneOf(Resource.class)
				: EnumSet.copyOf(Arrays.asList(resources));
		candidates.add(new Candidate(owner, label, category, rank, () -> set, preClaim, won));
	}
	public void offerDynamic(String owner, Category category, int rank, BooleanSupplier preClaim, Runnable won,
			Supplier<Resource[]> resources) {
		offerDynamic(owner, owner, category, rank, preClaim, won, resources);
	}
	public void offerDynamic(String owner, String label, Category category, int rank, BooleanSupplier preClaim, Runnable won,
			Supplier<Resource[]> resources) {
		candidates.add(new Candidate(owner, label, category, rank, () -> {
			Resource[] resolved = resources.get();
			return resolved.length == 0 ? EnumSet.noneOf(Resource.class)
					: EnumSet.copyOf(Arrays.asList(resolved));
		}, preClaim, won));
	}
	public static PmbSkillScheduler of(Mob mob) { return ((PmbSchedulerHolder) mob).pmb$getSkillScheduler(); }
	public PmbSkillDebugSnapshot lastDebugSnapshot() { return lastDebugSnapshot; }
	public void markCurrentCandidateExecuted(String owner) {
		if (candidateAttempts == null || executingCandidate < 0 || executingCandidate >= candidateAttempts.size()) return;
		PmbSkillDebugSnapshot.CandidateAttempt attempt = candidateAttempts.get(executingCandidate);
		if (attempt.owner().equals(owner)) executingCandidateMarked = true;
	}

	/** One-time compatibility recovery for pre-permanent-swap saves. No new journal is written. */
	public void readAndRollbackSwapJournal(Mob mob, ValueInput root) {
		bindings.clear();
		if (!(mob instanceof PmbInventoryHolder holder)) return;
		for (ValueInput input : root.childrenListOrEmpty("PmbSwapJournal")) {
			InteractionHand hand = input.getStringOr("Hand", "mainhand").equals("offhand")
					? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND;
			int slot = input.getIntOr("Slot", 0);
			if (slot < 1 || slot > holder.pmb$getInventory().slots()) continue;
			ItemStack displaced = input.read("Displaced", ItemStack.CODEC).orElse(ItemStack.EMPTY);
			ItemStack action = input.read("Action", ItemStack.CODEC).orElse(ItemStack.EMPTY);
			if (ItemStack.matches(mob.getItemInHand(hand), action)
					&& ItemStack.matches(holder.pmb$getInventory().get(slot), displaced)) {
				mob.setItemInHand(hand, displaced.copy());
				holder.pmb$getInventory().set(slot, action.copy());
			}
		}
	}

	public void tick(Mob mob) {
		claimedThisTick.clear();
		candidates.clear();
		PmbAiData ai = ((PmbAiHolder) mob).pmb$getAiData();
		prepareDebugCapture(ai.isConfigured());
		LivingEntity avoid = ((PmbFactionMobState) mob).pmb$getFactionAvoidTarget();
		LivingEntity combat = mob.getTarget();
		Strategy next;
		LivingEntity target;
		if (!mob.isAlive() || mob.isNoAi() || ((PmbAiHolder) mob).pmb$getAiData().shield().isVulnerable()) {
			next = Strategy.BLOCKED; target = null;
		} else if (valid(mob, avoid)) {
			next = Strategy.RETREAT; target = avoid;
		} else if (valid(mob, combat)) {
			next = Strategy.COMBAT; target = combat;
		} else {
			next = Strategy.IDLE; target = null;
		}
		UUID nextTarget = target == null ? null : target.getUUID();
		if (strategy != next || !Objects.equals(authorityTarget, nextTarget)) cancelSustained(mob);
		strategy = next;
		authorityTarget = nextTarget;
		if (((PmbAiHolder) mob).pmb$getAiData().shield().isVulnerable())
			claim("shield_vulnerability", Resource.SHAKE);
		((PmbSkillHooks.Bow) mob).pmb$claimBowResources(this);
		((PmbSkillHooks.Shield) mob).pmb$claimShieldResources(this);
		((PmbSkillHooks.Wind) mob).pmb$claimWindResources(this);
		((PmbSkillHooks.Pearl) mob).pmb$claimPearlResources(this);
		sustained.clear();
		sustained.putAll(claimedThisTick);
		((PmbSkillHooks.Mace) mob).pmb$tickMaceSkill();
		((PmbSkillHooks.Bow) mob).pmb$tickBowSkill();
		((PmbSkillHooks.Shield) mob).pmb$tickShieldSkill();
		((PmbSkillHooks.Pearl) mob).pmb$tickPearlSkill();
		((PmbSkillHooks.Wind) mob).pmb$tickWindSkill();
		resolveCandidates();
		if (candidateAttempts != null) {
			lastDebugSnapshot = PmbSkillDebugSnapshot.capture(mob, this, ai,
					candidateAttempts, sustained, claimedThisTick, bindings, movement.snapshot());
			candidateAttempts = null;
		}
	}
	private void prepareDebugCapture(boolean configured) {
		if (configured) candidateAttempts = new ArrayList<>();
		else {
			candidateAttempts = null;
			lastDebugSnapshot = null;
		}
	}
	private void resolveCandidates() {
		candidates.sort(Comparator.comparingInt((Candidate candidate) -> categoryPriority(candidate.category())).reversed()
				.thenComparing(Comparator.comparingInt(Candidate::rank).reversed()).thenComparing(Candidate::owner));
		List<Candidate> winners = new ArrayList<>();
		List<Integer> winnerAttemptIndices = candidateAttempts == null ? null : new ArrayList<>();
		for (Candidate candidate : candidates) {
			if (!candidate.preClaim().getAsBoolean()) {
				if (candidateAttempts != null) candidateAttempts.add(
						PmbSkillDebugSnapshot.CandidateAttempt.preClaimRejected(candidate.owner(),
								candidate.label(), candidate.category(), candidate.rank()));
				continue;
			}
			Resource[] resources = candidate.resources().get().toArray(Resource[]::new);
			if (candidateAttempts == null) {
				if (claim(candidate.owner(), resources)) winners.add(candidate);
				continue;
			}
			Resource blocked = null;
			String blocker = null;
			for (Resource resource : resources) {
				String current = claimedThisTick.get(resource);
				if (current != null && !current.equals(candidate.owner())) { blocked = resource; blocker = current; break; }
			}
			if (blocked != null) {
				candidateAttempts.add(PmbSkillDebugSnapshot.CandidateAttempt.resourceBlocked(candidate.owner(),
						candidate.label(), candidate.category(), candidate.rank(), resources, blocked, blocker));
				continue;
			}
			claim(candidate.owner(), resources);
			candidateAttempts.add(PmbSkillDebugSnapshot.CandidateAttempt.admitted(candidate.owner(),
					candidate.label(), candidate.category(), candidate.rank(), resources));
			winners.add(candidate);
			winnerAttemptIndices.add(candidateAttempts.size() - 1);
		}
		for (int winnerIndex = 0; winnerIndex < winners.size(); winnerIndex++) {
			Candidate winner = winners.get(winnerIndex);
			executingCandidate = winnerAttemptIndices == null ? -1 : winnerAttemptIndices.get(winnerIndex);
			executingCandidateMarked = false;
			try { winner.won().run(); }
			finally {
				if (candidateAttempts != null && executingCandidate >= 0) {
					PmbSkillDebugSnapshot.CandidateAttempt current = candidateAttempts.get(executingCandidate);
					candidateAttempts.set(executingCandidate, current.completed(executingCandidateMarked));
				}
				executingCandidate = -1;
				executingCandidateMarked = false;
			}
		}
	}
	public int categoryPriority(Category category) {
		return switch (strategy) {
			case COMBAT -> switch (category) { case MAIN -> 5; case OFF -> 4; case THROW -> 3; case FOOD -> 2; case BLOCK -> 1; };
			case RETREAT -> switch (category) { case THROW -> 5; case OFF -> 4; case FOOD -> 3; case MAIN -> 2; case BLOCK -> 1; };
			case IDLE, BLOCKED -> switch (category) { case BLOCK -> 5; case FOOD -> 4; case OFF -> 3; case MAIN -> 2; case THROW -> 1; };
		};
	}
	private void cancelSustained(Mob mob) {
		movement.clear();
		((PmbSkillHooks.Bow) mob).pmb$cancelBowSkill();
		((PmbSkillHooks.Shield) mob).pmb$cancelShieldSkill();
		((PmbSkillHooks.Wind) mob).pmb$cancelWindSkill();
		((PmbSkillHooks.Pearl) mob).pmb$cancelPearlSkill();
	}
	private static boolean valid(Mob mob, LivingEntity target) {
		return target != null && target.isAlive() && target.level() == mob.level();
	}
}
