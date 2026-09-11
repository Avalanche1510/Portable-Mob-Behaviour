package com.pmb.ai;

import java.util.function.Predicate;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.ItemStack;

public final class PmbAmmoAccess {
	private PmbAmmoAccess() {}
	public static final class Source {
		private InteractionHand hand;
		private int inventorySlot;
		private ItemStack expected;
		private final ItemStack projectile;
		private Source(InteractionHand hand, int inventorySlot, ItemStack stack) {
			this.hand = hand; this.inventorySlot = inventorySlot;
			this.expected = stack.copy(); this.projectile = stack.copyWithCount(1);
		}
		public InteractionHand hand() { return hand; }
		public int inventorySlot() { return inventorySlot; }
		public ItemStack projectile() { return projectile.copy(); }
		/**
		 * Follows an already-recorded hand stack when a permanent weapon exchange
		 * displaces it into another hand or the original inventory slot. This is
		 * bookkeeping only: ammunition is never separately moved or exchanged.
		 */
		public void followEquipmentSwap(PmbSkillItemAccess.ActionBinding binding) {
			if (binding == null || hand != binding.hand()) return;
			if (binding.fromInventory()) {
				hand = null; inventorySlot = binding.inventorySlot();
			} else if (binding.transfer() == PmbSkillItemAccess.Transfer.HAND_SWAP) {
				hand = binding.sourceHand();
			}
		}
		public boolean matches(Mob mob) { return ItemStack.matches(current(mob), expected); }
		public boolean consume(Mob mob) {
			if (!matches(mob)) return false;
			if (hand != null) {
				ItemStack stack = mob.getItemInHand(hand);
				stack.consume(1, mob);
				mob.setItemInHand(hand, stack.isEmpty() ? ItemStack.EMPTY : stack.copy());
			} else if (inventorySlot > 0 && mob instanceof PmbInventoryHolder holder) {
				ItemStack stack = holder.pmb$getInventory().get(inventorySlot);
				stack.consume(1, mob);
				holder.pmb$getInventory().set(inventorySlot, stack.isEmpty() ? ItemStack.EMPTY : stack.copy());
			}
			expected = current(mob).copy();
			return true;
		}
		private ItemStack current(Mob mob) {
			if (hand != null) return mob.getItemInHand(hand);
			return inventorySlot > 0 && mob instanceof PmbInventoryHolder holder
					? holder.pmb$getInventory().get(inventorySlot) : ItemStack.EMPTY;
		}
	}

	public static Source find(Mob mob, Predicate<ItemStack> supported) {
		for (InteractionHand hand : InteractionHand.values()) {
			ItemStack stack = mob.getItemInHand(hand);
			if (supported.test(stack)) return new Source(hand, 0, stack);
		}
		if (mob instanceof PmbInventoryHolder holder) {
			for (int slot = 1; slot <= holder.pmb$getInventory().slots(); slot++) {
				ItemStack stack = holder.pmb$getInventory().get(slot);
				if (supported.test(stack)) return new Source(null, slot, stack);
			}
		}
		return null;
	}

	/**
	 * Resolves real ammunition without moving it. Omission preserves the original
	 * hands-then-lowest-enabled-inventory scan; an explicit empty list has no source.
	 */
	public static Source find(Mob mob, PmbAmmoSources sources, Predicate<ItemStack> supported) {
		if (!sources.isExplicit()) return find(mob, supported);
		for (PmbActivationSources.Source source : sources.sources()) {
			switch (source.kind()) {
				case MAIN_HAND -> {
					ItemStack stack = mob.getMainHandItem();
					if (supported.test(stack)) return new Source(InteractionHand.MAIN_HAND, 0, stack);
				}
				case OFF_HAND -> {
					ItemStack stack = mob.getOffhandItem();
					if (supported.test(stack)) return new Source(InteractionHand.OFF_HAND, 0, stack);
				}
				case INVENTORY -> {
					if (!(mob instanceof PmbInventoryHolder holder)) continue;
					int last = Math.min(source.lastSlot(), holder.pmb$getInventory().slots());
					for (int slot = source.firstSlot(); slot <= last; slot++) {
						ItemStack stack = holder.pmb$getInventory().get(slot);
						if (supported.test(stack)) return new Source(null, slot, stack);
					}
				}
			}
		}
		return null;
	}
}
