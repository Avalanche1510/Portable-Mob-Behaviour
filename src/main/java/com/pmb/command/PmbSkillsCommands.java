package com.pmb.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import com.pmb.PortableMobBehaviour;
import com.pmb.ai.PmbAiData;
import com.pmb.ai.PmbAiHolder;
import com.pmb.ai.PmbSchedulerHolder;
import com.pmb.ai.PmbShieldVulnerableHolder;
import com.pmb.ai.PmbSkillConfigData;
import com.pmb.ai.PmbSkillHooks;
import com.pmb.ai.PmbSkillScheduler;
import com.pmb.ai.PmbSkillPriorities;
import com.pmb.ai.PmbSkillSchema;
import com.pmb.ai.PmbPathfindingData;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import net.fabricmc.fabric.api.command.v2.ArgumentTypeRegistry;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.permissions.Permissions;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;

public final class PmbSkillsCommands {
	private static final DynamicCommandExceptionType ERROR = new DynamicCommandExceptionType(
			message -> Component.literal(String.valueOf(message)));
	private PmbSkillsCommands() {}

	public static void initialize() {
		ArgumentTypeRegistry.registerArgumentType(PortableMobBehaviour.id("skill_parameters"),
				PmbSkillParametersArgument.class, new PmbSkillParametersArgument.Info());
		ArgumentTypeRegistry.registerArgumentType(PortableMobBehaviour.id("priority_list"),
				PmbPriorityListArgument.class, new PmbPriorityListArgument.Info());
		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> register(dispatcher));
	}

	private static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
		RequiredArgumentBuilder<CommandSourceStack, ?> activeTargets = Commands.argument("targets", EntityArgument.entities());
		RequiredArgumentBuilder<CommandSourceStack, ?> conditionalTargets = Commands.argument("targets", EntityArgument.entities());
		for (PmbSkillSchema schema : PmbSkillSchema.all()) {
			if (schema.skillKind() == PmbSkillSchema.SkillKind.ACTIVE) activeTargets.then(skillBranch(schema));
			else conditionalTargets.then(skillBranch(schema));
		}
		RequiredArgumentBuilder<CommandSourceStack, ?> priorityTargets = Commands.argument("targets", EntityArgument.entities());
		priorityTargets.then(priorityGetBranch()).then(prioritySetBranch()).then(priorityResetBranch());
		dispatcher.register(Commands.literal("pmb")
				.requires(source -> source.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER))
				.then(Commands.literal("skill")
						.then(Commands.literal("active").then(activeTargets))
						.then(Commands.literal("conditional").then(conditionalTargets))
						.then(Commands.literal("priority").then(priorityTargets)))
				.then(pathfindingBranch()));
	}

	private static LiteralArgumentBuilder<CommandSourceStack> pathfindingBranch() {
		return Commands.literal("pathfinding")
				.then(pathSection("planner"))
				.then(pathSection("strategy"))
				.then(pathSection("air_tracking"))
				.then(pathSection("retreat"));
	}
	private static LiteralArgumentBuilder<CommandSourceStack> pathSection(String section) {
		RequiredArgumentBuilder<CommandSourceStack, ?> targets = Commands.argument("targets", EntityArgument.entities());
		targets.then(Commands.literal("get").executes(context -> pathfindingGet(context.getSource(),
				entities(context), section)));
		if (section.equals("planner")) {
			targets.then(pathFloat(section, "planningHorizon", 1, 256))
					.then(pathInt(section, "repathIntervalTicks", 1, 1200))
					.then(pathInt(section, "objectiveMemoryTicks", 0, 72000))
					.then(pathFloat(section, "targetMoveThreshold", 0, 64))
					.then(pathInt(section, "conditionalWaitTicks", 1, 72000))
					.then(Commands.literal("set").then(Commands.literal("debugPathParticles")
							.then(Commands.argument("value", BoolArgumentType.bool())
									.executes(context -> pathPlannerDebugSet(context.getSource(), entities(context),
											BoolArgumentType.getBool(context, "value"))))));
		} else if (section.equals("air_tracking")) {
			targets.then(pathFloat(section, "trackAcceleration", 0, 1))
					.then(pathFloat(section, "trackMaxHorizontalSpeed", 0, 3))
					.then(pathInt(section, "trackDurationTicks", -1, 72000));
		} else if (section.equals("retreat")) {
			targets.then(pathFloat(section, "speedMultiplier", 0, 100))
					.then(pathInt(section, "searchHorizontalRange", 1, 256))
					.then(pathInt(section, "searchVerticalRange", 1, 256))
					.then(pathInt(section, "repathIntervalTicks", 1, 1200));
		} else {
			for (String strategy : List.of("combat", "retreat", "idle")) {
				targets.then(Commands.literal("set").then(Commands.literal(strategy)
						.then(Commands.literal("routeMode").then(Commands.argument("value", StringArgumentType.word())
								.suggests((context, builder) -> net.minecraft.commands.SharedSuggestionProvider.suggest(
										List.of("vanilla_only", "fallback", "efficient"), builder))
								.executes(context -> pathStrategySet(context.getSource(), entities(context), strategy,
										"routeMode", StringArgumentType.getString(context, "value")))))
						.then(Commands.literal("dangerousFallPolicy").then(Commands.argument("value", StringArgumentType.word())
								.suggests((context, builder) -> net.minecraft.commands.SharedSuggestionProvider.suggest(
										List.of("enable", "disable", "conditional_required"), builder))
								.executes(context -> pathStrategySet(context.getSource(), entities(context), strategy,
										"dangerousFallPolicy", StringArgumentType.getString(context, "value")))))
						.then(Commands.literal("requireEyeSight").then(Commands.argument("value", BoolArgumentType.bool())
								.executes(context -> pathStrategySet(context.getSource(), entities(context), strategy,
										"requireEyeSight", Boolean.toString(BoolArgumentType.getBool(context, "value"))))))));
			}
		}
		return Commands.literal(section).then(targets);
	}
	private static LiteralArgumentBuilder<CommandSourceStack> pathFloat(String section, String field, float min, float max) {
		return Commands.literal("set").then(Commands.literal(field).then(Commands.argument("value", FloatArgumentType.floatArg(min, max))
				.executes(context -> pathNumberSet(context.getSource(), entities(context), section, field,
						FloatArgumentType.getFloat(context, "value")))));
	}
	private static LiteralArgumentBuilder<CommandSourceStack> pathInt(String section, String field, int min, int max) {
		return Commands.literal("set").then(Commands.literal(field).then(Commands.argument("value", IntegerArgumentType.integer(min, max))
				.executes(context -> pathNumberSet(context.getSource(), entities(context), section, field,
						IntegerArgumentType.getInteger(context, "value")))));
	}
	private static int pathfindingGet(CommandSourceStack source, Collection<? extends Entity> entities, String section)
			throws CommandSyntaxException {
		List<Mob> mobs = mobs(entities);
		if (mobs.size() != 1) throw error("get requires exactly one Mob target");
		PmbPathfindingData data = ((PmbAiHolder) mobs.getFirst()).pmb$getAiData().pathfinding();
		String value = switch (section) {
			case "planner" -> data.planner().toString();
			case "strategy" -> "combat=" + data.strategy(PmbSkillScheduler.Strategy.COMBAT) + ",retreat="
					+ data.strategy(PmbSkillScheduler.Strategy.RETREAT) + ",idle=" + data.strategy(PmbSkillScheduler.Strategy.IDLE);
			case "air_tracking" -> "acceleration=" + data.airTracking().trackAcceleration() + ",maxSpeed="
					+ data.airTracking().trackMaxHorizontalSpeed() + ",duration=" + data.airTracking().trackDurationTicks();
			case "retreat" -> data.retreat().toString();
			default -> throw new IllegalArgumentException(section);
		};
		source.sendSuccess(() -> Component.literal(section + ": " + value), false);
		return 1;
	}
	private static int pathNumberSet(CommandSourceStack source, Collection<? extends Entity> entities,
			String section, String field, Number value) throws CommandSyntaxException {
		List<Mob> mobs = mobs(entities);
		for (Mob mob : mobs) {
			PmbPathfindingData data = ((PmbAiHolder) mob).pmb$getAiData().pathfinding();
			if (section.equals("planner")) {
				var old = data.planner();
				data.setPlanner(new PmbPathfindingData.Planner(
						field.equals("planningHorizon") ? value.floatValue() : old.planningHorizon(),
						field.equals("repathIntervalTicks") ? value.intValue() : old.repathIntervalTicks(),
						field.equals("objectiveMemoryTicks") ? value.intValue() : old.objectiveMemoryTicks(),
						field.equals("targetMoveThreshold") ? value.floatValue() : old.targetMoveThreshold(),
						field.equals("conditionalWaitTicks") ? value.intValue() : old.conditionalWaitTicks(),
						old.debugPathParticles()));
			} else if (section.equals("retreat")) {
				var old = data.retreat();
				data.setRetreat(new PmbPathfindingData.Retreat(
						field.equals("speedMultiplier") ? value.floatValue() : old.speedMultiplier(),
						field.equals("searchHorizontalRange") ? value.intValue() : old.searchHorizontalRange(),
						field.equals("searchVerticalRange") ? value.intValue() : old.searchVerticalRange(),
						field.equals("repathIntervalTicks") ? value.intValue() : old.repathIntervalTicks()));
			} else {
				var old = data.airTracking();
				old.set(field.equals("trackAcceleration") ? value.floatValue() : old.trackAcceleration(),
						field.equals("trackMaxHorizontalSpeed") ? value.floatValue() : old.trackMaxHorizontalSpeed(),
						field.equals("trackDurationTicks") ? value.intValue() : old.trackDurationTicks());
			}
		}
		source.sendSuccess(() -> Component.literal("Set pathfinding " + section + '.' + field + '=' + value
				+ " for " + mobs.size() + " mob(s)"), true);
		return mobs.size();
	}
	private static int pathPlannerDebugSet(CommandSourceStack source, Collection<? extends Entity> entities,
			boolean enabled) throws CommandSyntaxException {
		List<Mob> mobs = mobs(entities);
		for (Mob mob : mobs) {
			PmbPathfindingData data = ((PmbAiHolder) mob).pmb$getAiData().pathfinding();
			var old = data.planner();
			data.setPlanner(new PmbPathfindingData.Planner(old.planningHorizon(), old.repathIntervalTicks(),
					old.objectiveMemoryTicks(), old.targetMoveThreshold(), old.conditionalWaitTicks(), enabled));
		}
		source.sendSuccess(() -> Component.literal("Set pathfinding planner.debugPathParticles=" + enabled
				+ " for " + mobs.size() + " mob(s)"), true);
		return mobs.size();
	}
	private static int pathStrategySet(CommandSourceStack source, Collection<? extends Entity> entities,
			String strategyName, String field, String raw) throws CommandSyntaxException {
		PmbSkillScheduler.Strategy key = strategy(strategyName);
		List<Mob> mobs = mobs(entities);
		for (Mob mob : mobs) {
			PmbPathfindingData data = ((PmbAiHolder) mob).pmb$getAiData().pathfinding();
			var old = data.strategy(key);
			PmbPathfindingData.RouteMode mode = old.routeMode();
			PmbPathfindingData.DangerousFallPolicy fall = old.dangerousFallPolicy();
			boolean sight = old.requireEyeSight();
			if (field.equals("routeMode")) {
				try { mode = PmbPathfindingData.RouteMode.valueOf(raw.toUpperCase(java.util.Locale.ROOT)); }
				catch (IllegalArgumentException exception) { throw error("routeMode must be vanilla_only, fallback, or efficient"); }
			} else if (field.equals("dangerousFallPolicy")) {
				try { fall = PmbPathfindingData.DangerousFallPolicy.valueOf(raw.toUpperCase(java.util.Locale.ROOT)); }
				catch (IllegalArgumentException exception) { throw error("dangerousFallPolicy must be enable, disable, or conditional_required"); }
			} else sight = Boolean.parseBoolean(raw);
			data.setStrategy(key, new PmbPathfindingData.Strategy(mode, fall, sight));
		}
		return mobs.size();
	}
	private static LiteralArgumentBuilder<CommandSourceStack> priorityGetBranch() {
		LiteralArgumentBuilder<CommandSourceStack> get = Commands.literal("get");
		for (String name : List.of("combat", "retreat", "idle", "all"))
			get.then(Commands.literal(name).executes(context -> priorityGet(context.getSource(), entities(context), name)));
		return get;
	}
	private static LiteralArgumentBuilder<CommandSourceStack> prioritySetBranch() {
		LiteralArgumentBuilder<CommandSourceStack> set = Commands.literal("set");
		for (String name : List.of("combat", "retreat", "idle"))
			set.then(Commands.literal(name).then(Commands.argument("priorities", new PmbPriorityListArgument())
					.executes(context -> prioritySet(context.getSource(), entities(context), name,
							PmbPriorityListArgument.get(context, "priorities")))));
		return set;
	}
	private static LiteralArgumentBuilder<CommandSourceStack> priorityResetBranch() {
		LiteralArgumentBuilder<CommandSourceStack> reset = Commands.literal("reset");
		for (String name : List.of("combat", "retreat", "idle", "all"))
			reset.then(Commands.literal(name).executes(context -> priorityReset(context.getSource(), entities(context), name)));
		return reset;
	}

	private static LiteralArgumentBuilder<CommandSourceStack> skillBranch(PmbSkillSchema schema) {
		String id = schema.id();
		return Commands.literal(id)
				.then(Commands.literal("get").executes(context -> get(context.getSource(), entities(context), schema)))
				.then(writeBranch(schema, "append"))
				.then(writeBranch(schema, "insert"))
				.then(writeBranch(schema, "modify"))
				.then(Commands.literal("delete")
						.then(Commands.literal("*").executes(context -> mutate(context.getSource(), entities(context), schema,
								Mode.DELETE_ALL, null)))
						.then(Commands.argument("parameters", new PmbSkillParametersArgument(id, true))
								.executes(context -> mutate(context.getSource(), entities(context), schema, Mode.DELETE,
										PmbSkillParametersArgument.get(context, "parameters")))));
	}

	private static LiteralArgumentBuilder<CommandSourceStack> writeBranch(PmbSkillSchema schema, String mode) {
		Mode operation = Mode.valueOf(mode.toUpperCase());
		return Commands.literal(mode)
				.then(Commands.argument("parameters", new PmbSkillParametersArgument(schema.id(), false))
						.executes(context -> mutate(context.getSource(), entities(context), schema, operation,
								PmbSkillParametersArgument.get(context, "parameters"))));
	}

	private static Collection<? extends Entity> entities(com.mojang.brigadier.context.CommandContext<CommandSourceStack> context)
			throws CommandSyntaxException { return EntityArgument.getEntities(context, "targets"); }

	private static int mutate(CommandSourceStack source, Collection<? extends Entity> entities, PmbSkillSchema schema,
			Mode mode, PmbSkillPatch patch) throws CommandSyntaxException {
		List<Prepared> prepared = new ArrayList<>();
		long gameTime = source.getServer().overworld().getGameTime();
		HolderLookup.Provider registries = source.registryAccess();
		for (Entity entity : entities) {
			if (!(entity instanceof Mob mob)) throw error("Target is not a Mob: " + entity.getName().getString());
			PmbAiData data = ((PmbAiHolder) mob).pmb$getAiData();
			PmbSkillConfigData selected = data.skill(schema.id());
			boolean exists = selected.isConfigured();
			Set<String> explicit = selected.explicitFields();
			if (mode == Mode.APPEND && exists) throw error(entityName(mob) + " already has skill node " + schema.id());
			if (mode != Mode.APPEND && !exists) throw error(entityName(mob) + " has no skill node " + schema.id());
			if (mode == Mode.INSERT) for (String key : patch.keys())
				if (explicit.contains(key)) throw error(entityName(mob) + " already explicitly defines " + key);
			if (mode == Mode.MODIFY || mode == Mode.DELETE) for (String key : patch.keys())
				if (!explicit.contains(key)) throw error(entityName(mob) + " does not explicitly define " + key);

			CompoundTag snapshot = data.snapshot(registries, gameTime);
			CompoundTag ai = snapshot.getCompound(PmbAiData.TAG).orElseGet(() -> {
				CompoundTag created = new CompoundTag(); snapshot.put(PmbAiData.TAG, created); return created;
			});
			CompoundTag skill = ai.getCompound("skill").orElseGet(() -> {
				CompoundTag created = new CompoundTag(); ai.put("skill", created); return created;
			});
			String kind = schema.skillKind() == PmbSkillSchema.SkillKind.ACTIVE ? "active" : "conditional";
			CompoundTag group = skill.getCompound(kind).orElseGet(() -> {
				CompoundTag created = new CompoundTag(); skill.put(kind, created); return created;
			});
			String[] nodePath = schema.id().split("\\.");
			CompoundTag container = group;
			if (nodePath.length == 2) container = group.getCompound(nodePath[0]).orElseGet(() -> {
				CompoundTag created = new CompoundTag(); group.put(nodePath[0], created); return created;
			});
			String nodeName = nodePath[nodePath.length - 1];
			if (mode == Mode.DELETE && patch.keys().isEmpty()) {
				container.remove(nodeName);
			} else {
				CompoundTag parentNode = container;
				CompoundTag node = container.getCompound(nodeName).orElseGet(() -> {
					CompoundTag created = new CompoundTag(); parentNode.put(nodeName, created); return created;
				});
				if (mode == Mode.DELETE_ALL) for (PmbSkillSchema.Field field : schema.fields()) node.remove(field.name());
				else if (mode == Mode.DELETE) for (String key : patch.keys()) node.remove(key);
				else for (var entry : patch.values().entrySet()) node.put(entry.getKey(), entry.getValue().copy());
				CompoundTag effective = schema.defaultTag(); effective.merge(node.copy()); schema.validate(effective);
			}
			prepared.add(new Prepared(mob, snapshot));
		}

		for (Prepared item : prepared) {
			cancelSelected(item.mob(), schema.id());
			PmbAiData data = ((PmbAiHolder) item.mob()).pmb$getAiData();
			data.replaceSkillFromSnapshot(schema.id(), item.snapshot(), registries);
			data.skill(schema.id()).resetRuntime();
		}
		source.sendSuccess(() -> Component.literal("Updated " + schema.id() + " for " + prepared.size() + " mob(s)"), true);
		return prepared.size();
	}

	private static int get(CommandSourceStack source, Collection<? extends Entity> entities, PmbSkillSchema schema)
			throws CommandSyntaxException {
		if (entities.size() != 1) throw error("get requires exactly one target");
		Entity entity = entities.iterator().next();
		if (!(entity instanceof Mob mob)) throw error("Target is not a Mob: " + entity.getName().getString());
		PmbAiData data = ((PmbAiHolder) mob).pmb$getAiData();
		PmbSkillConfigData selected = data.skill(schema.id());
		if (!selected.isConfigured()) {
			source.sendSuccess(() -> Component.literal(schema.id() + ": node absent"), false); return 0;
		}
		CompoundTag snapshot = data.snapshot(source.registryAccess(), source.getServer().overworld().getGameTime());
		String kind = schema.skillKind() == PmbSkillSchema.SkillKind.ACTIVE ? "active" : "conditional";
		CompoundTag node = snapshot.getCompoundOrEmpty(PmbAiData.TAG).getCompoundOrEmpty("skill")
				.getCompoundOrEmpty(kind);
		for (String segment : schema.id().split("\\.")) node = node.getCompoundOrEmpty(segment);
		Set<String> explicit = new LinkedHashSet<>(selected.explicitFields());
		List<String> values = new ArrayList<>();
		for (PmbSkillSchema.Field field : schema.fields()) {
			Tag value = node.get(field.name());
			String rendered = value == null ? field.defaultText() : value.toString();
			values.add(field.name() + "=" + rendered + (explicit.contains(field.name()) ? " (explicit)" : " (default)"));
		}
		source.sendSuccess(() -> Component.literal(schema.id() + " explicit=" + explicit + " values={" + String.join(", ", values) + "}"), false);
		return 1;
	}
	private static int prioritySet(CommandSourceStack source, Collection<? extends Entity> entities, String name,
			List<List<String>> tiers) throws CommandSyntaxException {
		PmbSkillScheduler.Strategy strategy = strategy(name);
		List<Mob> mobs = mobs(entities);
		for (Mob mob : mobs) ((PmbAiHolder) mob).pmb$getAiData().skillPriorities().set(strategy, tiers);
		for (Mob mob : mobs) ((PmbSchedulerHolder) mob).pmb$getSkillScheduler().resetPriorityRuntime(strategy);
		source.sendSuccess(() -> Component.literal("Set " + name + " priorities to "
				+ PmbSkillPriorities.toTag(tiers) + " for " + mobs.size() + " mob(s)"), true);
		return mobs.size();
	}
	private static int priorityReset(CommandSourceStack source, Collection<? extends Entity> entities, String name)
			throws CommandSyntaxException {
		List<Mob> mobs = mobs(entities);
		for (Mob mob : mobs) {
			PmbSkillPriorities priorities = ((PmbAiHolder) mob).pmb$getAiData().skillPriorities();
			PmbSkillScheduler scheduler = ((PmbSchedulerHolder) mob).pmb$getSkillScheduler();
			if (name.equals("all")) { priorities.resetAll(); scheduler.resetPriorityRuntime(null); }
			else { PmbSkillScheduler.Strategy strategy = strategy(name); priorities.reset(strategy); scheduler.resetPriorityRuntime(strategy); }
		}
		source.sendSuccess(() -> Component.literal("Reset " + name + " priorities for " + mobs.size() + " mob(s)"), true);
		return mobs.size();
	}
	private static int priorityGet(CommandSourceStack source, Collection<? extends Entity> entities, String name)
			throws CommandSyntaxException {
		List<Mob> mobs = mobs(entities);
		if (mobs.size() != 1) throw error("get requires exactly one Mob target");
		PmbSkillPriorities priorities = ((PmbAiHolder) mobs.getFirst()).pmb$getAiData().skillPriorities();
		List<String> strategies = name.equals("all") ? List.of("combat", "retreat", "idle") : List.of(name);
		for (String selected : strategies) {
			PmbSkillScheduler.Strategy strategy = strategy(selected);
			String sourceName = priorities.hasOverride(strategy) ? "override" : "default";
			source.sendSuccess(() -> Component.literal(selected + " (" + sourceName + "): "
					+ PmbSkillPriorities.toTag(priorities.effective(strategy))), false);
		}
		return 1;
	}
	private static List<Mob> mobs(Collection<? extends Entity> entities) throws CommandSyntaxException {
		List<Mob> result = new ArrayList<>();
		for (Entity entity : entities) {
			if (!(entity instanceof Mob mob)) throw error("Target is not a Mob: " + entity.getName().getString());
			result.add(mob);
		}
		return result;
	}
	private static PmbSkillScheduler.Strategy strategy(String name) {
		return switch (name) {
			case "combat" -> PmbSkillScheduler.Strategy.COMBAT;
			case "retreat" -> PmbSkillScheduler.Strategy.RETREAT;
			case "idle" -> PmbSkillScheduler.Strategy.IDLE;
			default -> throw new IllegalArgumentException(name);
		};
	}

	private static void cancelSelected(Mob mob, String skill) {
		PmbSkillScheduler scheduler = ((PmbSchedulerHolder) mob).pmb$getSkillScheduler();
		switch (skill) {
			case "shield" -> { ((PmbSkillHooks.Shield) mob).pmb$cancelShieldSkill(); ((PmbShieldVulnerableHolder) mob).pmb$setSyncedShieldVulnerableTicks(0); }
			case "wind_charge" -> ((PmbSkillHooks.Wind) mob).pmb$cancelWindSkill();
			case "mace" -> ((PmbSkillHooks.Mace) mob).pmb$cancelMaceSkill();
			case "bow" -> ((PmbSkillHooks.Bow) mob).pmb$cancelBowSkill();
			case "ender_pearl" -> ((PmbSkillHooks.Pearl) mob).pmb$cancelPearlSkill();
			case "traversal", "traversal.wind_charge", "traversal.ender_pearl",
					"fall_rescue", "fall_rescue.wind_charge", "fall_rescue.ender_pearl" -> { }
			default -> throw new IllegalArgumentException(skill);
		}
		String owner = skill.startsWith("traversal.") ? "traversal" :
				skill.startsWith("fall_rescue.") ? "fall_rescue" :
				skill.equals("wind_charge") ? "wind" : skill.equals("ender_pearl") ? "pearl" : skill;
		scheduler.release(mob, owner);
	}

	private static String entityName(Mob mob) { return mob.getName().getString() + " [" + mob.getStringUUID() + "]"; }
	private static CommandSyntaxException error(String message) { return ERROR.create(message); }
	private enum Mode { APPEND, INSERT, MODIFY, DELETE, DELETE_ALL }
	private record Prepared(Mob mob, CompoundTag snapshot) {}
}
