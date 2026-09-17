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
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TextColor;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
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
		testMovement();
		testDebugCaptureGate();
		testSchema();
		testBowMeleeSuppressionRules();
		testSkillTimingAndPreClaim();
		testSkillDebugDiagnostics();
		testFactionCompatCodec();
		testWindChargeNeutralFallbackPredicate();
		testWindBounceLookOwnershipBoundary();
		testParameterSuggestions();
		System.out.println("PASSED " + checks + " equipment/movement/schema assertions");
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
				&& a.status() == PmbSkillDebugSnapshot.AttemptStatus.FINAL_REJECTED),
				"admitted action without execution marker becomes final rejected");

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
		check(debugAttempts(current[0]).getFirst().status() == PmbSkillDebugSnapshot.AttemptStatus.FINAL_REJECTED,
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
						new PmbSkillDebugSnapshot.SkillState("shield", true, false, false, "check=0", "-"),
						new PmbSkillDebugSnapshot.SkillState("mace", false, false, false, "smash=0", "-")),
				List.of(PmbSkillDebugSnapshot.CandidateAttempt.resourceBlocked("bow", "line",
						PmbSkillScheduler.Category.MAIN, 10,
						new PmbSkillScheduler.Resource[] {PmbSkillScheduler.Resource.USE_ITEM},
						PmbSkillScheduler.Resource.USE_ITEM, "shield")),
				Map.of(), Map.of(PmbSkillScheduler.Resource.USE_ITEM, "shield"),
				Map.of("bow", "hand=MAIN_HAND"), null, true, List.of("bow"), false, "-");
		var formatted = debugSnapshot.formatForLog(103);
		check(formatted.startsWith("PMB SKILL DEBUG\nENTITY: minecraft:husk#42 uuid=" + debugUuid),
				"skill debug log formatter starts with prominent entity identity");
		check(formatted.contains("\nSKILLS:\n  bow [ACTIVE] cooldown{line=7,arc=11}")
				&& !formatted.contains("shield [") && !formatted.contains("mace ["),
				"skill debug log shows only enabled skills and highlights active state");
		check(formatted.contains("\nLAST ATTEMPTS:\n  bow/line [RESOURCE_BLOCKED]")
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
		scheduler.offerDynamic("plain", "plain", PmbSkillScheduler.Category.MAIN, 1,
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
		scheduler.offer("high", PmbSkillScheduler.Category.MAIN, 20, () -> false,
				() -> trace.add("high"), PmbSkillScheduler.Resource.MAIN_HAND);
		scheduler.offer("low", PmbSkillScheduler.Category.MAIN, 10, () -> true,
				() -> trace.add("low"), PmbSkillScheduler.Resource.MAIN_HAND);
		resolveCandidates(scheduler);
		check(trace.equals(List.of("low")), "failed pre-claim does not reserve resources from lower priority skill");

		scheduler = new PmbSkillScheduler();
		int[] cooldownWrites = {0};
		scheduler.claim("sustained", PmbSkillScheduler.Resource.LOOK);
		scheduler.offer("candidate", PmbSkillScheduler.Category.THROW, 10,
				() -> { cooldownWrites[0]++; return true; }, () -> trace.add("candidate"),
				PmbSkillScheduler.Resource.LOOK);
		resolveCandidates(scheduler);
		check(cooldownWrites[0] == 1 && !trace.contains("candidate"),
				"successful probability keeps cooldown when resource claim fails");
		scheduler = new PmbSkillScheduler();
		int[] finalChecks = {0};
		scheduler.offer("stale", PmbSkillScheduler.Category.MAIN, 10,
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
	private static void resolveCandidates(PmbSkillScheduler scheduler) throws Exception {
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
	private static void testWindBounceLookOwnershipBoundary() throws Exception {
		var predicate = Class.forName("com.pmb.mixin.PmbMobWindChargeMixin")
				.getDeclaredMethod("pmb$bounceStillOwnsLook", int.class, boolean.class);
		predicate.setAccessible(true);
		check((boolean) predicate.invoke(null, 60, true), "fresh grounded wind bounce owns LOOK during grace");
		check((boolean) predicate.invoke(null, 59, true), "second grounded wind bounce grace tick owns LOOK");
		check(!(boolean) predicate.invoke(null, 58, true), "first grounded clear tick releases LOOK before arbitration");
		check((boolean) predicate.invoke(null, 58, false), "airborne wind bounce retains LOOK after grace boundary");
		check((boolean) predicate.invoke(null, 1, false), "final airborne wind bounce tick retains LOOK");
		check(!(boolean) predicate.invoke(null, 0, false), "expired wind bounce never owns LOOK");
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
		field(PmbInventory.class, "slots").setInt(mob.inventory, 3);
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
