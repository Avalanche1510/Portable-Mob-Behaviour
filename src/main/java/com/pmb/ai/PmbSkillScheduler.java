package com.pmb.ai;

import com.pmb.PortableMobBehaviour;
import com.pmb.faction.PmbFactionMobState;
import java.util.EnumMap;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.HashMap;
import java.util.Map;
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
	public enum Resource { MAIN_HAND, OFF_HAND, USE_ITEM, LOOK, NAVIGATION, SMASH, SHAKE }
	public enum CommitResult { COMMITTED, FAILED }
	private record Candidate(String owner, String label, Category category, int rank, Supplier<EnumSet<Resource>> resources,
			BooleanSupplier preClaim, BooleanSupplier finalCheck, Supplier<CommitResult> commit) {}
	public record ActivePhase(String owner, String label, EnumSet<Resource> required, EnumSet<Resource> optional,
			BooleanSupplier valid, Runnable cancel) {
		public ActivePhase {
			required = required.isEmpty() ? EnumSet.noneOf(Resource.class) : EnumSet.copyOf(required);
			optional = optional.isEmpty() ? EnumSet.noneOf(Resource.class) : EnumSet.copyOf(optional);
		}
	}
	private final PmbMovementController movement = new PmbMovementController();
	public PmbMovementController movement() { return movement; }
	private Strategy strategy = Strategy.IDLE;
	private UUID authorityTarget;
	private final Map<String, PmbSkillItemAccess.ActionBinding> bindings = new HashMap<>();
	private final Map<Resource, String> claimedThisTick = new EnumMap<>(Resource.class);
	private final List<Candidate> candidates = new ArrayList<>();
	private final Map<String, ActivePhase> activePhases = new java.util.LinkedHashMap<>();
	private final EnumMap<Strategy, Map<Integer, Integer>> roundRobin = new EnumMap<>(Strategy.class);
	private PmbSkillPriorities priorities = new PmbSkillPriorities();
	private List<PmbSkillDebugSnapshot.CandidateAttempt> candidateAttempts;
	private int executingCandidate = -1;
	private boolean executingCandidateMarked;
	private PmbSkillDebugSnapshot lastDebugSnapshot;
	private final PmbAirTrackingController airTracking = new PmbAirTrackingController();
	private String lastPreemption;
	private PendingVanillaMelee pendingVanillaMelee;
	private record PendingVanillaMelee(net.minecraft.server.level.ServerLevel level, net.minecraft.world.entity.Entity target) {}

	public Strategy strategy() { return strategy; }
	public UUID authorityTarget() { return authorityTarget; }
	public boolean hasBinding(String skill) { return bindings.containsKey(skill); }
	public boolean bind(String skill, PmbSkillItemAccess.ActionBinding lease) {
		if (lease == null || bindings.containsKey(skill)) return false;
		bindings.put(skill, lease);
		return true;
	}
	public boolean release(Mob mob, String skill) { bindings.remove(skill); return true; }
	public void clearBindings(Mob mob) { bindings.clear(); movement.clear(); sustained.clear(); activePhases.clear(); }
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
	public void maintainPhase(String owner, String label, Runnable cancel, Resource[] required, Resource... optional) {
		maintainPhase(owner, label, () -> true, cancel, required, optional);
	}
	public void maintainPhase(String owner, String label, BooleanSupplier valid, Runnable cancel,
			Resource[] required, Resource... optional) {
		EnumSet<Resource> requiredSet = required.length == 0 ? EnumSet.noneOf(Resource.class)
				: EnumSet.copyOf(Arrays.asList(required));
		EnumSet<Resource> optionalSet = optional.length == 0 ? EnumSet.noneOf(Resource.class)
				: EnumSet.copyOf(Arrays.asList(optional));
		if (priorityTier(owner) < 0 || !valid.getAsBoolean()) {
			cancel.run();
			return;
		}
		if (requiredSet.contains(Resource.NAVIGATION) && !mayOverrideVanillaNavigation(owner)) {
			cancel.run();
			return;
		}
		List<ActivePhase> displaced = new ArrayList<>();
		for (ActivePhase incumbent : activePhases.values()) {
			if (incumbent.owner().equals(owner)) continue;
			EnumSet<Resource> conflict = incumbent.required().clone();
			conflict.retainAll(requiredSet);
			if (conflict.isEmpty()) continue;
			int incomingTier = priorityTier(owner);
			int incumbentTier = priorityTier(incumbent.owner());
			if (incomingTier >= 0 && incumbentTier >= 0 && incomingTier < incumbentTier) displaced.add(incumbent);
			else { cancel.run(); return; }
		}
		for (ActivePhase phase : displaced) {
			lastPreemption = phase.owner() + '/' + phase.label() + " -> " + owner + '/' + label;
			phase.cancel().run();
			activePhases.entrySet().removeIf(entry -> entry.getValue() == phase);
		}
		activePhases.put(owner + "#" + label, new ActivePhase(owner, label, requiredSet, optionalSet, valid, cancel));
		rebuildActiveClaims();
	}
	public Map<String, ActivePhase> activePhases() { return Map.copyOf(activePhases); }
	public PmbSkillPriorities priorities() { return priorities; }
	public String lastPreemption() { return lastPreemption; }
	public boolean ownsResource(String owner, Resource resource) { return owner.equals(claimedThisTick.get(resource)); }
	public String navigationOwner() { return claimedThisTick.get(Resource.NAVIGATION); }
	public void resetPriorityRuntime(Strategy strategy) {
		if (strategy == null) roundRobin.clear(); else roundRobin.remove(strategy);
	}
	public int priorityTier(String owner) { return priorities.tier(strategy, priorityId(owner)); }
	public boolean mayOverrideVanillaNavigation(String owner) {
		int ownerTier = priorityTier(owner);
		int vanillaTier = priorities.tier(strategy, PmbSkillPriorities.VANILLA);
		return ownerTier >= 0 && vanillaTier >= 0 && ownerTier < vanillaTier;
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
		candidates.add(new Candidate(owner, label, category, rank, () -> set, preClaim, () -> true,
				() -> { won.run(); return candidateAttempts == null || executingCandidateMarked
						? CommitResult.COMMITTED : CommitResult.FAILED; }));
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
		}, preClaim, () -> true, () -> { won.run(); return candidateAttempts == null || executingCandidateMarked
				? CommitResult.COMMITTED : CommitResult.FAILED; }));
	}
	public void offerPlanned(String owner, String label, Category category, int rank, BooleanSupplier preClaim,
			BooleanSupplier finalCheck, Runnable commit, Resource... resources) {
		EnumSet<Resource> set = resources.length == 0 ? EnumSet.noneOf(Resource.class)
				: EnumSet.copyOf(Arrays.asList(resources));
		candidates.add(new Candidate(owner, label, category, rank, () -> set, preClaim, finalCheck,
				() -> { commit.run(); return candidateAttempts == null || executingCandidateMarked
						? CommitResult.COMMITTED : CommitResult.FAILED; }));
	}
	public void offerPlannedResult(String owner, String label, Category category, int rank, BooleanSupplier preClaim,
			BooleanSupplier finalCheck, Supplier<CommitResult> commit, Resource... resources) {
		EnumSet<Resource> set = resources.length == 0 ? EnumSet.noneOf(Resource.class)
				: EnumSet.copyOf(Arrays.asList(resources));
		candidates.add(new Candidate(owner, label, category, rank, () -> set, preClaim, finalCheck, commit));
	}
	public void offerDynamicPlanned(String owner, String label, Category category, int rank,
			BooleanSupplier preClaim, BooleanSupplier finalCheck, Runnable commit, Supplier<Resource[]> resources) {
		candidates.add(new Candidate(owner, label, category, rank, () -> {
			Resource[] resolved = resources.get();
			return resolved.length == 0 ? EnumSet.noneOf(Resource.class)
					: EnumSet.copyOf(Arrays.asList(resolved));
		}, preClaim, finalCheck, () -> { commit.run(); return candidateAttempts == null || executingCandidateMarked
				? CommitResult.COMMITTED : CommitResult.FAILED; }));
	}
	public void offerDynamicPlannedResult(String owner, String label, Category category, int rank,
			BooleanSupplier preClaim, BooleanSupplier finalCheck, Supplier<CommitResult> commit,
			Supplier<Resource[]> resources) {
		candidates.add(new Candidate(owner, label, category, rank, () -> {
			Resource[] resolved = resources.get();
			return resolved.length == 0 ? EnumSet.noneOf(Resource.class)
					: EnumSet.copyOf(Arrays.asList(resolved));
		}, preClaim, finalCheck, commit));
	}
	public static PmbSkillScheduler of(Mob mob) { return ((PmbSchedulerHolder) mob).pmb$getSkillScheduler(); }
	public void queueVanillaMelee(net.minecraft.server.level.ServerLevel level, net.minecraft.world.entity.Entity target) {
		pendingVanillaMelee = new PendingVanillaMelee(level, target);
	}
	public PmbSkillDebugSnapshot lastDebugSnapshot() { return lastDebugSnapshot; }
	public PmbAirTrackingController airTracking() { return airTracking; }
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
		activePhases.clear();
		PmbAiData ai = ((PmbAiHolder) mob).pmb$getAiData();
		priorities = ai.skillPriorities();
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
		if (next == Strategy.BLOCKED) cancelSustained(mob);
		strategy = next;
		authorityTarget = nextTarget;
		if (((PmbAiHolder) mob).pmb$getAiData().shield().isVulnerable())
			claim("shield_vulnerability", Resource.SHAKE);
		((PmbSkillHooks.Bow) mob).pmb$claimBowResources(this);
		((PmbSkillHooks.Shield) mob).pmb$claimShieldResources(this);
		((PmbSkillHooks.Wind) mob).pmb$claimWindResources(this);
		((PmbSkillHooks.Pearl) mob).pmb$claimPearlResources(this);
		airTracking.tick(mob, this);
		sustained.clear();
		sustained.putAll(claimedThisTick);
		((PmbSkillHooks.Mace) mob).pmb$tickMaceSkill();
		((PmbSkillHooks.Bow) mob).pmb$tickBowSkill();
		((PmbSkillHooks.Shield) mob).pmb$tickShieldSkill();
		((PmbSkillHooks.Pearl) mob).pmb$tickPearlSkill();
		((PmbSkillHooks.Wind) mob).pmb$tickWindSkill();
		if (pendingVanillaMelee != null) {
			PendingVanillaMelee pending = pendingVanillaMelee;
			offerPlanned(PmbSkillPriorities.VANILLA, "melee", Category.MAIN, 0, () -> true,
					() -> mob.isAlive() && pending.target().isAlive() && pending.target().level() == mob.level()
							&& pending.target() instanceof LivingEntity living && mob.canAttack(living)
							&& mob.isWithinMeleeAttackRange(living) && mob.getSensing().hasLineOfSight(living),
					() -> {
						mob.doHurtTarget(pending.level(), pending.target());
						markCurrentCandidateExecuted(PmbSkillPriorities.VANILLA);
					});
			pendingVanillaMelee = null;
		}
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
		List<Candidate> ordered = orderedCandidates();
		for (Candidate candidate : ordered) {
			if (!candidate.preClaim().getAsBoolean()) {
				if (candidateAttempts != null) candidateAttempts.add(
						PmbSkillDebugSnapshot.CandidateAttempt.preClaimRejected(candidate.owner(),
								candidate.label(), candidate.category(), candidate.rank()));
				continue;
			}
			Resource[] resources = candidate.resources().get().toArray(Resource[]::new);
			Resource blocked = null;
			String blocker = null;
			List<ActivePhase> preempt = new ArrayList<>();
			int candidateTier = priorities.tier(strategy, priorityId(candidate.owner()));
			if (java.util.Arrays.asList(resources).contains(Resource.NAVIGATION)
					&& !mayOverrideVanillaNavigation(candidate.owner())) {
				if (candidateAttempts != null) candidateAttempts.add(PmbSkillDebugSnapshot.CandidateAttempt.resourceBlocked(
						candidate.owner(), candidate.label(), candidate.category(), candidate.rank(), resources,
						Resource.NAVIGATION, PmbSkillPriorities.VANILLA));
				continue;
			}
			for (ActivePhase phase : activePhases.values()) {
				if (phase.owner().equals(candidate.owner())) continue;
				EnumSet<Resource> conflict = phase.required().clone();
				conflict.retainAll(candidate.resources().get());
				if (conflict.isEmpty()) continue;
				int incumbentTier = priorities.tier(strategy, priorityId(phase.owner()));
				if (candidateTier >= 0 && incumbentTier >= 0 && candidateTier < incumbentTier) preempt.add(phase);
				else { blocked = conflict.iterator().next(); blocker = phase.owner(); break; }
			}
			if (blocked == null) for (Resource resource : resources) {
				String current = claimedThisTick.get(resource);
				if (current != null && !current.equals(candidate.owner())
						&& activePhases.values().stream().noneMatch(phase -> phase.owner().equals(current))) {
					blocked = resource; blocker = current; break;
				}
			}
			if (blocked != null) {
				if (candidateAttempts != null) candidateAttempts.add(PmbSkillDebugSnapshot.CandidateAttempt.resourceBlocked(candidate.owner(),
						candidate.label(), candidate.category(), candidate.rank(), resources, blocked, blocker));
				continue;
			}
			if (!candidate.finalCheck().getAsBoolean()) {
				if (candidateAttempts != null) candidateAttempts.add(
						PmbSkillDebugSnapshot.CandidateAttempt.admitted(candidate.owner(), candidate.label(),
								candidate.category(), candidate.rank(), resources).completed(false));
				continue;
			}
			for (ActivePhase phase : preempt) {
				lastPreemption = phase.owner() + '/' + phase.label() + " -> " + candidate.owner() + '/' + candidate.label();
				phase.cancel().run();
				activePhases.entrySet().removeIf(entry -> entry.getValue() == phase);
			}
			rebuildActiveClaims();
			claimCandidateRequired(candidate.owner(), resources);
			if (candidateAttempts != null) {
				candidateAttempts.add(PmbSkillDebugSnapshot.CandidateAttempt.admitted(candidate.owner(),
						candidate.label(), candidate.category(), candidate.rank(), resources));
				executingCandidate = candidateAttempts.size() - 1;
			} else executingCandidate = -1;
			executingCandidateMarked = false;
			CommitResult result = CommitResult.FAILED;
			RuntimeException commitFailure = null;
			try { result = candidate.commit().get(); }
			catch (RuntimeException exception) { result = CommitResult.FAILED; commitFailure = exception; }
			finally {
				if (candidateAttempts != null && executingCandidate >= 0) {
					PmbSkillDebugSnapshot.CandidateAttempt current = candidateAttempts.get(executingCandidate);
					candidateAttempts.set(executingCandidate, result == CommitResult.COMMITTED
							? current.completed(true) : current.commitFailed(!preempt.isEmpty()));
				}
				executingCandidate = -1;
				executingCandidateMarked = false;
			}
			if (result == CommitResult.COMMITTED) {
				advanceRoundRobin(candidateTier, candidate.owner());
				break;
			}
			rebuildActiveClaims();
			if (commitFailure != null) PortableMobBehaviour.LOGGER.error("PMB skill commit failed for {}/{}",
					candidate.owner(), candidate.label(), commitFailure);
			break;
		}
	}
	private void claimCandidateRequired(String owner, Resource[] resources) {
		for (Resource resource : resources) {
			String current = claimedThisTick.get(resource);
			if (current == null || current.equals(owner)) continue;
			boolean requiredByIncumbent = activePhases.values().stream()
					.anyMatch(phase -> phase.owner().equals(current) && phase.required().contains(resource));
			if (!requiredByIncumbent) claimedThisTick.remove(resource);
		}
		if (!claim(owner, resources)) throw new IllegalStateException("validated PMB resource plan changed before commit");
	}
	private void rebuildActiveClaims() {
		String shieldVulnerability = claimedThisTick.get(Resource.SHAKE);
		claimedThisTick.clear();
		if (shieldVulnerability != null) claimedThisTick.put(Resource.SHAKE, shieldVulnerability);
		for (ActivePhase phase : activePhases.values()) {
			for (Resource resource : phase.required()) claimedThisTick.put(resource, phase.owner());
			for (Resource resource : phase.optional()) claimedThisTick.putIfAbsent(resource, phase.owner());
		}
	}
	private List<Candidate> orderedCandidates() {
		List<List<String>> tiers = priorities.effective(strategy);
		List<Candidate> ordered = new ArrayList<>();
		for (int tier = 0; tier < tiers.size(); tier++) {
			List<String> owners = tiers.get(tier);
			int start = owners.isEmpty() ? 0 : roundRobin.computeIfAbsent(strategy, ignored -> new HashMap<>())
					.getOrDefault(tier, 0) % owners.size();
			for (int offset = 0; offset < owners.size(); offset++) {
				String id = owners.get((start + offset) % owners.size());
				candidates.stream().filter(candidate -> priorityId(candidate.owner()).equals(id))
						.sorted(java.util.Comparator.comparingInt(Candidate::rank).reversed()
								.thenComparing(Candidate::label)).forEach(ordered::add);
			}
		}
		return ordered;
	}
	private void advanceRoundRobin(int tier, String winner) {
		if (tier < 0) return;
		List<String> owners = priorities.effective(strategy).get(tier);
		boolean contested = candidates.stream().anyMatch(candidate -> !priorityId(candidate.owner()).equals(priorityId(winner))
				&& priorities.tier(strategy, priorityId(candidate.owner())) == tier);
		if (owners.size() > 1 && contested) {
			int winnerIndex = owners.indexOf(priorityId(winner));
			roundRobin.computeIfAbsent(strategy, ignored -> new HashMap<>())
					.put(tier, (winnerIndex + 1) % owners.size());
		}
	}
	public static String skillIdForOwner(String owner) {
		return switch (owner) { case "wind" -> "wind_charge"; case "pearl" -> "ender_pearl"; default -> owner; };
	}
	private static String priorityId(String owner) { return skillIdForOwner(owner); }
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
		airTracking.clear("blocked");
		((PmbSkillHooks.Pearl) mob).pmb$cancelPearlSkill();
	}
	private static boolean valid(Mob mob, LivingEntity target) {
		return target != null && target.isAlive() && target.level() == mob.level();
	}
}
