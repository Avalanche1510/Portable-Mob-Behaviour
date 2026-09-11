package com.pmb.command;

import com.mojang.brigadier.CommandDispatcher;
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
import com.pmb.ai.PmbSkillSchema;
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
		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> register(dispatcher));
	}

	private static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
		RequiredArgumentBuilder<CommandSourceStack, ?> targets = Commands.argument("targets", EntityArgument.entities());
		for (PmbSkillSchema schema : PmbSkillSchema.all()) targets.then(skillBranch(schema));
		dispatcher.register(Commands.literal("pmb")
				.requires(source -> source.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER))
				.then(Commands.literal("skills").then(targets)));
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
			if (mode == Mode.DELETE && patch.keys().isEmpty()) {
				ai.remove(schema.id());
			} else {
				CompoundTag node = ai.getCompound(schema.id()).orElseGet(() -> {
					CompoundTag created = new CompoundTag(); ai.put(schema.id(), created); return created;
				});
				if (mode == Mode.DELETE_ALL) for (String key : List.copyOf(node.keySet())) node.remove(key);
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
		CompoundTag node = snapshot.getCompoundOrEmpty(PmbAiData.TAG).getCompoundOrEmpty(schema.id());
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

	private static void cancelSelected(Mob mob, String skill) {
		PmbSkillScheduler scheduler = ((PmbSchedulerHolder) mob).pmb$getSkillScheduler();
		switch (skill) {
			case "shield" -> { ((PmbSkillHooks.Shield) mob).pmb$cancelShieldSkill(); ((PmbShieldVulnerableHolder) mob).pmb$setSyncedShieldVulnerableTicks(0); }
			case "wind_charge" -> ((PmbSkillHooks.Wind) mob).pmb$cancelWindSkill();
			case "mace" -> ((PmbSkillHooks.Mace) mob).pmb$cancelMaceSkill();
			case "bow" -> ((PmbSkillHooks.Bow) mob).pmb$cancelBowSkill();
			case "ender_pearl" -> ((PmbSkillHooks.Pearl) mob).pmb$cancelPearlSkill();
			default -> throw new IllegalArgumentException(skill);
		}
		scheduler.release(mob, skill.equals("wind_charge") ? "wind" : skill.equals("ender_pearl") ? "pearl" : skill);
	}

	private static String entityName(Mob mob) { return mob.getName().getString() + " [" + mob.getStringUUID() + "]"; }
	private static CommandSyntaxException error(String message) { return ERROR.create(message); }
	private enum Mode { APPEND, INSERT, MODIFY, DELETE, DELETE_ALL }
	private record Prepared(Mob mob, CompoundTag snapshot) {}
}
