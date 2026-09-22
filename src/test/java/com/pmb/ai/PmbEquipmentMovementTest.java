package com.pmb.ai;

import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.SharedConstants;
import net.minecraft.core.RegistryAccess;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TextColor;
import net.minecraft.server.Bootstrap;
import net.minecraft.util.ProblemReporter;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;
import net.minecraft.world.level.storage.ValueInput;
import sun.misc.Unsafe;

/**
 * Dependency-free regression executable. The allocated fixture bypasses world construction
 * and overrides only item storage; production resolution, exchanges and movement arbitration run unchanged.
 * This does not test Minecraft AI timing or Mixin application.
 */
public final class PmbEquipmentMovementTest {
	private static int checks;
	public static void main(String[] args) throws Exception {
		SharedConstants.tryDetectVersion();
		Bootstrap.bootStrap();
		// Only these fixture items are used; no datapack/world component loading is involved.
		for (var item : List.of(Items.ARROW, Items.BOW, Items.STICK, Items.DIAMOND))
			item.builtInRegistryHolder().bindComponents(net.minecraft.core.component.DataComponentMap.builder()
					.set(net.minecraft.core.component.DataComponents.MAX_STACK_SIZE, 64).build());
		testEquipment();
		testInventoryScatterDrops();
		testInventoryCapacityAndSources();
		testMovement();
		testDebugCaptureGate();
		testSchema();
		testSkillPriorities();
		testBowMeleeSuppressionRules();
		testSkillTimingAndPreClaim();
		testPriorityPreemptionHarness();
		testSkillDebugDiagnostics();
		testFactionCompatCodec();
		testWindChargeNeutralFallbackPredicate();
		testParameterSuggestions();
		testPriorityArgument();
		System.out.println("PASSED " + checks + " equipment/movement/schema assertions");
	}
	private static void testInventoryScatterDrops() throws Exception {
		PmbInventory inventory = new PmbInventory();
		inventory.read(fixture(), TagValueInput.create(ProblemReporter.DISCARDING, RegistryAccess.EMPTY,
				new CompoundTag()));
		check(!inventory.usesScatteredDeathDrops(), "missing inventory root defaults ScatterDrops false");

		CompoundTag missingField = new CompoundTag();
		CompoundTag missingFieldInventory = new CompoundTag();
		missingFieldInventory.putInt("Slots", 1);
		missingField.put(PmbInventory.TAG, missingFieldInventory);
		inventory.read(fixture(), TagValueInput.create(ProblemReporter.DISCARDING, RegistryAccess.EMPTY, missingField));
		check(!inventory.usesScatteredDeathDrops(), "missing ScatterDrops field defaults false");

		for (boolean value : List.of(false, true)) {
			CompoundTag root = new CompoundTag();
			CompoundTag storedInventory = new CompoundTag();
			storedInventory.putInt("Slots", 1);
			storedInventory.putBoolean("ScatterDrops", value);
			root.put(PmbInventory.TAG, storedInventory);
			inventory.read(fixture(), TagValueInput.create(ProblemReporter.DISCARDING, RegistryAccess.EMPTY, root));
			check(inventory.usesScatteredDeathDrops() == value, "explicit ScatterDrops value reads");

			TagValueOutput output = TagValueOutput.createWithoutContext(ProblemReporter.DISCARDING);
			inventory.write(output);
			check(output.buildResult().getCompound(PmbInventory.TAG).orElseThrow()
					.getBoolean("ScatterDrops").orElseThrow() == value, "ScatterDrops value writes");

			PmbInventory copied = new PmbInventory();
			inventory.copyTo(copied);
			check(copied.usesScatteredDeathDrops() == value, "ScatterDrops value copies");
		}

		PmbInventory omitted = new PmbInventory();
		field(PmbInventory.class, "scatterDrops").setBoolean(omitted, true);
		TagValueOutput emptyOutput = TagValueOutput.createWithoutContext(ProblemReporter.DISCARDING);
		omitted.write(emptyOutput);
		check(!emptyOutput.buildResult().contains(PmbInventory.TAG),
				"empty zero-slot inventory keeps root omission behavior");
		check(!new PmbInventory().usesScatteredDeathDrops(),
				"default death-drop branch remains stationary");
		check(omitted.usesScatteredDeathDrops(), "enabled death-drop branch selects player-style scatter");
	}
	private static void testInventoryCapacityAndSources() throws Exception {
		PmbInventory empty = new PmbInventory();
		check(empty.slots() == 0 && empty.capacity() == 0,
				"zero-slot inventory allocates no backing slots");
		for (int capacity : List.of(27, 128, 255, 256)) {
			PmbInventory inventory = inventory(capacity);
			int count = capacity == 256 ? 16 : capacity == 255 ? 15 : capacity == 128 ? 8 : 7;
			inventory.set(capacity, new ItemStack(Items.ARROW, count));
			PmbInventory loaded = roundTrip(inventory);
			check(loaded.slots() == capacity && loaded.capacity() == capacity,
					"inventory round trip preserves dynamic capacity " + capacity);
			check(loaded.get(capacity).is(Items.ARROW) && loaded.get(capacity).getCount() == count,
					"inventory round trip preserves highest slot item and count " + capacity);
		}

		PmbInventory clamped = inventoryFromRoot(inventoryRoot(257));
		check(clamped.slots() == 256 && clamped.capacity() == 256,
				"Slots 257 clamps to 256 dynamic backing slots");

		PmbInventory slotDonor = inventory(256);
		slotDonor.set(256, new ItemStack(Items.DIAMOND));
		CompoundTag invalidSlotRoot = writeInventory(slotDonor);
		invalidSlotRoot.getCompoundOrEmpty(PmbInventory.TAG).getListOrEmpty("Items")
				.getCompoundOrEmpty(0).putInt("Slot", 257);
		PmbInventory invalidSlot = inventoryFromRoot(invalidSlotRoot);
		check(invalidSlot.get(256).isEmpty(), "external Slot 257 is ignored");

		PmbInventory shrinkDonor = inventory(256);
		shrinkDonor.set(1, new ItemStack(Items.ARROW, 60));
		shrinkDonor.set(256, new ItemStack(Items.ARROW, 4));
		CompoundTag shrinkRoot = writeInventory(shrinkDonor);
		shrinkRoot.getCompoundOrEmpty(PmbInventory.TAG).putInt("Slots", 1);
		PmbInventory shrunk = inventoryFromRoot(shrinkRoot);
		check(shrunk.capacity() == 1 && shrunk.get(1).getCount() == 64,
				"capacity reduction merges high-slot contents into enabled low slots");
		check(shrunk.get(256).isEmpty(), "capacity reduction exposes no disabled high slot");
		ItemStack remainder = shrunk.add(new ItemStack(Items.DIAMOND, 3));
		check(remainder.is(Items.DIAMOND) && remainder.getCount() == 3,
				"full resized inventory preserves the complete overflow remainder");

		PmbInventory highPickup = inventory(256);
		for (int slot = 1; slot <= 27; slot++) highPickup.set(slot, new ItemStack(Items.ARROW, 64));
		check(highPickup.add(new ItemStack(Items.DIAMOND, 5)).isEmpty()
				&& highPickup.get(28).is(Items.DIAMOND) && highPickup.get(28).getCount() == 5,
				"pickup-style add continues into slots above 27");
		for (int slot = 28; slot <= 256; slot++) highPickup.set(slot, new ItemStack(Items.DIAMOND, 64));
		ItemStack highRemainder = highPickup.add(new ItemStack(Items.STICK, 2));
		check(highRemainder.is(Items.STICK) && highRemainder.getCount() == 2,
				"full 256-slot pickup preserves its complete remainder");

		PmbInventory overflowDonor = inventory(256);
		overflowDonor.set(1, new ItemStack(Items.ARROW, 64));
		overflowDonor.set(256, new ItemStack(Items.DIAMOND, 7));
		CompoundTag overflowRoot = writeInventory(overflowDonor);
		overflowRoot.getCompoundOrEmpty(PmbInventory.TAG).putInt("Slots", 1);
		List<ItemStack> overflowDrops = new ArrayList<>();
		PmbInventory overflowLoaded = new PmbInventory();
		overflowLoaded.read(TagValueInput.create(ProblemReporter.DISCARDING, RegistryAccess.EMPTY, overflowRoot),
				overflowDrops::add);
		check(overflowLoaded.get(1).is(Items.ARROW) && overflowLoaded.get(1).getCount() == 64
				&& overflowDrops.size() == 1 && overflowDrops.getFirst().is(Items.DIAMOND)
				&& overflowDrops.getFirst().getCount() == 7,
				"load shrink sends the exact unmerged high-slot remainder to overflow dropping");

		PmbInventory duplicateDonor = inventory(2);
		duplicateDonor.set(1, new ItemStack(Items.ARROW, 60));
		duplicateDonor.set(2, new ItemStack(Items.ARROW, 4));
		CompoundTag duplicateRoot = writeInventory(duplicateDonor);
		duplicateRoot.getCompoundOrEmpty(PmbInventory.TAG).getListOrEmpty("Items")
				.getCompoundOrEmpty(1).putInt("Slot", 1);
		PmbInventory duplicateLoaded = inventoryFromRoot(duplicateRoot);
		check(duplicateLoaded.get(1).is(Items.ARROW) && duplicateLoaded.get(1).getCount() == 64
				&& duplicateLoaded.get(2).isEmpty(),
				"duplicate Slot entries retain the first position and merge later contents through overflow");

		PmbInventory zeroSlotDonor = inventory(1);
		zeroSlotDonor.set(1, new ItemStack(Items.DIAMOND, 6));
		CompoundTag zeroSlotRoot = writeInventory(zeroSlotDonor);
		zeroSlotRoot.getCompoundOrEmpty(PmbInventory.TAG).getListOrEmpty("Items")
				.getCompoundOrEmpty(0).putInt("Slot", 0);
		PmbInventory zeroSlotLoaded = inventoryFromRoot(zeroSlotRoot);
		check(zeroSlotLoaded.get(1).isEmpty(), "explicit external Slot 0 is ignored");

		PmbActivationSources activation = new PmbActivationSources();
		activation.read(sourceInput(PmbActivationSources.FIELD,
				"inventory", "inventory:256", "inventory:1..256", "inventory:0", "inventory:257", "inventory:9..7"));
		check(activation.sources().equals(List.of(
				new PmbActivationSources.Source(PmbActivationSources.Kind.INVENTORY, 1, 256),
				new PmbActivationSources.Source(PmbActivationSources.Kind.INVENTORY, 256, 256),
				new PmbActivationSources.Source(PmbActivationSources.Kind.INVENTORY, 1, 256))),
				"FetchSource accepts 256 and rejects zero, 257, and reversed ranges");
		TagValueOutput activationOutput = TagValueOutput.createWithoutContext(ProblemReporter.DISCARDING);
		activation.write(activationOutput);
		check(activationOutput.buildResult().toString().contains("\"inventory\""),
				"full FetchSource range formats as inventory alias");

		PmbAmmoSources ammunition = new PmbAmmoSources();
		ammunition.read(sourceInput(PmbAmmoSources.FIELD,
				"inventory", "inventory:256", "inventory:1..256", "inventory:0", "inventory:257", "inventory:9..7"));
		check(ammunition.sources().equals(activation.sources()),
				"AmmoSource shares the 1..256 validation and inventory alias");
		check(PmbSkillSchema.validSource("inventory:256") && PmbSkillSchema.validSource("inventory:1..256")
				&& !PmbSkillSchema.validSource("inventory:0") && !PmbSkillSchema.validSource("inventory:257")
				&& !PmbSkillSchema.validSource("inventory:9..7"),
				"command schema enforces the expanded source range");

		Fixture mob = fixture();
		mob.inventory = inventory(256);
		mob.inventory.set(256, new ItemStack(Items.ARROW, 2));
		PmbAmmoSources slot256Ammo = new PmbAmmoSources();
		slot256Ammo.read(sourceInput(PmbAmmoSources.FIELD, "inventory:256"));
		PmbAmmoAccess.Source found = PmbAmmoAccess.find(mob, slot256Ammo, stack -> stack.is(Items.ARROW));
		check(found != null && found.inventorySlot() == 256 && found.consume(mob)
				&& mob.inventory.get(256).getCount() == 1,
				"slot 256 ammunition is found and consumed from its real slot");

		PmbInventory converted = new PmbInventory();
		mob.inventory.copyTo(converted);
		check(converted.capacity() == 256 && converted.get(256).getCount() == 1,
				"conversion copy allocates and preserves slot 256");

		mob.items.put(InteractionHand.MAIN_HAND, new ItemStack(Items.STICK));
		mob.inventory.set(256, new ItemStack(Items.BOW));
		PmbActivationSources slot256Fetch = sources(
				new PmbActivationSources.Source(PmbActivationSources.Kind.INVENTORY, 256, 256));
		PmbSkillItemAccess.Resolved slot256Resolved = resolve(mob, slot256Fetch, PmbPreferredHand.MAIN);
		PmbSkillItemAccess.ActionBinding slot256Binding = PmbSkillItemAccess.acquire(mob, slot256Resolved);
		check(slot256Binding != null && slot256Binding.inventorySlot() == 256
				&& mob.getMainHandItem().is(Items.BOW) && mob.inventory.get(256).is(Items.STICK),
				"FetchSource acquires slot 256 through the permanent inventory swap path");

		PmbInventory death = configuredInventory(256, 1.0F, false);
		death.set(1, new ItemStack(Items.ARROW, 2));
		death.set(256, new ItemStack(Items.DIAMOND, 7));
		List<PmbInventory.DeathDrop> deathDrops = death.drainDeathDrops(RandomSource.create(2008L), true);
		check(deathDrops.size() == 2
				&& deathDrops.get(0).stack().is(Items.ARROW) && deathDrops.get(0).stack().getCount() == 2
				&& deathDrops.get(1).stack().is(Items.DIAMOND) && deathDrops.get(1).stack().getCount() == 7
				&& deathDrops.stream().noneMatch(PmbInventory.DeathDrop::scattered)
				&& death.get(1).isEmpty() && death.get(256).isEmpty(),
				"death processing reaches slot 256 and preserves each whole stack in stationary mode");
		PmbInventory scatteredDeath = configuredInventory(256, 1.0F, true);
		scatteredDeath.set(256, new ItemStack(Items.DIAMOND, 9));
		List<PmbInventory.DeathDrop> scatteredDrops = scatteredDeath.drainDeathDrops(RandomSource.create(2008L), true);
		check(scatteredDrops.size() == 1 && scatteredDrops.getFirst().scattered()
				&& scatteredDrops.getFirst().stack().getCount() == 9,
				"slot 256 death processing preserves whole-stack scattered branch selection");
		PmbInventory suppressedDeath = configuredInventory(256, 1.0F, true);
		suppressedDeath.set(256, new ItemStack(Items.DIAMOND, 11));
		check(suppressedDeath.drainDeathDrops(RandomSource.create(2008L), false).isEmpty()
				&& suppressedDeath.get(256).isEmpty(),
				"doMobLoot false still drains slot 256 without producing a death drop");
	}

