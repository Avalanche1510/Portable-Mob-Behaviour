package com.pmb.ai;

import java.util.List;
import java.util.EnumSet;
import java.util.function.Predicate;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.ItemStack;

/** Validated, permanent equipment exchanges and short-lived action bindings. */
public final class PmbSkillItemAccess {
	private PmbSkillItemAccess() {}
	public enum Transfer { DIRECT, INVENTORY_SWAP, HAND_SWAP }
	public record Resolved(Transfer transfer, InteractionHand hand, InteractionHand sourceHand, int inventorySlot,
			ItemStack sourceSnapshot, ItemStack displacedSnapshot) {
		public PmbSkillScheduler.Resource[] resources(PmbSkillScheduler.Resource... additional) {
			EnumSet<PmbSkillScheduler.Resource> result = EnumSet.of(resource(hand));
			if (transfer == Transfer.HAND_SWAP) result.add(resource(sourceHand));
			java.util.Collections.addAll(result, additional);
			return result.toArray(PmbSkillScheduler.Resource[]::new);
		}
	}
	public static PmbSkillScheduler.Resource resource(InteractionHand hand) {
		return hand == InteractionHand.MAIN_HAND ? PmbSkillScheduler.Resource.MAIN_HAND : PmbSkillScheduler.Resource.OFF_HAND;
	}
	public static final class ActionBinding {
		private final Resolved swap;
		private ItemStack actionSnapshot;
		private ActionBinding(Resolved swap) { this.swap = swap; this.actionSnapshot = swap.sourceSnapshot().copy(); }
		public InteractionHand hand() { return swap.hand(); }
		public InteractionHand sourceHand() { return swap.sourceHand(); }
		public Transfer transfer() { return swap.transfer(); }
		public int inventorySlot() { return swap.inventorySlot(); }
		public boolean fromInventory() { return swap.transfer() == Transfer.INVENTORY_SWAP; }
		public boolean matches(Mob mob) { return ItemStack.matches(mob.getItemInHand(hand()), actionSnapshot); }
		public void authorizeAction(Mob mob) { actionSnapshot = mob.getItemInHand(hand()).copy(); }
	}
	/** Both locations are validated before either write; no original-layout restoration is retained. */
	public static ActionBinding acquire(Mob mob, Resolved resolved) {
		if (resolved == null) return null;
		ItemStack source = switch (resolved.transfer()) {
			case DIRECT, HAND_SWAP -> mob.getItemInHand(resolved.sourceHand());
			case INVENTORY_SWAP -> ((PmbInventoryHolder) mob).pmb$getInventory().get(resolved.inventorySlot());
		};
		if (!ItemStack.matches(source, resolved.sourceSnapshot())) return null;
		if (resolved.transfer() != Transfer.DIRECT) {
			ItemStack displaced = mob.getItemInHand(resolved.hand());
			if (!ItemStack.matches(displaced, resolved.displacedSnapshot())) return null;
			ItemStack equipped = source.copy();
			ItemStack stored = displaced.copy();
			if (resolved.transfer() == Transfer.HAND_SWAP) mob.setItemInHand(resolved.sourceHand(), stored);
			else ((PmbInventoryHolder) mob).pmb$getInventory().set(resolved.inventorySlot(), stored);
			mob.setItemInHand(resolved.hand(), equipped);
		}
		return new ActionBinding(resolved);
	}
	public static Resolved resolvePreferred(Mob mob, PmbActivationSources configured, List<InteractionHand> defaults,
			Predicate<ItemStack> predicate, PmbPreferredHand preference, String owner) {
		PmbSkillScheduler scheduler = PmbSkillScheduler.of(mob);
		InteractionHand hand = preference.hand();
		if (!scheduler.handAvailable(owner, hand)) {
			if (preference.enforced()) return null;
			hand = hand == InteractionHand.MAIN_HAND ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND;
			if (!scheduler.handAvailable(owner, hand)) return null;
		}
		return resolveAtHand(mob, configured, defaults, predicate, hand, owner);
	}
	private static Resolved resolveAtHand(Mob mob, PmbActivationSources configured, List<InteractionHand> defaults,
			Predicate<ItemStack> predicate, InteractionHand hand, String owner) {
		if (!PmbSkillScheduler.of(mob).handAvailable(owner, hand)) return null;
		ItemStack held = mob.getItemInHand(hand);
		if (predicate.test(held)) return new Resolved(Transfer.DIRECT, hand, hand, 0, held.copy(), ItemStack.EMPTY);
		if (!configured.isExplicit()) {
			for (InteractionHand source : defaults) {
				Resolved found = fromHand(mob, hand, source, predicate, owner);
				if (found != null) return found;
			}
			return null;
		}
		for (PmbActivationSources.Source source : configured.sources()) {
			if (source.kind() != PmbActivationSources.Kind.INVENTORY) {
				InteractionHand sourceHand = source.kind() == PmbActivationSources.Kind.MAIN_HAND
						? InteractionHand.MAIN_HAND : InteractionHand.OFF_HAND;
				Resolved found = fromHand(mob, hand, sourceHand, predicate, owner);
				if (found != null) return found;
			} else if (mob instanceof PmbInventoryHolder holder) {
				int last = Math.min(source.lastSlot(), holder.pmb$getInventory().slots());
				for (int slot = source.firstSlot(); slot <= last; slot++) {
					ItemStack candidate = holder.pmb$getInventory().get(slot);
					if (predicate.test(candidate)) return new Resolved(Transfer.INVENTORY_SWAP, hand, null, slot,
							candidate.copy(), held.copy());
				}
			}
		}
		return null;
	}
	private static Resolved fromHand(Mob mob, InteractionHand hand, InteractionHand source,
			Predicate<ItemStack> predicate, String owner) {
		if (!PmbSkillScheduler.of(mob).handAvailable(owner, source)) return null;
		ItemStack item = mob.getItemInHand(source);
		if (!predicate.test(item)) return null;
		return new Resolved(source == hand ? Transfer.DIRECT : Transfer.HAND_SWAP, hand, source, 0,
				item.copy(), mob.getItemInHand(hand).copy());
	}
	public static Resolved resolveForRequiredHand(Mob mob, PmbActivationSources configured,
			List<InteractionHand> defaults, Predicate<ItemStack> predicate, InteractionHand required) {
		return resolveAtHand(mob, configured, defaults, predicate, required, "mace");
	}
	/**
	 * Checks whether an already selected vanilla TargetGoal target may be retained
	 * without line of sight. This deliberately only inspects configuration, items,
	 * and range; it does not acquire an item binding or advance skill state.
	 */
	public static boolean canRetainTargetWithoutEyeSight(Mob mob, LivingEntity target) {
		if (!target.isAlive() || target.level() != mob.level()) return false;
		double followRange = Math.max(0.0D, mob.getAttributeValue(Attributes.FOLLOW_RANGE));
		if (mob.distanceToSqr(target) > followRange * followRange) return false;

		double distance = mob.distanceTo(target);
		PmbBowAiData bowAi = ((PmbAiHolder) mob).pmb$getAiData().bow();
		if (bowAi.isEnabled() && !bowAi.requireEyeSight()
				&& bowAi.canShootAtDistance(distance)
				&& canManageBow(mob)) return true;

		PmbEnderPearlAiData pearlAi = ((PmbAiHolder) mob).pmb$getAiData().enderPearl();
		if (!pearlAi.isEnabled() || pearlAi.requireEyeSight() || pearlAi.throwChance() <= 0.0F
				|| !pearlAi.canThrowAtCombatDistance(distance)) return false;
		return resolvePreferred(mob, pearlAi.fetchSource(), List.of(InteractionHand.MAIN_HAND, InteractionHand.OFF_HAND),
				stack -> stack.is(Items.ENDER_PEARL), pearlAi.preferredHand(), "pearl") != null;
	}

