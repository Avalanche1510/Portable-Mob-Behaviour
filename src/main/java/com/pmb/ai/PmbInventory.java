package com.pmb.ai;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

public final class PmbInventory {
	public static final String TAG = "PmbInventory";
	public static final int MAX_SLOTS = 27;
	private static final float DEFAULT_DROP_CHANCE = 0.5F;

	private record StoredItem(int slot, ItemStack stack) {
		private static final Codec<StoredItem> CODEC = RecordCodecBuilder.create(instance -> instance.group(
				Codec.INT.fieldOf("Slot").forGetter(StoredItem::slot),
				ItemStack.MAP_CODEC.forGetter(StoredItem::stack)).apply(instance, StoredItem::new));
	}

	private int slots;
	private float dropChance = DEFAULT_DROP_CHANCE;
	private final SimpleContainer items = new SimpleContainer(MAX_SLOTS);

	public int slots() { return slots; }
	public float dropChance() { return dropChance; }
	public ItemStack get(int publicSlot) {
		return publicSlot >= 1 && publicSlot <= slots ? items.getItem(publicSlot - 1) : ItemStack.EMPTY;
	}
	public void set(int publicSlot, ItemStack stack) {
		if (publicSlot >= 1 && publicSlot <= slots) items.setItem(publicSlot - 1, stack);
	}
	public ItemStack add(ItemStack stack) {
		if (slots <= 0 || stack.isEmpty()) return stack;
		SimpleContainer enabled = new SimpleContainer(slots);
		for (int i = 0; i < slots; i++) enabled.setItem(i, items.getItem(i));
		ItemStack remainder = enabled.addItem(stack);
		for (int i = 0; i < slots; i++) items.setItem(i, enabled.getItem(i));
		return remainder;
	}

	public void read(Mob owner, ValueInput root) {
		items.clearContent();
		var child = root.child(TAG);
		if (child.isEmpty()) { slots = 0; dropChance = DEFAULT_DROP_CHANCE; return; }
		ValueInput input = child.get();
		slots = Mth.clamp(input.getIntOr("Slots", 0), 0, MAX_SLOTS);
		float loadedDropChance = input.getFloatOr("DropChance", DEFAULT_DROP_CHANCE);
		dropChance = Float.isFinite(loadedDropChance)
				? Mth.clamp(loadedDropChance, 0.0F, 1.0F) : DEFAULT_DROP_CHANCE;
		List<ItemStack> overflow = new ArrayList<>();
		for (StoredItem stored : input.listOrEmpty("Items", StoredItem.CODEC)) {
			if (stored.slot() < 1 || stored.slot() > MAX_SLOTS || stored.stack().isEmpty()) continue;
			int index = stored.slot() - 1;
			if (index < slots && items.getItem(index).isEmpty()) items.setItem(index, stored.stack());
			else overflow.add(stored.stack());
		}
		for (ItemStack stack : overflow) {
			ItemStack remainder = add(stack);
			if (!remainder.isEmpty() && owner.level() instanceof ServerLevel level) drop(level, owner, remainder);
		}
	}

	public void write(ValueOutput root) {
		if (slots <= 0 && items.isEmpty()) return;
		ValueOutput output = root.child(TAG);
		output.putInt("Slots", slots);
		output.putFloat("DropChance", dropChance);
		ValueOutput.TypedOutputList<StoredItem> stored = output.list("Items", StoredItem.CODEC);
		for (int i = 0; i < MAX_SLOTS; i++) {
			ItemStack stack = items.getItem(i);
			if (!stack.isEmpty()) stored.add(new StoredItem(i + 1, stack));
		}
	}

	public void dropOnDeath(ServerLevel level, Mob owner, boolean doMobLoot) {
		for (int i = 0; i < MAX_SLOTS; i++) {
			ItemStack stack = items.removeItemNoUpdate(i);
			if (doMobLoot && !stack.isEmpty() && owner.getRandom().nextFloat() < dropChance) drop(level, owner, stack);
		}
	}

	public void copyTo(PmbInventory target) {
		target.slots = slots;
		target.dropChance = dropChance;
		target.items.clearContent();
		for (int i = 0; i < MAX_SLOTS; i++) target.items.setItem(i, items.getItem(i).copy());
	}

	private static void drop(ServerLevel level, Mob owner, ItemStack stack) {
		ItemEntity entity = new ItemEntity(level, owner.getX(), owner.getY(), owner.getZ(), stack.copy());
		level.addFreshEntity(entity);
	}
}
