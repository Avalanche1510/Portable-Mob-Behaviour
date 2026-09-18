package com.pmb.ai;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

public final class PmbInventory {
	public static final String TAG = "PmbInventory";
	public static final int MAX_SLOTS = 256;
	private static final float DEFAULT_DROP_CHANCE = 0.5F;

	private record StoredItem(int slot, ItemStack stack) {
		private static final Codec<StoredItem> CODEC = RecordCodecBuilder.create(instance -> instance.group(
				Codec.INT.fieldOf("Slot").forGetter(StoredItem::slot),
				ItemStack.MAP_CODEC.forGetter(StoredItem::stack)).apply(instance, StoredItem::new));
	}
	record DeathDrop(ItemStack stack, boolean scattered) {}

	private int slots;
	private float dropChance = DEFAULT_DROP_CHANCE;
	private boolean scatterDrops;
	private SimpleContainer items = new SimpleContainer(0);

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
		return items.addItem(stack);
	}

	public void read(Mob owner, ValueInput root) {
		read(root, stack -> {
			if (owner.level() instanceof ServerLevel level) drop(level, owner, stack);
		});
	}

	void read(ValueInput root, Consumer<ItemStack> overflowDrop) {
		var child = root.child(TAG);
		if (child.isEmpty()) {
			resize(0);
			dropChance = DEFAULT_DROP_CHANCE;
			scatterDrops = false;
			return;
		}
		ValueInput input = child.get();
		resize(Mth.clamp(input.getIntOr("Slots", 0), 0, MAX_SLOTS));
		float loadedDropChance = input.getFloatOr("DropChance", DEFAULT_DROP_CHANCE);
		dropChance = Float.isFinite(loadedDropChance)
				? Mth.clamp(loadedDropChance, 0.0F, 1.0F) : DEFAULT_DROP_CHANCE;
		scatterDrops = input.getBooleanOr("ScatterDrops", false);
		List<ItemStack> overflow = new ArrayList<>();
		for (StoredItem stored : input.listOrEmpty("Items", StoredItem.CODEC)) {
			if (stored.slot() < 1 || stored.slot() > MAX_SLOTS || stored.stack().isEmpty()) continue;
			int index = stored.slot() - 1;
			if (index < slots && items.getItem(index).isEmpty()) items.setItem(index, stored.stack());
			else overflow.add(stored.stack());
		}
		for (ItemStack stack : overflow) {
			ItemStack remainder = add(stack);
			if (!remainder.isEmpty()) overflowDrop.accept(remainder);
		}
	}

	public void write(ValueOutput root) {
		if (slots <= 0 && items.isEmpty()) return;
		ValueOutput output = root.child(TAG);
		output.putInt("Slots", slots);
		output.putFloat("DropChance", dropChance);
		output.putBoolean("ScatterDrops", scatterDrops);
		ValueOutput.TypedOutputList<StoredItem> stored = output.list("Items", StoredItem.CODEC);
		for (int i = 0; i < items.getContainerSize(); i++) {
			ItemStack stack = items.getItem(i);
			if (!stack.isEmpty()) stored.add(new StoredItem(i + 1, stack));
		}
	}

	public void dropOnDeath(ServerLevel level, Mob owner, boolean doMobLoot) {
		for (DeathDrop deathDrop : drainDeathDrops(owner.getRandom(), doMobLoot)) {
			if (deathDrop.scattered()) owner.drop(deathDrop.stack().copy(), true, false);
			else drop(level, owner, deathDrop.stack());
		}
	}

	List<DeathDrop> drainDeathDrops(RandomSource random, boolean doMobLoot) {
		List<DeathDrop> drops = new ArrayList<>();
		for (int i = 0; i < items.getContainerSize(); i++) {
			ItemStack stack = items.removeItemNoUpdate(i);
			if (doMobLoot && !stack.isEmpty() && random.nextFloat() < dropChance)
				drops.add(new DeathDrop(stack, usesScatteredDeathDrops()));
		}
		return drops;
	}

	public void copyTo(PmbInventory target) {
		target.resize(slots);
		target.dropChance = dropChance;
		target.scatterDrops = scatterDrops;
		for (int i = 0; i < items.getContainerSize(); i++) target.items.setItem(i, items.getItem(i).copy());
	}

	boolean usesScatteredDeathDrops() { return scatterDrops; }
	int capacity() { return items.getContainerSize(); }

	private void resize(int capacity) {
		slots = capacity;
		items = new SimpleContainer(capacity);
	}

	private static void drop(ServerLevel level, Mob owner, ItemStack stack) {
		ItemEntity entity = new ItemEntity(level, owner.getX(), owner.getY(), owner.getZ(), stack.copy());
		level.addFreshEntity(entity);
	}
}