	private static PmbInventory inventory(int slots) throws Exception {
		return inventoryFromRoot(inventoryRoot(slots));
	}
	private static PmbInventory configuredInventory(int slots, float dropChance, boolean scatterDrops) throws Exception {
		CompoundTag root = inventoryRoot(slots);
		CompoundTag child = root.getCompoundOrEmpty(PmbInventory.TAG);
		child.putFloat("DropChance", dropChance);
		child.putBoolean("ScatterDrops", scatterDrops);
		return inventoryFromRoot(root);
	}
	private static PmbInventory inventoryFromRoot(CompoundTag root) throws Exception {
		PmbInventory inventory = new PmbInventory();
		inventory.read(fixture(), TagValueInput.create(ProblemReporter.DISCARDING, RegistryAccess.EMPTY, root));
		return inventory;
	}
	private static CompoundTag inventoryRoot(int slots) {
		CompoundTag root = new CompoundTag();
		CompoundTag child = new CompoundTag();
		child.putInt("Slots", slots);
		root.put(PmbInventory.TAG, child);
		return root;
	}
	private static CompoundTag writeInventory(PmbInventory inventory) {
		TagValueOutput output = TagValueOutput.createWithoutContext(ProblemReporter.DISCARDING);
		inventory.write(output);
		return output.buildResult();
	}
	private static PmbInventory roundTrip(PmbInventory inventory) throws Exception {
		return inventoryFromRoot(writeInventory(inventory));
	}
	private static ValueInput sourceInput(String field, String... values) {
		CompoundTag root = new CompoundTag();
		ListTag list = new ListTag();
		for (String value : values) list.add(StringTag.valueOf(value));
		root.put(field, list);
		return TagValueInput.create(ProblemReporter.DISCARDING, RegistryAccess.EMPTY, root);
	}
	private static void testEquipment() throws Exception {
		Fixture mob = fixture();
		PmbActivationSources sources = sources(new PmbActivationSources.Source(PmbActivationSources.Kind.INVENTORY, 1, 3));
		mob.items.put(InteractionHand.MAIN_HAND, new ItemStack(Items.ARROW, 4));
		mob.inventory.set(1, new ItemStack(Items.BOW));
		var arrow = PmbAmmoAccess.find(mob, stack -> stack.is(Items.ARROW));
		var resolved = resolve(mob, sources, PmbPreferredHand.MAIN);
		check(resolved.inventorySlot() == 1, "first configured inventory slot");
		var action = PmbSkillItemAccess.acquire(mob, resolved);
		arrow.followEquipmentSwap(action);
		check(mob.getMainHandItem().is(Items.BOW) && mob.inventory.get(1).is(Items.ARROW), "permanent inventory exchange");
		check(arrow.inventorySlot() == 1 && arrow.matches(mob), "arrow follows displaced stack");
		check(arrow.consume(mob) && mob.inventory.get(1).getCount() == 3, "mapped inventory arrow consumed exactly once");
		mob.scheduler.bind("bow", action);
		mob.scheduler.release(mob, "bow");
		check(mob.getMainHandItem().is(Items.BOW), "ending action does not restore");
		check(resolve(mob, sources(), PmbPreferredHand.MAIN).transfer() == PmbSkillItemAccess.Transfer.DIRECT,
				"destination bow bypasses empty FetchSource");
		mob.items.put(InteractionHand.MAIN_HAND, new ItemStack(Items.ARROW, 4));
		mob.items.put(InteractionHand.OFF_HAND, new ItemStack(Items.BOW));
		arrow = PmbAmmoAccess.find(mob, stack -> stack.is(Items.ARROW));
		var hands = sources(new PmbActivationSources.Source(PmbActivationSources.Kind.OFF_HAND, 0, 0));
		resolved = resolve(mob, hands, PmbPreferredHand.MAIN);
		check(resolved.resources().length == 2, "cross-hand swap claims both hands");
		action = PmbSkillItemAccess.acquire(mob, resolved);
		arrow.followEquipmentSwap(action);
		check(arrow.hand() == InteractionHand.OFF_HAND && arrow.matches(mob), "arrow follows cross-hand exchange");
		check(arrow.consume(mob) && mob.getOffhandItem().getCount() == 3, "mapped offhand arrow consumed exactly once");
		mob.items.put(InteractionHand.MAIN_HAND, new ItemStack(Items.STICK));
		mob.inventory.set(1, new ItemStack(Items.BOW));
		resolved = resolve(mob, sources, PmbPreferredHand.MAIN);
		mob.inventory.set(1, new ItemStack(Items.DIAMOND));
		check(PmbSkillItemAccess.acquire(mob, resolved) == null && mob.getMainHandItem().is(Items.STICK)
				&& mob.inventory.get(1).is(Items.DIAMOND), "changed source cannot be overwritten");
		mob.inventory.set(1, new ItemStack(Items.BOW));
		resolved = resolve(mob, sources, PmbPreferredHand.MAIN);
		mob.items.put(InteractionHand.MAIN_HAND, new ItemStack(Items.DIAMOND));
		check(PmbSkillItemAccess.acquire(mob, resolved) == null && mob.inventory.get(1).is(Items.BOW),
				"changed destination cannot be overwritten");
		@SuppressWarnings("unchecked") Map<PmbSkillScheduler.Resource, String> sustained =
				(Map<PmbSkillScheduler.Resource, String>) field(PmbSkillScheduler.class, "sustained").get(mob.scheduler);
		sustained.put(PmbSkillScheduler.Resource.MAIN_HAND, "shield");
		check(resolve(mob, sources, PmbPreferredHand.MAIN).hand() == InteractionHand.OFF_HAND, "ongoing owner triggers fallback");
		check(resolve(mob, sources, PmbPreferredHand.MAIN_ENFORCE) == null, "enforce rejects occupied destination");
		mob.items.put(InteractionHand.MAIN_HAND, new ItemStack(Items.BOW));
		var ordered = sources(new PmbActivationSources.Source(PmbActivationSources.Kind.MAIN_HAND, 0, 0),
				new PmbActivationSources.Source(PmbActivationSources.Kind.INVENTORY, 1, 3));
		check(resolve(mob, ordered, PmbPreferredHand.OFF).inventorySlot() == 1, "busy source skipped");
		sustained.clear();
		mob.scheduler.claim("other_candidate", PmbSkillScheduler.Resource.MAIN_HAND);
		check(resolve(mob, sources, PmbPreferredHand.MAIN).hand() == InteractionHand.MAIN_HAND,
				"same-tick competing claim does not change hand selection");

	}
	private static void testParameterSuggestions() {
		var argument = new com.pmb.command.PmbSkillParametersArgument("bow", false);
		String prefix = "pmb skills @s bow insert ";
		var empty = argument.listSuggestions(null,
				new com.mojang.brigadier.suggestion.SuggestionsBuilder(prefix, prefix.length())).join();
		check(empty.getList().size() == 1 && empty.getList().getFirst().getText().equals("[")
				&& empty.getList().getFirst().getRange().getStart() == prefix.length(),
				"empty parameter entry suggests only opening bracket at argument offset");
		for (String invalid : List.of("enable", " [enable="))
			check(argument.listSuggestions(null,
					new com.mojang.brigadier.suggestion.SuggestionsBuilder(prefix + invalid, prefix.length()))
					.join().isEmpty(), "non-bracket parameter prefix offers no suggestions");
		var values = argument.listSuggestions(null,
				new com.mojang.brigadier.suggestion.SuggestionsBuilder(prefix + "[enable=", prefix.length())).join();
		check(values.getList().stream().map(com.mojang.brigadier.suggestion.Suggestion::getText).toList()
				.equals(List.of("0b", "1b")), "bracketed boolean value suggestions preserved");
		var sourceValues = argument.listSuggestions(null,
				new com.mojang.brigadier.suggestion.SuggestionsBuilder(
						prefix + "[FetchSource=[\"inventory:2", prefix.length())).join();
		check(sourceValues.getList().stream().map(com.mojang.brigadier.suggestion.Suggestion::getText).toList()
				.contains("\"inventory:256\""), "expanded slot 256 is offered by source autocomplete");
	}
	private static void testPriorityArgument() throws Exception {
		var argument = new com.pmb.command.PmbPriorityListArgument();
		var parsed = argument.parse(new com.mojang.brigadier.StringReader(
				"[\"mace\",[\"ender_pearl\",\"bow\"],\"vanilla\"]"));
		check(parsed.equals(List.of(List.of("mace"), List.of("ender_pearl", "bow"), List.of("vanilla"))),
				"priority command parses strict mixed quoted list");
		for (String invalid : List.of("[mace,\"vanilla\"]", "[\"mace\"]",
				"[\"mace\",\"mace\",\"vanilla\"]", "[[],\"vanilla\"]")) {
			boolean rejected = false;
			try { argument.parse(new com.mojang.brigadier.StringReader(invalid)); }
			catch (com.mojang.brigadier.exceptions.CommandSyntaxException expected) { rejected = true; }
			check(rejected, "priority command rejects invalid input: " + invalid);
		}
		String prefix = "pmb skills priority @s set combat ";
		var opening = argument.listSuggestions(null,
				new com.mojang.brigadier.suggestion.SuggestionsBuilder(prefix, prefix.length())).join();
		check(opening.getList().stream().anyMatch(value -> value.getText().equals("[")),
				"priority argument suggests opening bracket");
		String outer = "[\"mace\",";
		var outerSuggestions = argument.listSuggestions(null,
				new com.mojang.brigadier.suggestion.SuggestionsBuilder(prefix + outer, prefix.length())).join();
		check(outerSuggestions.getList().stream().anyMatch(value -> value.getText().equals("[")),
				"outer priority element suggests an equal-tier group");
		String grouped = "[[\"mace\",";
		var groupSuggestions = argument.listSuggestions(null,
				new com.mojang.brigadier.suggestion.SuggestionsBuilder(prefix + grouped, prefix.length())).join();
		check(groupSuggestions.getList().stream().noneMatch(value -> value.getText().equals("["))
				&& groupSuggestions.getList().stream().anyMatch(value -> value.getText().equals("\"bow\"")),
				"equal-tier group suggests skills but never a nested group");
		check(outerSuggestions.getList().stream().anyMatch(value -> value.getText().equals("\"air_tracking\"")),
				"priority autocomplete derives and offers air_tracking");
	}
	private static void testMovement() throws Exception {
		Fixture mob = fixture(); var movement = mob.scheduler.movement(); List<String> trace = new ArrayList<>();
		field(PmbBowAiData.class, "configured").setBoolean(mob.ai.bow(), true);
		mob.tickCount = 20;
		movement.apply(mob, mob.scheduler);
		check(!movement.blocksVanillaBowMovement(mob), "no bow action leaves vanilla movement available");
		offer(mob, "bow", PmbMovementController.Type.LOCOMOTION, PmbMovementController.Tier.PASSIVE, 100, trace);
		offer(mob, "wind", PmbMovementController.Type.VELOCITY_MODIFIER, PmbMovementController.Tier.ACTIVE, 1, trace);
		offer(mob, "retreat", PmbMovementController.Type.LOCOMOTION, PmbMovementController.Tier.RETREAT, 0, trace);
		movement.apply(mob, mob.scheduler);
		check(trace.equals(List.of("retreat", "wind")), "retreat wins, velocity modifier remains additive");
		check(movement.snapshot().tick() == mob.tickCount
				&& "retreat".equals(movement.snapshot().locomotionOwner())
				&& movement.snapshot().velocityModifierOwners().equals(List.of("wind")),
				"movement debug snapshot records actual applied owners");
		trace.clear();
		offer(mob, "active", PmbMovementController.Type.LOCOMOTION, PmbMovementController.Tier.ACTIVE, 0, trace);
		offer(mob, "passive", PmbMovementController.Type.LOCOMOTION, PmbMovementController.Tier.PASSIVE, 999, trace);
		movement.apply(mob, mob.scheduler);
		check(trace.equals(List.of("active")), "active beats higher-rank passive movement");
		trace.clear();
		offer(mob, "stale", PmbMovementController.Type.LOCOMOTION, PmbMovementController.Tier.ACTIVE, 0, trace);
		mob.tickCount += 2; movement.apply(mob, mob.scheduler);
		check(trace.isEmpty(), "stale epoch is discarded");
		offer(mob, "cancelled", PmbMovementController.Type.LOCOMOTION, PmbMovementController.Tier.ACTIVE, 0, trace);
		movement.cancel("cancelled"); movement.apply(mob, mob.scheduler);
		check(trace.isEmpty(), "cancel removes pending movement");
		movement.submit(mob, "invalid", PmbMovementController.Type.LOCOMOTION, PmbMovementController.Tier.ACTIVE,
				PmbSkillScheduler.Category.MAIN, 0, () -> false, () -> trace.add("invalid"));
		movement.apply(mob, mob.scheduler);
		check(trace.isEmpty(), "invalid target predicate discarded");
		offer(mob, "stop", PmbMovementController.Type.HARD_STOP, PmbMovementController.Tier.PASSIVE, 0, trace);
		offer(mob, "retreat", PmbMovementController.Type.LOCOMOTION, PmbMovementController.Tier.RETREAT, 99, trace);
		offer(mob, "modifier", PmbMovementController.Type.VELOCITY_MODIFIER, PmbMovementController.Tier.ACTIVE, 0, trace);
		movement.apply(mob, mob.scheduler);
		check(trace.equals(List.of("stop")), "hard stop wins and suppresses velocity modifiers");
		trace.clear();
		Fixture oldTarget = fixture(), newTarget = fixture();
		mob.combat = oldTarget;
		movement.submit(mob, "old_wind", PmbMovementController.Type.VELOCITY_MODIFIER,
				PmbMovementController.Tier.ACTIVE, PmbSkillScheduler.Category.THROW, 1,
				() -> PmbMovementController.hasCurrentAuthority(mob, oldTarget, false), () -> trace.add("old"));
		mob.avoid = newTarget;
		movement.apply(mob, mob.scheduler);
		check(trace.isEmpty(), "retreat transition invalidates prior combat steering before skill tick");
		mob.avoid = null; mob.combat = newTarget;
		check(!PmbMovementController.hasCurrentAuthority(mob, oldTarget, false), "combat target change invalidates steering");
		mob.avoid = newTarget;
		check(!PmbMovementController.hasCurrentAuthority(mob, oldTarget, true), "avoid target change invalidates steering");
		mob.avoid = null;
		offer(mob, "wind", PmbMovementController.Type.VELOCITY_MODIFIER, PmbMovementController.Tier.ACTIVE, 0, trace);
		movement.apply(mob, mob.scheduler);
		check(!movement.blocksVanillaBowMovement(mob), "non-bow action cannot intercept vanilla bow movement");
		offer(mob, "bow", PmbMovementController.Type.LOCOMOTION, PmbMovementController.Tier.ACTIVE, 0, trace);
		movement.apply(mob, mob.scheduler);
		check(movement.blocksVanillaBowMovement(mob), "active bow movement winner enables interception");
		movement.cancel("bow");
		check(!movement.blocksVanillaBowMovement(mob), "cancelled winner cannot retain movement interception");
	}
	private static void testSkillDebugDiagnostics() throws Exception {
		PmbSkillScheduler scheduler = new PmbSkillScheduler();
		prepareDebug(scheduler, true);
		scheduler.claim("shield", PmbSkillScheduler.Resource.USE_ITEM);
		scheduler.offer("bow", "line", PmbSkillScheduler.Category.MAIN, 10, () -> false, () -> {},
				PmbSkillScheduler.Resource.MAIN_HAND);
		scheduler.offer("pearl", "throw", PmbSkillScheduler.Category.THROW, 20, () -> true, () -> {},
				PmbSkillScheduler.Resource.USE_ITEM);
		scheduler.offer("shield", "raise", PmbSkillScheduler.Category.OFF, 10, () -> true, () -> {},
				PmbSkillScheduler.Resource.OFF_HAND);
		resolveCandidates(scheduler);
		List<PmbSkillDebugSnapshot.CandidateAttempt> attempts = debugAttempts(scheduler);
		check(attempts.stream().anyMatch(a -> a.label().equals("line")
				&& a.status() == PmbSkillDebugSnapshot.AttemptStatus.PRECLAIM_REJECTED),
				"debug records failed pre-claim without re-running it");
		check(attempts.stream().anyMatch(a -> a.label().equals("throw")
				&& a.status() == PmbSkillDebugSnapshot.AttemptStatus.RESOURCE_BLOCKED
				&& a.blockedResource() == PmbSkillScheduler.Resource.USE_ITEM
				&& "shield".equals(a.blocker())), "debug records resource blocker and owner");
		check(attempts.stream().anyMatch(a -> a.label().equals("raise")
				&& a.status() == PmbSkillDebugSnapshot.AttemptStatus.COMMIT_FAILED),
				"admitted action without execution marker becomes commit failed");

		PmbSkillScheduler[] current = {new PmbSkillScheduler()};
		prepareDebug(current[0], true);
		current[0].offer("mace", "smash", PmbSkillScheduler.Category.MAIN, 20, () -> true,
				() -> current[0].markCurrentCandidateExecuted("mace"), PmbSkillScheduler.Resource.SMASH);
		resolveCandidates(current[0]);
		check(debugAttempts(current[0]).getFirst().status() == PmbSkillDebugSnapshot.AttemptStatus.EXECUTED,
				"matching winner execution marker is recorded");
		current[0] = new PmbSkillScheduler();
		prepareDebug(current[0], true);
		current[0].offer("mace", "smash", PmbSkillScheduler.Category.MAIN, 20, () -> true,
				() -> current[0].markCurrentCandidateExecuted("bow"), PmbSkillScheduler.Resource.SMASH);
		resolveCandidates(current[0]);
		check(debugAttempts(current[0]).getFirst().status() == PmbSkillDebugSnapshot.AttemptStatus.COMMIT_FAILED,
				"mismatched execution marker is safely ignored");

		PmbSkillScheduler.Resource[] mutable = {PmbSkillScheduler.Resource.LOOK};
		var immutable = PmbSkillDebugSnapshot.CandidateAttempt.admitted("wind", "throw",
				PmbSkillScheduler.Category.THROW, 10, mutable);
		mutable[0] = PmbSkillScheduler.Resource.SMASH;
		check(immutable.resources().equals(List.of(PmbSkillScheduler.Resource.LOOK)),
				"candidate diagnostic copies mutable resource arrays");

		UUID debugUuid = UUID.randomUUID();
		var debugSnapshot = new PmbSkillDebugSnapshot(42, debugUuid, "minecraft:husk", 100,
				PmbSkillScheduler.Strategy.COMBAT, UUID.randomUUID(),
				List.of(new PmbSkillDebugSnapshot.SkillState("bow", true, true, true,
						"line=7,arc=11", "-"),
						new PmbSkillDebugSnapshot.SkillState("wind_charge", true, true, false,
								"throw=0,bounce=0", "tracking=infinite,lastClearReason=active"),
						new PmbSkillDebugSnapshot.SkillState("shield", true, false, false, "check=0", "-"),
						new PmbSkillDebugSnapshot.SkillState("mace", false, false, false, "smash=0", "-")),
				List.of(PmbSkillDebugSnapshot.CandidateAttempt.resourceBlocked("bow", "line",
						PmbSkillScheduler.Category.MAIN, 10,
						new PmbSkillScheduler.Resource[] {PmbSkillScheduler.Resource.USE_ITEM},
						PmbSkillScheduler.Resource.USE_ITEM, "shield"),
						PmbSkillDebugSnapshot.CandidateAttempt.admitted("mace", "smash",
								PmbSkillScheduler.Category.MAIN, 20, new PmbSkillScheduler.Resource[0])
								.commitFailed(true)),
				Map.of(), Map.of(PmbSkillScheduler.Resource.USE_ITEM, "shield"),
				Map.of("bow", "hand=MAIN_HAND"), null, true, List.of("bow"), false, "-");
		var formatted = debugSnapshot.formatForLog(103);
		check(formatted.startsWith("PMB SKILL DEBUG\nENTITY: minecraft:husk#42 uuid=" + debugUuid),
				"skill debug log formatter starts with prominent entity identity");
		check(formatted.contains("\nSKILLS:\n  bow [ACTIVE] cooldown{line=7,arc=11}")
				&& formatted.contains("wind_charge [READY]")
				&& formatted.contains("tracking=infinite,lastClearReason=active")
				&& !formatted.contains("shield [") && !formatted.contains("mace ["),
				"skill debug log shows only enabled skills and highlights active state");
		check(formatted.contains("\nLAST ATTEMPTS:\n  bow/line [RESOURCE_BLOCKED]")
				&& formatted.contains("mace/smash [COMMIT_FAILED_AFTER_PREEMPT]")
				&& formatted.contains("\nRESOURCES:\n  sustained={}\n  current={USE_ITEM=shield}")
				&& formatted.contains("\nBINDINGS:\n  bow=hand=MAIN_HAND")
				&& formatted.contains("\nMELEE: SUPPRESSED reasons=[bow]")
				&& formatted.contains("\nCONTEXT:\n  snapshotTick=100 ageTicks=3 strategy=COMBAT"),
				"skill debug log formatter keeps ordered diagnostic sections and key fields");
		check(inOrder(formatted, "\nENTITY:", "\nMELEE:", "\nSKILLS:", "\nLAST ATTEMPTS:",
				"\nRESOURCES:", "\nBINDINGS:", "\nCONTEXT:"),
				"skill debug log puts melee warning immediately after identity before skill diagnostics");

		var emptySnapshot = new PmbSkillDebugSnapshot(7, UUID.randomUUID(), "minecraft:zombie", 1,
				PmbSkillScheduler.Strategy.IDLE, null,
				List.of(new PmbSkillDebugSnapshot.SkillState("bow", true, false, false, "line=0", "-")),
				List.of(), Map.of(), Map.of(), Map.of(), null, false, List.of(), false, "-");
		String empty = emptySnapshot.formatForLog(1);
		check(empty.contains("\nSKILLS: []\nLAST ATTEMPTS: []\nRESOURCES: {}\nBINDINGS: []"),
				"empty debug sections remain visible with exact compact markers");

		Component chat = debugSnapshot.formatForChat(103);
		String chatText = chat.getString();
		check(chatText.contains("debug.portable-mob-behaviour.skills")
				&& chatText.contains("debug.portable-mob-behaviour.status.resource_blocked"),
				"chat formatter retains translatable keys for client localization");
		check(inOrder(chatText, "debug.portable-mob-behaviour.entity",
				"debug.portable-mob-behaviour.melee", "debug.portable-mob-behaviour.skills",
				"debug.portable-mob-behaviour.attempts", "debug.portable-mob-behaviour.resources",
				"debug.portable-mob-behaviour.bindings", "debug.portable-mob-behaviour.context"),
				"localized chat puts melee warning immediately after identity before skill diagnostics");
		check(containsColor(chat, ChatFormatting.GOLD) && containsColor(chat, ChatFormatting.GREEN)
				&& containsColor(chat, ChatFormatting.RED),
				"chat formatter applies title, active, and blocked/suppressed status colors");
		Component unavailable = PmbSkillDebugFormatter.formatUnavailableForChat(
				"minecraft:husk", 42, debugUuid);
		check(unavailable.getString().contains("debug.portable-mob-behaviour.snapshot.unavailable"),
				"unavailable chat report remains localizable");

		var buffer = new net.minecraft.network.FriendlyByteBuf(io.netty.buffer.Unpooled.buffer());
		try {
			var request = new com.pmb.network.PmbProtocolPackets.SkillDebugRequest(2008);
			com.pmb.network.PmbProtocolPackets.SkillDebugRequest.CODEC.encode(buffer, request);
			check(com.pmb.network.PmbProtocolPackets.SkillDebugRequest.CODEC.decode(buffer).entityId() == 2008,
					"skill debug request varint codec round trip");
		} finally { buffer.release(); }
	}
	private static boolean containsColor(Component component, ChatFormatting formatting) {
		TextColor expected = TextColor.fromLegacyFormat(formatting);
		if (expected.equals(component.getStyle().getColor())) return true;
		return component.getSiblings().stream().anyMatch(child -> containsColor(child, formatting));
	}
	private static boolean inOrder(String text, String... markers) {
		int previous = -1;
		for (String marker : markers) {
			int current = text.indexOf(marker);
			if (current <= previous) return false;
			previous = current;
		}
		return true;
	}
	private static void testDebugCaptureGate() throws Exception {
		Fixture mob = fixture();
		for (int i = 0; i < 3; i++) mob.scheduler.movement().apply(mob, mob.scheduler);
		check(mob.scheduler.movement().snapshot() == null,
				"unconfigured mob does not allocate or retain a movement debug snapshot");

		PmbSkillScheduler scheduler = new PmbSkillScheduler();
		int[] calls = {0, 0, 0};
		field(PmbSkillScheduler.class, "strategy").set(scheduler, PmbSkillScheduler.Strategy.COMBAT);
		scheduler.offerDynamic("bow", "plain", PmbSkillScheduler.Category.MAIN, 1,
				() -> { calls[0]++; return true; }, () -> calls[2]++,
				() -> { calls[1]++; return new PmbSkillScheduler.Resource[] {PmbSkillScheduler.Resource.LOOK}; });
		resolveCandidates(scheduler);
		check(java.util.Arrays.equals(calls, new int[] {1, 1, 1}),
				"unconfigured scheduler executes production pre-claim, resource and winner callbacks exactly once");
		check(field(PmbSkillScheduler.class, "candidateAttempts").get(scheduler) == null,
				"unconfigured scheduler does not allocate candidate diagnostics");

		field(PmbBowAiData.class, "configured").setBoolean(mob.ai.bow(), true);
		mob.scheduler.movement().apply(mob, mob.scheduler);
		check(mob.scheduler.movement().snapshot() != null,
				"configured transition enables movement diagnostic capture");
		prepareDebug(scheduler, true);
		check(field(PmbSkillScheduler.class, "candidateAttempts").get(scheduler) != null,
				"configured transition enables candidate diagnostic capture");
		field(PmbSkillScheduler.class, "lastDebugSnapshot").set(scheduler,
				new PmbSkillDebugSnapshot(1, java.util.UUID.randomUUID(), "minecraft:zombie", 1,
						PmbSkillScheduler.Strategy.IDLE, null, List.of(), List.of(), Map.of(), Map.of(),
						Map.of(), null, false, List.of(), false, "-"));
		prepareDebug(scheduler, false);
		check(field(PmbSkillScheduler.class, "candidateAttempts").get(scheduler) == null
				&& scheduler.lastDebugSnapshot() == null,
				"configuration removal clears candidate capture and stale completed snapshot");
		field(PmbBowAiData.class, "configured").setBoolean(mob.ai.bow(), false);
		mob.scheduler.movement().apply(mob, mob.scheduler);
		check(mob.scheduler.movement().snapshot() == null,
				"configuration removal clears stale movement snapshot");
	}
	private static void prepareDebug(PmbSkillScheduler scheduler, boolean configured) throws Exception {
		var method = PmbSkillScheduler.class.getDeclaredMethod("prepareDebugCapture", boolean.class);
		method.setAccessible(true);
		method.invoke(scheduler, configured);
	}
	@SuppressWarnings("unchecked")
	private static List<PmbSkillDebugSnapshot.CandidateAttempt> debugAttempts(PmbSkillScheduler scheduler)
			throws Exception {
		return List.copyOf((List<PmbSkillDebugSnapshot.CandidateAttempt>)
				field(PmbSkillScheduler.class, "candidateAttempts").get(scheduler));
	}
	private static void testSchema() throws Exception {
		for (String id : List.of("bow", "shield", "wind_charge", "ender_pearl")) {
			var entry = PmbSkillSchema.byId(id).field("preferredHand");
			check(entry.choices().equals(List.of("main", "off", "main-enforce", "off-enforce")), "enum schema " + id);
			check(entry.defaultValue().asString().orElseThrow().equals(id.equals("bow") ? "main" : "off"), "default " + id);
		}
		check(!PmbSkillSchema.byId("mace").hasField("preferredHand"), "mace excludes preferredHand");
		var air = PmbSkillSchema.byId("air_tracking");
		check(air != null && air.field("trackAcceleration").min() == 0 && air.field("trackAcceleration").max() == 1
				&& air.field("trackMaxHorizontalSpeed").min() == 0 && air.field("trackMaxHorizontalSpeed").max() == 3,
				"air tracking acceleration and horizontal-speed schema bounds");
		check(air.field("activationSkills").defaultValue().asList().orElseThrow().size() == 2
				&& PmbSkillSchema.isRegisteredSkill("air_tracking"), "air tracking is a registered configurable skill");
		for (String id : List.of("shield", "wind_charge", "mace", "bow", "ender_pearl")) {
			var bias = PmbSkillSchema.byId(id).field("randomCooldownBias");
			check(bias != null && bias.defaultValue().asInt().orElseThrow() == 20
					&& bias.min() == 0 && bias.max() == 1200, "random cooldown schema " + id);
		}
		check(new PmbShieldAiData().randomCooldownBias() == 20
				&& new PmbWindChargeAiData().randomCooldownBias() == 20
				&& new PmbMaceAiData().randomCooldownBias() == 20
				&& new PmbBowAiData().randomCooldownBias() == 20
				&& new PmbEnderPearlAiData().randomCooldownBias() == 20,
				"legacy-absent skill data defaults random cooldown bias to twenty");
		PmbBowAiData bow = new PmbBowAiData();
		bow.restoreCooldowns(0, 60, 80);
		check(bow.lineCooldownRemaining() == 60 && bow.arcCooldownRemaining() == 80,
				"runtime cooldown restore accepts configured base plus default bias");
		var maximumBias = new net.minecraft.nbt.CompoundTag();
		maximumBias.putInt("randomCooldownBias", 1200);
		PmbSkillSchema.byId("bow").validate(maximumBias);
		boolean rejected = false;
		try {
			var excessiveBias = new net.minecraft.nbt.CompoundTag();
			excessiveBias.putInt("randomCooldownBias", 1201);
			PmbSkillSchema.byId("bow").validate(excessiveBias);
		} catch (com.mojang.brigadier.exceptions.CommandSyntaxException expectedFailure) {
			rejected = true;
		}
		check(rejected, "command schema rejects random cooldown bias above maximum");
		var duration = PmbSkillSchema.byId("air_tracking").field("trackDurationTicks");
		check(duration.defaultValue().asInt().orElseThrow() == -1 && duration.min() == -1 && duration.max() == 72000,
				"air tracking duration schema preserves infinite sentinel and bounded explicit durations");
		check(PmbAirTrackingAiData.DEFAULT_ACTIVATION_SKILLS.equals(List.of("wind_charge", "mace")),
				"air tracking defaults to wind charge and mace sources");
		java.util.UUID firstTarget = java.util.UUID.randomUUID();
		check(PmbAirTrackingController.sameSession("mace", firstTarget, false,
				"mace", firstTarget, false)
				&& !PmbAirTrackingController.sameSession("mace", firstTarget, false,
						"mace", java.util.UUID.randomUUID(), false)
				&& !PmbAirTrackingController.sameSession("mace", firstTarget, false,
						"mace", firstTarget, true),
				"air tracking session identity includes source target and evasive mode");
		check(PmbAirTrackingController.withinMaceAirRange(0.0D, 16.0D)
				&& PmbAirTrackingController.withinMaceAirRange(9.0D, 16.0D)
				&& PmbAirTrackingController.withinMaceAirRange(256.0D, 16.0D)
				&& !PmbAirTrackingController.withinMaceAirRange(256.01D, 16.0D),
				"mace air source covers the full inclusive follow range, including smash range");
		PmbAirTrackingController tracking = new PmbAirTrackingController();
		field(PmbAirTrackingController.class, "windLatched").setBoolean(tracking, true);
		check(tracking.trackingState().equals("wind_latched_waiting_next_tick"),
				"air tracking exposes a newly latched wind bounce before its first airborne control tick");
		field(PmbAirTrackingController.class, "source").set(tracking, "wind_charge");
		field(PmbAirTrackingController.class, "remaining").setInt(tracking, -1);
		check(tracking.trackingState().equals("infinite"),
				"negative-one air tracking duration remains a live infinite session rather than invalidating it");
		field(PmbAirTrackingController.class, "lookDenied").setBoolean(tracking, true);
		check(tracking.trackingState().equals("infinite,look_optional_denied"),
				"optional LOOK denial remains diagnostic state without ending an active tracking session");
		check(PmbAirTrackingController.groundLifecycleState(true, false, false, true)
					.equals("wind_latched_waiting_next_tick")
				&& PmbAirTrackingController.groundLifecycleState(true, false, false, false).equals("target_invalid")
				&& PmbAirTrackingController.groundLifecycleState(true, true, true, true).equals("landed"),
				"wind latch lifecycle waits on the bounce ground tick, rejects an invalid target, then clears on landing");
		tracking.clear("landed");
		check(tracking.trackingState().equals("inactive") && tracking.clearReason().equals("landed"),
				"landing clears the completed air tracking session after its active infinite state");
	}
	private static void testSkillPriorities() {
		PmbSkillPriorities priorities = new PmbSkillPriorities();
		check(priorities.effective(PmbSkillScheduler.Strategy.COMBAT).equals(List.of(
				List.of("mace"), List.of("ender_pearl"), List.of("wind_charge"), List.of("air_tracking"), List.of("bow"),
				List.of("shield"), List.of("vanilla"))), "combat priority default");
		check(priorities.effective(PmbSkillScheduler.Strategy.RETREAT).equals(List.of(
				List.of("ender_pearl"), List.of("wind_charge"), List.of("air_tracking"), List.of("vanilla"))), "retreat omission default");
		check(priorities.effective(PmbSkillScheduler.Strategy.IDLE).equals(List.of(List.of("vanilla"))),
				"idle vanilla-only default");
		PmbAiData priorityOnly = new PmbAiData();
		priorityOnly.skillPriorities().set(PmbSkillScheduler.Strategy.COMBAT, List.of(List.of("vanilla")));
		check(!priorityOnly.isConfigured() && priorityOnly.hasPersistentData(),
				"priority-only data persists without masquerading as a configured skill");

		var tag = new net.minecraft.nbt.ListTag();
		tag.add(net.minecraft.nbt.StringTag.valueOf("mace"));
		var equal = new net.minecraft.nbt.ListTag();
		equal.add(net.minecraft.nbt.StringTag.valueOf("ender_pearl"));
		equal.add(net.minecraft.nbt.StringTag.valueOf("bow"));
		tag.add(equal);
		tag.add(net.minecraft.nbt.StringTag.valueOf("vanilla"));
		var parsed = PmbSkillPriorities.parse(tag);
		check(parsed.equals(List.of(List.of("mace"), List.of("ender_pearl", "bow"), List.of("vanilla"))),
				"mixed quoted priority list parses into normalized tiers");
		check(PmbSkillPriorities.toTag(parsed).equals(tag), "priority mixed-list canonical round trip");
		priorities.set(PmbSkillScheduler.Strategy.COMBAT, parsed);
		check(priorities.hasOverride(PmbSkillScheduler.Strategy.COMBAT)
				&& priorities.tier(PmbSkillScheduler.Strategy.COMBAT, "bow") == 1
				&& priorities.tier(PmbSkillScheduler.Strategy.COMBAT, "wind_charge") == -1,
				"explicit list tier and omission semantics");
		priorities.reset(PmbSkillScheduler.Strategy.COMBAT);
		check(!priorities.hasOverrides(), "single-strategy reset returns to default without persistence");

		for (var invalid : List.of(
				List.of(List.of("mace")),
				List.of(List.of("mace"), List.of("mace"), List.of("vanilla")),
				List.of(List.of("unknown"), List.of("vanilla")))) {
			boolean rejected = false;
			try { PmbSkillPriorities.validate(invalid); } catch (IllegalArgumentException expected) { rejected = true; }
			check(rejected, "invalid priority list rejected atomically: " + invalid);
		}
	}
	private static void testBowMeleeSuppressionRules() throws Exception {
		PmbBowAiData bow = new PmbBowAiData();
		field(PmbBowAiData.class, "configured").setBoolean(bow, true);
		field(PmbBowAiData.class, "enabled").setBoolean(bow, true);
		field(PmbBowAiData.class, "lineMinRange").setFloat(bow, 4.0F);
		field(PmbBowAiData.class, "lineMaxRange").setFloat(bow, 8.0F);
		field(PmbBowAiData.class, "lineShootChance").setFloat(bow, 1.0F);
		field(PmbBowAiData.class, "arcMinRange").setFloat(bow, 12.0F);
		field(PmbBowAiData.class, "arcMaxRange").setFloat(bow, 16.0F);
		field(PmbBowAiData.class, "arcShootChance").setFloat(bow, 1.0F);

		check(PmbSkillItemAccess.hasBowInAllowedHand(PmbPreferredHand.MAIN, true, false)
				&& PmbSkillItemAccess.hasBowInAllowedHand(PmbPreferredHand.MAIN, false, true)
				&& PmbSkillItemAccess.hasBowInAllowedHand(PmbPreferredHand.OFF, true, false)
				&& PmbSkillItemAccess.hasBowInAllowedHand(PmbPreferredHand.OFF, false, true),
				"soft bow hand preferences accept an already held bow in either hand");
		check(PmbSkillItemAccess.hasBowInAllowedHand(PmbPreferredHand.MAIN_ENFORCE, true, false)
				&& !PmbSkillItemAccess.hasBowInAllowedHand(PmbPreferredHand.MAIN_ENFORCE, false, true)
				&& PmbSkillItemAccess.hasBowInAllowedHand(PmbPreferredHand.OFF_ENFORCE, false, true)
				&& !PmbSkillItemAccess.hasBowInAllowedHand(PmbPreferredHand.OFF_ENFORCE, true, false),
				"enforced bow hand preferences accept only their required hand");
		check(!PmbSkillItemAccess.hasBowInAllowedHand(PmbPreferredHand.MAIN, false, false),
				"inventory or binding availability without a held bow cannot suppress melee");

		check(!PmbSkillItemAccess.shouldSuppressMeleeForBow(bow, false, 4.0D, true, true),
				"missing invalid dead cross-level or unattackable target cannot suppress melee");
		check(PmbSkillItemAccess.shouldSuppressMeleeForBow(bow, true, 4.0D, true, false)
				&& PmbSkillItemAccess.shouldSuppressMeleeForBow(bow, true, 8.0D, true, false)
				&& PmbSkillItemAccess.shouldSuppressMeleeForBow(bow, true, 12.0D, true, false)
				&& PmbSkillItemAccess.shouldSuppressMeleeForBow(bow, true, 16.0D, true, false),
				"line and arc closed range boundaries suppress melee without consumed ammunition");
		check(!PmbSkillItemAccess.shouldSuppressMeleeForBow(bow, true, 3.99D, true, true)
				&& !PmbSkillItemAccess.shouldSuppressMeleeForBow(bow, true, 10.0D, true, true)
				&& !PmbSkillItemAccess.shouldSuppressMeleeForBow(bow, true, 16.01D, true, true),
				"outside and gap distances restore ordinary melee");

		field(PmbBowAiData.class, "lineShootChance").setFloat(bow, 0.0F);
		check(!PmbSkillItemAccess.shouldSuppressMeleeForBow(bow, true, 6.0D, true, true)
				&& PmbSkillItemAccess.shouldSuppressMeleeForBow(bow, true, 14.0D, true, true),
				"zero-chance mode does not suppress while another live range still can");
		field(PmbBowAiData.class, "lineShootChance").setFloat(bow, 1.0F);
		field(PmbBowAiData.class, "lineCooldown").setInt(bow, 100);
		check(PmbSkillItemAccess.shouldSuppressMeleeForBow(bow, true, 6.0D, true, false),
				"cooldown does not participate in lightweight melee suppression");

		field(PmbBowAiData.class, "doConsume").setBoolean(bow, true);
		check(!PmbSkillItemAccess.shouldSuppressMeleeForBow(bow, true, 6.0D, true, false)
				&& PmbSkillItemAccess.shouldSuppressMeleeForBow(bow, true, 6.0D, true, true),
				"consuming bow requires currently available supported ammunition");
		Fixture ammunitionMob = fixture();
		ammunitionMob.items.put(InteractionHand.MAIN_HAND, new ItemStack(Items.BOW));
		ammunitionMob.items.put(InteractionHand.OFF_HAND, new ItemStack(Items.ARROW));
		PmbAmmoAccess.Source ammunition = PmbAmmoAccess.find(ammunitionMob, stack -> stack.is(Items.ARROW));
		check(ammunition != null && PmbSkillItemAccess.shouldSuppressMeleeForBow(bow, true, 6.0D,
				true, true), "real hand ammunition satisfies consuming bow suppression");
		check(ammunition.consume(ammunitionMob)
				&& PmbAmmoAccess.find(ammunitionMob, stack -> stack.is(Items.ARROW)) == null
				&& !PmbSkillItemAccess.shouldSuppressMeleeForBow(bow, true, 6.0D, true, false),
				"exhausting the last supported arrow restores ordinary melee eligibility");
		field(PmbBowAiData.class, "enabled").setBoolean(bow, false);
		check(!PmbSkillItemAccess.shouldSuppressMeleeForBow(bow, true, 6.0D, true, true),
				"disabled bow never suppresses ordinary melee");
	}
	private static void testSkillTimingAndPreClaim() throws Exception {
		var zero = net.minecraft.util.RandomSource.create(2008L);
		var zeroControl = net.minecraft.util.RandomSource.create(2008L);
		check(!PmbSkillTiming.passesChance(zero, 0.0F)
				&& zero.nextInt() == zeroControl.nextInt(), "zero chance fails without consuming RNG");
		var one = net.minecraft.util.RandomSource.create(2008L);
		var oneControl = net.minecraft.util.RandomSource.create(2008L);
		check(PmbSkillTiming.passesChance(one, 1.0F)
				&& one.nextInt() == oneControl.nextInt(), "one chance succeeds without consuming RNG");
		var expected = net.minecraft.util.RandomSource.create(77L);
		boolean expectedChance = expected.nextFloat() < 0.5F;
		check(PmbSkillTiming.passesChance(net.minecraft.util.RandomSource.create(77L), 0.5F) == expectedChance,
				"intermediate chance uses strict random less-than comparison");
		var cooldownRandom = net.minecraft.util.RandomSource.create(91L);
		boolean inRange = true, sawMinimum = false, sawMaximum = false;
		for (int i = 0; i < 10000; i++) {
			int cooldown = PmbSkillTiming.cooldown(cooldownRandom, 40, 20);
			inRange &= cooldown >= 40 && cooldown <= 60;
			sawMinimum |= cooldown == 40;
			sawMaximum |= cooldown == 60;
		}
		check(inRange, "cooldown remains in inclusive configured interval");
		check(sawMinimum && sawMaximum, "cooldown randomization reaches both inclusive endpoints");

		PmbSkillScheduler scheduler = new PmbSkillScheduler();
		List<String> trace = new ArrayList<>();
		scheduler.offer("mace", PmbSkillScheduler.Category.MAIN, 20, () -> false,
				() -> trace.add("high"), PmbSkillScheduler.Resource.MAIN_HAND);
		scheduler.offer("bow", PmbSkillScheduler.Category.MAIN, 10, () -> true,
				() -> trace.add("low"), PmbSkillScheduler.Resource.MAIN_HAND);
		resolveCandidates(scheduler);
		check(trace.equals(List.of("low")), "failed pre-claim does not reserve resources from lower priority skill");

		scheduler = new PmbSkillScheduler();
		int[] cooldownWrites = {0};
		scheduler.claim("sustained", PmbSkillScheduler.Resource.LOOK);
		scheduler.offer("bow", PmbSkillScheduler.Category.THROW, 10,
				() -> { cooldownWrites[0]++; return true; }, () -> trace.add("candidate"),
				PmbSkillScheduler.Resource.LOOK);
		resolveCandidates(scheduler);
		check(cooldownWrites[0] == 1 && !trace.contains("candidate"),
				"successful probability keeps cooldown when resource claim fails");
		scheduler = new PmbSkillScheduler();
		int[] finalChecks = {0};
		scheduler.offer("mace", PmbSkillScheduler.Category.MAIN, 10,
				() -> { cooldownWrites[0]++; return true; }, () -> finalChecks[0]++,
				PmbSkillScheduler.Resource.MAIN_HAND);
		resolveCandidates(scheduler);
		check(finalChecks[0] == 1 && cooldownWrites[0] == 2,
				"winner final revalidation occurs after the already-retained cooldown write");

		boolean[] bouncePassed = {false};
		scheduler = new PmbSkillScheduler();
		trace.clear();
		scheduler.offer("wind", PmbSkillScheduler.Category.THROW, 10,
				() -> { bouncePassed[0] = false; return false; }, () -> trace.add("bounce"),
				PmbSkillScheduler.Resource.LOOK);
		scheduler.offer("wind", PmbSkillScheduler.Category.THROW, 10,
				() -> !bouncePassed[0], () -> trace.add("throw"), PmbSkillScheduler.Resource.LOOK);
		resolveCandidates(scheduler);
		check(trace.equals(List.of("throw")), "wind throw fallback follows failed bounce probability");
		scheduler = new PmbSkillScheduler();
		trace.clear();
		bouncePassed[0] = false;
		scheduler.claim("sustained", PmbSkillScheduler.Resource.LOOK);
		scheduler.offer("wind", PmbSkillScheduler.Category.THROW, 10,
				() -> { bouncePassed[0] = true; return true; }, () -> trace.add("bounce"),
				PmbSkillScheduler.Resource.LOOK);
		scheduler.offer("wind", PmbSkillScheduler.Category.THROW, 10,
				() -> !bouncePassed[0], () -> trace.add("throw"), PmbSkillScheduler.Resource.LOOK);
		resolveCandidates(scheduler);
		check(trace.isEmpty(), "wind throw does not fallback after successful bounce probability loses resources");
	}
	@SuppressWarnings("unchecked")
	private static void testPriorityPreemptionHarness() throws Exception {
		PmbSkillScheduler scheduler = new PmbSkillScheduler();
		List<String> trace = new ArrayList<>();
		field(PmbSkillScheduler.class, "strategy").set(scheduler, PmbSkillScheduler.Strategy.COMBAT);
		scheduler.maintainPhase("bow", "charge", () -> trace.add("cancel-bow"),
				new PmbSkillScheduler.Resource[] {PmbSkillScheduler.Resource.LOOK});
		prepareDebug(scheduler, true);
		scheduler.offerPlanned("mace", "smash", PmbSkillScheduler.Category.MAIN, 20,
				() -> true, () -> false, () -> trace.add("mace"), PmbSkillScheduler.Resource.LOOK);
		resolveCandidates(scheduler);
		check(trace.isEmpty(), "failed final check does not preempt incumbent or commit candidate");
		check(debugAttempts(scheduler).size() == 1
				&& debugAttempts(scheduler).getFirst().status() == PmbSkillDebugSnapshot.AttemptStatus.FINAL_REJECTED,
				"failed final validation is visible in scheduler debug evidence");

		((List<?>) field(PmbSkillScheduler.class, "candidates").get(scheduler)).clear();
		scheduler.offerPlanned("mace", "smash", PmbSkillScheduler.Category.MAIN, 20,
				() -> true, () -> true, () -> trace.add("mace"), PmbSkillScheduler.Resource.LOOK);
		resolveCandidates(scheduler);
		check(trace.equals(List.of("cancel-bow", "mace")),
				"strictly higher candidate cancels conflicting required phase only after final validation");

		scheduler = new PmbSkillScheduler();
		field(PmbSkillScheduler.class, "strategy").set(scheduler, PmbSkillScheduler.Strategy.COMBAT);
		trace.clear();
		scheduler.maintainPhase("bow", "charge", () -> trace.add("cancel-bow"),
				new PmbSkillScheduler.Resource[] {PmbSkillScheduler.Resource.LOOK});
		prepareDebug(scheduler, true);
		int[] lowerChecks = {0};
		scheduler.offerPlannedResult("mace", "smash", PmbSkillScheduler.Category.MAIN, 20,
				() -> true, () -> true, () -> PmbSkillScheduler.CommitResult.FAILED,
				PmbSkillScheduler.Resource.LOOK);
		scheduler.offer("ender_pearl", PmbSkillScheduler.Category.THROW, 10,
				() -> { lowerChecks[0]++; return true; }, () -> trace.add("pearl"));
		resolveCandidates(scheduler);
		check(trace.equals(List.of("cancel-bow")) && lowerChecks[0] == 0
				&& debugAttempts(scheduler).getFirst().status()
						== PmbSkillDebugSnapshot.AttemptStatus.COMMIT_FAILED_AFTER_PREEMPT,
				"failed post-preemption commit is diagnosed and terminates lower candidate checks");

		scheduler = new PmbSkillScheduler();
		field(PmbSkillScheduler.class, "strategy").set(scheduler, PmbSkillScheduler.Strategy.COMBAT);
		trace.clear();
		scheduler.maintainPhase("wind", "tracking", () -> trace.add("cancel-wind"),
				new PmbSkillScheduler.Resource[] {PmbSkillScheduler.Resource.NAVIGATION},
				PmbSkillScheduler.Resource.LOOK);
		scheduler.maintainPhase("mace", "smash", () -> trace.add("cancel-mace"),
				new PmbSkillScheduler.Resource[] {PmbSkillScheduler.Resource.NAVIGATION});
		check(trace.equals(List.of("cancel-wind"))
				&& scheduler.ownsResource("mace", PmbSkillScheduler.Resource.NAVIGATION)
				&& scheduler.canClaim("bow", PmbSkillScheduler.Resource.LOOK),
				"phase preemption rebuilds claims without leaving displaced optional resources behind");

		scheduler = new PmbSkillScheduler();
		field(PmbSkillScheduler.class, "strategy").set(scheduler, PmbSkillScheduler.Strategy.COMBAT);
		scheduler.maintainPhase("ender_pearl", "follow_through", () -> {},
				new PmbSkillScheduler.Resource[] {}, PmbSkillScheduler.Resource.LOOK);
		trace.clear();
		scheduler.offer("bow", PmbSkillScheduler.Category.MAIN, 20, () -> true,
				() -> trace.add("bow"), PmbSkillScheduler.Resource.LOOK);
		resolveCandidates(scheduler);
		check(trace.equals(List.of("bow"))
				&& scheduler.ownsResource("bow", PmbSkillScheduler.Resource.LOOK)
				&& scheduler.activePhases().values().stream().anyMatch(phase -> phase.owner().equals("ender_pearl")),
				"required candidate displaces an optional claim without cancelling its phase");

		scheduler = new PmbSkillScheduler();
		trace.clear();
		scheduler.offer("mace", PmbSkillScheduler.Category.MAIN, 20, () -> true, () -> trace.add("mace"));
		scheduler.offer("ender_pearl", PmbSkillScheduler.Category.THROW, 20, () -> true, () -> trace.add("pearl"));
		resolveCandidates(scheduler);
		check(trace.equals(List.of("mace")), "first successful irreversible commit ends tick arbitration");

		scheduler = new PmbSkillScheduler();
		PmbSkillPriorities priorities = (PmbSkillPriorities) field(PmbSkillScheduler.class, "priorities").get(scheduler);
		priorities.set(PmbSkillScheduler.Strategy.COMBAT,
				List.of(List.of("mace", "bow"), List.of("vanilla")));
		trace.clear();
		for (int tick = 0; tick < 2; tick++) {
			((List<?>) field(PmbSkillScheduler.class, "candidates").get(scheduler)).clear();
			scheduler.offer("mace", PmbSkillScheduler.Category.MAIN, 20, () -> true, () -> trace.add("mace"));
			scheduler.offer("bow", PmbSkillScheduler.Category.MAIN, 20, () -> true, () -> trace.add("bow"));
			resolveCandidates(scheduler);
		}
		check(trace.equals(List.of("mace", "bow")), "equal priority tier rotates winner per entity without incumbent preemption");

		scheduler = new PmbSkillScheduler();
		priorities = (PmbSkillPriorities) field(PmbSkillScheduler.class, "priorities").get(scheduler);
		priorities.set(PmbSkillScheduler.Strategy.COMBAT,
				List.of(List.of("mace", "bow"), List.of("vanilla")));
		trace.clear();
		int[] maceChecks = {0}, bowChecks = {0};
		scheduler.offer("mace", PmbSkillScheduler.Category.MAIN, 20,
				() -> { maceChecks[0]++; return false; }, () -> trace.add("mace"));
		scheduler.offer("bow", PmbSkillScheduler.Category.MAIN, 20,
				() -> { bowChecks[0]++; return true; }, () -> trace.add("bow"));
		resolveCandidates(scheduler);
		((List<?>) field(PmbSkillScheduler.class, "candidates").get(scheduler)).clear();
		scheduler.offer("mace", PmbSkillScheduler.Category.MAIN, 20,
				() -> { maceChecks[0]++; return true; }, () -> trace.add("mace"));
		scheduler.offer("bow", PmbSkillScheduler.Category.MAIN, 20,
				() -> { bowChecks[0]++; return true; }, () -> trace.add("bow"));
		resolveCandidates(scheduler);
		check(trace.equals(List.of("bow", "mace")) && maceChecks[0] == 2 && bowChecks[0] == 1,
				"contested tier advances after the actual winner and never checks later candidates after commit");

		scheduler = new PmbSkillScheduler();
		priorities = (PmbSkillPriorities) field(PmbSkillScheduler.class, "priorities").get(scheduler);
		priorities.set(PmbSkillScheduler.Strategy.COMBAT,
				List.of(List.of("mace", "bow"), List.of("vanilla")));
		trace.clear();
		scheduler.offer("mace", PmbSkillScheduler.Category.MAIN, 20, () -> true, () -> trace.add("mace"));
		resolveCandidates(scheduler);
		((List<?>) field(PmbSkillScheduler.class, "candidates").get(scheduler)).clear();
		scheduler.offer("mace", PmbSkillScheduler.Category.MAIN, 20, () -> true, () -> trace.add("mace"));
		scheduler.offer("bow", PmbSkillScheduler.Category.MAIN, 20, () -> true, () -> trace.add("bow"));
		resolveCandidates(scheduler);
		check(trace.equals(List.of("mace", "mace")),
				"an uncontested equal-tier action does not advance the round-robin cursor");

		scheduler = new PmbSkillScheduler();
		priorities = (PmbSkillPriorities) field(PmbSkillScheduler.class, "priorities").get(scheduler);
		priorities.set(PmbSkillScheduler.Strategy.COMBAT, List.of(List.of("vanilla")));
		int[] omittedChecks = {0};
		scheduler.offer("mace", PmbSkillScheduler.Category.MAIN, 20,
				() -> { omittedChecks[0]++; return true; }, () -> trace.add("omitted"));
		resolveCandidates(scheduler);
		check(omittedChecks[0] == 0 && !trace.contains("omitted"),
				"omitting a skill from the strategy prevents even its probability and cooldown check");

		scheduler = new PmbSkillScheduler();
		priorities = (PmbSkillPriorities) field(PmbSkillScheduler.class, "priorities").get(scheduler);
		priorities.set(PmbSkillScheduler.Strategy.COMBAT,
				List.of(List.of("vanilla"), List.of("bow")));
		int[] lowerNavigationChecks = {0};
		scheduler.offer("bow", PmbSkillScheduler.Category.MAIN, 20,
				() -> { lowerNavigationChecks[0]++; return true; }, () -> trace.add("lower-navigation"),
				PmbSkillScheduler.Resource.NAVIGATION);
		resolveCandidates(scheduler);
		check(lowerNavigationChecks[0] == 1 && !trace.contains("lower-navigation"),
				"a skill below vanilla consumes its normal check but cannot take NAVIGATION");

		scheduler = new PmbSkillScheduler();
		priorities = (PmbSkillPriorities) field(PmbSkillScheduler.class, "priorities").get(scheduler);
		priorities.set(PmbSkillScheduler.Strategy.COMBAT,
				List.of(List.of("bow", "ender_pearl"), List.of("vanilla")));
		field(PmbSkillScheduler.class, "strategy").set(scheduler, PmbSkillScheduler.Strategy.COMBAT);
		trace.clear();
		scheduler.maintainPhase("bow", "charge", () -> trace.add("cancel-bow"),
				new PmbSkillScheduler.Resource[] {PmbSkillScheduler.Resource.LOOK});
		scheduler.maintainPhase("pearl", "follow_through", () -> trace.add("cancel-pearl"),
				new PmbSkillScheduler.Resource[] {PmbSkillScheduler.Resource.LOOK});
		check(trace.equals(List.of("cancel-pearl")) && scheduler.ownsResource("bow", PmbSkillScheduler.Resource.LOOK),
				"an equal-priority active phase cannot preempt its incumbent");

		scheduler = new PmbSkillScheduler();
		priorities = (PmbSkillPriorities) field(PmbSkillScheduler.class, "priorities").get(scheduler);
		priorities.set(PmbSkillScheduler.Strategy.RETREAT, List.of(List.of("bow"), List.of("vanilla")));
		field(PmbSkillScheduler.class, "strategy").set(scheduler, PmbSkillScheduler.Strategy.COMBAT);
		trace.clear();
		scheduler.maintainPhase("bow", "charge", () -> trace.add("cancel"),
				new PmbSkillScheduler.Resource[] {PmbSkillScheduler.Resource.LOOK});
		field(PmbSkillScheduler.class, "strategy").set(scheduler, PmbSkillScheduler.Strategy.RETREAT);
		scheduler.maintainPhase("bow", "charge", () -> true, () -> trace.add("cancel"),
				new PmbSkillScheduler.Resource[] {PmbSkillScheduler.Resource.LOOK});
		check(trace.isEmpty(), "strategy change preserves a still-listed phase whose authority remains valid");
		scheduler.maintainPhase("bow", "charge", () -> false, () -> trace.add("cancel"),
				new PmbSkillScheduler.Resource[] {PmbSkillScheduler.Resource.LOOK});
		check(trace.equals(List.of("cancel")), "phase-local authority invalidation cancels the phase");
	}
	private static void resolveCandidates(PmbSkillScheduler scheduler) throws Exception {
		field(PmbSkillScheduler.class, "strategy").set(scheduler, PmbSkillScheduler.Strategy.COMBAT);
		var method = PmbSkillScheduler.class.getDeclaredMethod("resolveCandidates");
		method.setAccessible(true);
		method.invoke(scheduler);
	}
	private static void testFactionCompatCodec() {
		var legacy = com.pmb.faction.PmbVanillaCompatRules.CODEC.parse(JsonOps.INSTANCE,
				JsonParser.parseString("{\"piglin\":{\"greed\":false}}"))
				.getOrThrow();
		check(!legacy.piglin().greed(), "legacy piglin compatibility field preserved");
		check(legacy.breeze().windChargeNoAnger(), "legacy compatibility data defaults Breeze no-anger on");

		var configured = new com.pmb.faction.PmbVanillaCompatRules(
				com.pmb.faction.PmbPiglinCompatRules.DEFAULT,
				new com.pmb.faction.PmbBreezeCompatRules(false));
		var encoded = com.pmb.faction.PmbVanillaCompatRules.CODEC
				.encodeStart(JsonOps.INSTANCE, configured).getOrThrow();
		var decoded = com.pmb.faction.PmbVanillaCompatRules.CODEC
				.parse(JsonOps.INSTANCE, encoded).getOrThrow();
		check(!decoded.breeze().windChargeNoAnger(), "Breeze compatibility override survives codec round trip");
	}
	private static void testWindChargeNeutralFallbackPredicate() throws Exception {
		var predicate = com.pmb.faction.PmbFactionCombat.class.getDeclaredMethod(
				"shouldApplyWindChargeNeutralFallback", boolean.class, boolean.class, boolean.class,
				boolean.class, boolean.class, boolean.class);
		predicate.setAccessible(true);
		check(!(boolean) predicate.invoke(null, true, false, true, false, false, true),
				"non-wind damage cannot use wind neutral fallback");
		check(!(boolean) predicate.invoke(null, true, true, false, false, false, true),
				"victim outside vanilla wind no-anger tag cannot use fallback");
		check(!(boolean) predicate.invoke(null, true, true, true, true, false, true),
				"generic no-anger damage retains vanilla precedence");
		check(!(boolean) predicate.invoke(null, true, true, true, false, true, true),
				"Breeze owner is preserved by enabled compatibility rule");
		check((boolean) predicate.invoke(null, true, true, true, false, false, true),
				"non-Breeze owner bypasses the species compatibility exception");
		check((boolean) predicate.invoke(null, true, true, true, false, true, false),
				"disabled compatibility rule permits Breeze owner fallback");
		check(!(boolean) predicate.invoke(null, false, true, true, false, false, true),
				"ownerless damage cannot use neutral fallback");
	}
	private static void offer(Fixture mob, String owner, PmbMovementController.Type type,
			PmbMovementController.Tier tier, int rank, List<String> trace) {
		mob.scheduler.movement().submit(mob, owner, type, tier, PmbSkillScheduler.Category.MAIN,
				rank, () -> true, () -> trace.add(owner));
	}
	private static PmbSkillItemAccess.Resolved resolve(Fixture mob, PmbActivationSources sources, PmbPreferredHand preference) {
		return PmbSkillItemAccess.resolvePreferred(mob, sources, List.of(InteractionHand.MAIN_HAND),
				stack -> stack.is(Items.BOW), preference, "bow");
	}
	private static PmbActivationSources sources(PmbActivationSources.Source... entries) throws Exception {
		var sources = new PmbActivationSources();
		field(PmbActivationSources.class, "explicit").setBoolean(sources, true);
		@SuppressWarnings("unchecked") List<PmbActivationSources.Source> list =
				(List<PmbActivationSources.Source>) field(PmbActivationSources.class, "sources").get(sources);
		list.addAll(List.of(entries));
		return sources;
	}
	private static Field field(Class<?> type, String name) throws Exception {
		Field field = type.getDeclaredField(name); field.setAccessible(true); return field;
	}
	private static Fixture fixture() throws Exception {
		Unsafe unsafe = (Unsafe) field(Unsafe.class, "theUnsafe").get(null);
		Fixture mob = (Fixture) unsafe.allocateInstance(Fixture.class);
		mob.items = new EnumMap<>(InteractionHand.class);
		mob.scheduler = new PmbSkillScheduler(); mob.inventory = new PmbInventory(); mob.ai = new PmbAiData();
		mob.inventory.read(mob, TagValueInput.create(ProblemReporter.DISCARDING, RegistryAccess.EMPTY,
				inventoryRoot(3)));
		return mob;
	}
	private static void check(boolean condition, String label) {
		if (!condition) throw new AssertionError(label); checks++;
	}
	private static final class Fixture extends Mob implements PmbSchedulerHolder, PmbInventoryHolder, PmbAiHolder,
			com.pmb.faction.PmbFactionMobState {
		private net.minecraft.world.entity.LivingEntity combat, avoid;
		private Map<InteractionHand, ItemStack> items;
		private PmbSkillScheduler scheduler;
		private PmbInventory inventory;
		private PmbAiData ai;
		private Fixture() { super(EntityType.ZOMBIE, null); }
		@Override public ItemStack getItemInHand(InteractionHand hand) { return items.getOrDefault(hand, ItemStack.EMPTY); }
		@Override public ItemStack getMainHandItem() { return getItemInHand(InteractionHand.MAIN_HAND); }
		@Override public ItemStack getOffhandItem() { return getItemInHand(InteractionHand.OFF_HAND); }
		@Override public void setItemInHand(InteractionHand hand, ItemStack item) { items.put(hand, item); }
		@Override public boolean isAlive() { return true; }
		@Override public boolean isNoAi() { return false; }
		@Override public PmbSkillScheduler pmb$getSkillScheduler() { return scheduler; }
		@Override public PmbInventory pmb$getInventory() { return inventory; }
		@Override public PmbAiData pmb$getAiData() { return ai; }
		@Override public net.minecraft.world.entity.LivingEntity getTarget() { return combat; }
		@Override public net.minecraft.world.entity.LivingEntity pmb$getFactionCombatTarget() { return combat; }
		@Override public void pmb$setFactionCombatTarget(net.minecraft.world.entity.LivingEntity target) { combat = target; }
		@Override public net.minecraft.world.entity.LivingEntity pmb$getFactionAvoidTarget() { return avoid; }
		@Override public void pmb$setFactionAvoidTarget(net.minecraft.world.entity.LivingEntity target) { avoid = target; }
		@Override public int pmb$getFactionHurtTimestamp() { return 0; }
		@Override public void pmb$setFactionHurtTimestamp(int timestamp) {}
		@Override public boolean pmb$wasFactionAvoiding() { return avoid != null; }
		@Override public void pmb$setFactionAvoiding(boolean value) {}
		@Override public boolean pmb$isFactionGroupRevenge() { return false; }
		@Override public void pmb$setFactionGroupRevenge(boolean value) {}
	}
}