	/** True when PMB already owns a bow binding or can resolve one from the configured ordered sources. */
	public static boolean canManageBow(Mob mob) {
		PmbBowAiData bowAi = ((PmbAiHolder) mob).pmb$getAiData().bow();
		return bowAi.isEnabled() && (PmbSkillScheduler.of(mob).hasBinding("bow")
				|| resolvePreferred(mob, bowAi.fetchSource(),
						List.of(InteractionHand.MAIN_HAND),
						stack -> stack.is(Items.BOW), bowAi.preferredHand(), "bow") != null);
	}

	/**
	 * True while PMB bow use should suppress ordinary melee and movement control.
	 * An active bow binding remains authoritative until it is released. Outside an
	 * active action, consuming bow AI suppresses vanilla behavior only while a
	 * configured bow and real supported ammunition are both available.
	 */
	public static boolean shouldSuppressMeleeForBow(Mob mob) {
		PmbBowAiData bowAi = ((PmbAiHolder) mob).pmb$getAiData().bow();
		if (!bowAi.isEnabled()) return false;
		if (PmbSkillScheduler.of(mob).hasBinding("bow")) return true;
		if (resolvePreferred(mob, bowAi.fetchSource(),
				List.of(InteractionHand.MAIN_HAND), stack -> stack.is(Items.BOW),
				bowAi.preferredHand(), "bow") == null) return false;
		return !bowAi.doConsume() || PmbAmmoAccess.find(mob, bowAi.ammoSource(),
				((BowItem) Items.BOW).getAllSupportedProjectiles()) != null;
	}

}
