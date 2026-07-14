package com.pmb.faction;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.command.v2.ArgumentTypeRegistry;
import com.pmb.PortableMobBehaviour;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.CompoundTagArgument;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.Permissions;
import net.minecraft.commands.synchronization.SingletonArgumentInfo;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class PmbFactionCommands {
	private static final DynamicCommandExceptionType ERROR = new DynamicCommandExceptionType(
			value -> Component.literal(String.valueOf(value)));
	private static final Set<String> TARGET_KEYS = Set.of("factions", "teams", "types", "tags", "reputation", "roles");

	private PmbFactionCommands() {
	}

	public static void initialize() {
		ArgumentTypeRegistry.registerArgumentType(PortableMobBehaviour.id("faction_name"),
				PmbFactionNameArgument.class,
				SingletonArgumentInfo.contextFree(PmbFactionNameArgument::factionName));
		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> register(dispatcher));
	}

	private static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
		dispatcher.register(Commands.literal("pmb")
				.requires(source -> source.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER))
				.then(Commands.literal("faction")
						.then(Commands.literal("create")
								.then(Commands.argument("faction", PmbFactionNameArgument.factionName())
										.executes(context -> create(context.getSource(), id(context, "faction")))))
						.then(Commands.literal("delete")
								.then(Commands.argument("faction", PmbFactionNameArgument.factionName())
										.executes(context -> delete(context.getSource(), id(context, "faction")))))
						.then(Commands.literal("list").executes(context -> list(context.getSource())))
						.then(Commands.literal("info")
								.then(Commands.argument("faction", PmbFactionNameArgument.factionName())
										.executes(context -> info(context.getSource(), id(context, "faction")))))
						.then(ruleCommands())
						.then(compatCommands())
						.then(relationshipCommands())
						.then(memberCommands())
						.then(reputationCommands())
						.then(roleCommands())));
	}

	private static com.mojang.brigadier.builder.LiteralArgumentBuilder<CommandSourceStack> ruleCommands() {
		return Commands.literal("rule").then(Commands.literal("set")
				.then(booleanRule("allowInternalConflict"))
				.then(booleanRule("allowFriendlyFire"))
				.then(booleanRule("groupRevenge"))
				.then(booleanRule("overrideTeamRules"))
				.then(Commands.literal("defaultAttitude")
						.then(Commands.argument("faction", PmbFactionNameArgument.factionName())
								.then(Commands.argument("attitude", StringArgumentType.word())
										.executes(context -> setAttitudeRule(context.getSource(), id(context, "faction"),
												StringArgumentType.getString(context, "attitude"))))))
				.then(Commands.literal("evasiveSpeedMultiplier")
						.then(Commands.argument("faction", PmbFactionNameArgument.factionName())
								.then(Commands.argument("multiplier", FloatArgumentType.floatArg(0.0F, 100.0F))
										.executes(context -> setSpeedRule(context.getSource(), id(context, "faction"),
												FloatArgumentType.getFloat(context, "multiplier")))))));
	}

	private static com.mojang.brigadier.builder.LiteralArgumentBuilder<CommandSourceStack> booleanRule(String rule) {
		return Commands.literal(rule)
				.then(Commands.argument("faction", PmbFactionNameArgument.factionName())
						.then(Commands.argument("boolean", StringArgumentType.word())
								.executes(context -> setBooleanRule(context.getSource(), id(context, "faction"), rule,
										parseBoolean(StringArgumentType.getString(context, "boolean"))))));
	}

	private static com.mojang.brigadier.builder.LiteralArgumentBuilder<CommandSourceStack> compatCommands() {
		return Commands.literal("compat").then(Commands.literal("set")
				.then(Commands.literal("piglin")
						.then(booleanCompatRule("greed"))
						.then(booleanCompatRule("guarding"))
						.then(booleanCompatRule("avoidance"))));
	}

	private static com.mojang.brigadier.builder.LiteralArgumentBuilder<CommandSourceStack> booleanCompatRule(String rule) {
		return Commands.literal(rule)
				.then(Commands.argument("faction", PmbFactionNameArgument.factionName())
						.then(Commands.argument("boolean", StringArgumentType.word())
								.executes(context -> setBooleanCompatRule(context.getSource(), id(context, "faction"),
										rule, parseBoolean(StringArgumentType.getString(context, "boolean"))))));
	}

	private static com.mojang.brigadier.builder.LiteralArgumentBuilder<CommandSourceStack> relationshipCommands() {
		return Commands.literal("relationship")
				.then(Commands.literal("add").then(relationshipArguments(false)))
				.then(Commands.literal("insert")
						.then(Commands.argument("faction", PmbFactionNameArgument.factionName())
								.then(Commands.argument("index", IntegerArgumentType.integer(0))
										.then(Commands.argument("attitude", StringArgumentType.word())
												.then(Commands.argument("target", CompoundTagArgument.compoundTag())
														.executes(context -> putRelationship(context.getSource(), id(context, "faction"),
																IntegerArgumentType.getInteger(context, "index"), false,
																StringArgumentType.getString(context, "attitude"),
																CompoundTagArgument.getCompoundTag(context, "target"))))))))
				.then(Commands.literal("replace")
						.then(Commands.argument("faction", PmbFactionNameArgument.factionName())
								.then(Commands.argument("index", IntegerArgumentType.integer(0))
										.then(Commands.argument("attitude", StringArgumentType.word())
												.then(Commands.argument("target", CompoundTagArgument.compoundTag())
														.executes(context -> putRelationship(context.getSource(), id(context, "faction"),
																IntegerArgumentType.getInteger(context, "index"), true,
																StringArgumentType.getString(context, "attitude"),
																CompoundTagArgument.getCompoundTag(context, "target"))))))))
				.then(Commands.literal("remove")
						.then(Commands.argument("faction", PmbFactionNameArgument.factionName())
								.then(Commands.argument("index", IntegerArgumentType.integer(0))
										.executes(context -> removeRelationship(context.getSource(), id(context, "faction"),
												IntegerArgumentType.getInteger(context, "index"))))))
				.then(Commands.literal("move")
						.then(Commands.argument("faction", PmbFactionNameArgument.factionName())
								.then(Commands.argument("from", IntegerArgumentType.integer(0))
										.then(Commands.argument("to", IntegerArgumentType.integer(0))
												.executes(context -> moveRelationship(context.getSource(), id(context, "faction"),
														IntegerArgumentType.getInteger(context, "from"),
														IntegerArgumentType.getInteger(context, "to")))))))
				.then(Commands.literal("list")
						.then(Commands.argument("faction", PmbFactionNameArgument.factionName())
								.executes(context -> listRelationships(context.getSource(), id(context, "faction")))));
	}

	private static com.mojang.brigadier.builder.RequiredArgumentBuilder<CommandSourceStack, String> relationshipArguments(
			boolean replace) {
		return Commands.argument("faction", PmbFactionNameArgument.factionName())
				.then(Commands.argument("attitude", StringArgumentType.word())
						.then(Commands.argument("target", CompoundTagArgument.compoundTag())
								.executes(context -> putRelationship(context.getSource(), id(context, "faction"), -1,
										replace, StringArgumentType.getString(context, "attitude"),
										CompoundTagArgument.getCompoundTag(context, "target")))));
	}

	private static com.mojang.brigadier.builder.LiteralArgumentBuilder<CommandSourceStack> memberCommands() {
		return Commands.literal("member")
				.then(Commands.literal("set")
						.then(Commands.argument("faction", PmbFactionNameArgument.factionName())
								.then(Commands.argument("entities", EntityArgument.entities())
										.executes(context -> setMembers(context.getSource(), id(context, "faction"),
												EntityArgument.getEntities(context, "entities"))))))
				.then(Commands.literal("clear")
						.then(Commands.argument("entities", EntityArgument.entities())
								.executes(context -> clearMembers(context.getSource(), EntityArgument.getEntities(context, "entities")))))
				.then(Commands.literal("get")
						.then(Commands.argument("entity", EntityArgument.entity())
								.executes(context -> getMember(context.getSource(), EntityArgument.getEntity(context, "entity")))));
	}

	private static com.mojang.brigadier.builder.LiteralArgumentBuilder<CommandSourceStack> reputationCommands() {
		return Commands.literal("reputation")
				.then(Commands.literal("set").then(reputationMutation(false)))
				.then(Commands.literal("add").then(reputationMutation(true)))
				.then(Commands.literal("get")
						.then(Commands.argument("faction", PmbFactionNameArgument.factionName())
								.then(Commands.argument("players", EntityArgument.players())
										.executes(context -> getReputation(context.getSource(), id(context, "faction"),
												EntityArgument.getPlayers(context, "players"))))));
	}

	private static com.mojang.brigadier.builder.RequiredArgumentBuilder<CommandSourceStack, String> reputationMutation(
			boolean add) {
		return Commands.argument("faction", PmbFactionNameArgument.factionName())
				.then(Commands.argument("players", EntityArgument.players())
						.then(Commands.argument("value", IntegerArgumentType.integer())
								.executes(context -> mutateReputation(context.getSource(), id(context, "faction"),
										EntityArgument.getPlayers(context, "players"),
										IntegerArgumentType.getInteger(context, "value"), add))));
	}

	private static com.mojang.brigadier.builder.LiteralArgumentBuilder<CommandSourceStack> roleCommands() {
		return Commands.literal("role")
				.then(Commands.literal("add").then(roleMutation(true)))
				.then(Commands.literal("remove").then(roleMutation(false)))
				.then(Commands.literal("clear")
						.then(Commands.argument("faction", PmbFactionNameArgument.factionName())
								.then(Commands.argument("players", EntityArgument.players())
										.executes(context -> clearRoles(context.getSource(), id(context, "faction"),
												EntityArgument.getPlayers(context, "players"))))))
				.then(Commands.literal("list")
						.then(Commands.argument("faction", PmbFactionNameArgument.factionName())
								.then(Commands.argument("players", EntityArgument.players())
										.executes(context -> listRoles(context.getSource(), id(context, "faction"),
												EntityArgument.getPlayers(context, "players"))))));
	}

	private static com.mojang.brigadier.builder.RequiredArgumentBuilder<CommandSourceStack, String> roleMutation(
			boolean add) {
		return Commands.argument("faction", PmbFactionNameArgument.factionName())
				.then(Commands.argument("players", EntityArgument.players())
						.then(Commands.argument("role", StringArgumentType.word())
								.executes(context -> mutateRole(context.getSource(), id(context, "faction"),
										EntityArgument.getPlayers(context, "players"),
										StringArgumentType.getString(context, "role"), add))));
	}

	private static int create(CommandSourceStack source, String id) throws CommandSyntaxException {
		PmbFactionSavedData data = data(source);
		if (!data.create(id)) throw error("Faction already exists: " + id);
		return success(source, "Created faction " + id, 1);
	}

	private static int delete(CommandSourceStack source, String id) throws CommandSyntaxException {
		PmbFactionSavedData data = data(source);
		if (!data.delete(id)) throw error("Unknown faction: " + id);
		int cleared = clearDeletedMemberships(source.getServer(), id);
		return success(source, "Deleted faction " + id + "; cleared " + cleared + " loaded members", cleared + 1);
	}

	private static int list(CommandSourceStack source) {
		String values = data(source).factions().keySet().stream().sorted()
				.reduce((a, b) -> a + ", " + b).orElse("<none>");
		return success(source, "Factions: " + values, data(source).factions().size());
	}

	private static int info(CommandSourceStack source, String id) throws CommandSyntaxException {
		PmbFactionDefinition definition = requireFaction(source, id);
		PmbFactionRules rules = definition.rules();
		PmbVanillaCompatRules compat = definition.vanillaCompatRules();
		return success(source, id + " Rules{allowInternalConflict:" + rules.allowInternalConflict()
				+ ",allowFriendlyFire:" + rules.allowFriendlyFire() + ",groupRevenge:" + rules.groupRevenge()
				+ ",overrideTeamRules:" + rules.overrideTeamRules() + ",defaultAttitude:"
				+ rules.defaultAttitude().serializedName() + ",evasiveSpeedMultiplier:" + rules.evasiveSpeedMultiplier()
				+ "} VanillaCompatRules{piglin:{greed:" + compat.piglin().greed()
				+ ",guarding:" + compat.piglin().guarding() + ",avoidance:" + compat.piglin().avoidance() + "}"
				+ "} Relationships:" + definition.relationships().size() + " Players:" + definition.players().size(), 1);
	}

	private static int setBooleanCompatRule(CommandSourceStack source, String id, String rule, boolean value)
			throws CommandSyntaxException {
		PmbVanillaCompatRules old = requireFaction(source, id).vanillaCompatRules();
		PmbPiglinCompatRules piglin = old.piglin();
		PmbVanillaCompatRules updated = switch (rule) {
			case "greed" -> new PmbVanillaCompatRules(
					new PmbPiglinCompatRules(value, piglin.guarding(), piglin.avoidance()));
			case "guarding" -> new PmbVanillaCompatRules(
					new PmbPiglinCompatRules(piglin.greed(), value, piglin.avoidance()));
			case "avoidance" -> new PmbVanillaCompatRules(
					new PmbPiglinCompatRules(piglin.greed(), piglin.guarding(), value));
			default -> throw error("Unknown vanilla compatibility rule: " + rule);
		};
		data(source).update(id, faction -> faction.withVanillaCompatRules(updated));
		return success(source, "Set " + id + " VanillaCompatRules.piglin." + rule + " to " + value, 1);
	}

	private static int setBooleanRule(CommandSourceStack source, String id, String rule, boolean value)
			throws CommandSyntaxException {
		PmbFactionRules old = requireFaction(source, id).rules();
		PmbFactionRules updated = switch (rule) {
			case "allowInternalConflict" -> new PmbFactionRules(value, old.allowFriendlyFire(), old.groupRevenge(),
					old.overrideTeamRules(), old.defaultAttitude(), old.evasiveSpeedMultiplier());
			case "allowFriendlyFire" -> new PmbFactionRules(old.allowInternalConflict(), value, old.groupRevenge(),
					old.overrideTeamRules(), old.defaultAttitude(), old.evasiveSpeedMultiplier());
			case "groupRevenge" -> new PmbFactionRules(old.allowInternalConflict(), old.allowFriendlyFire(), value,
					old.overrideTeamRules(), old.defaultAttitude(), old.evasiveSpeedMultiplier());
			case "overrideTeamRules" -> new PmbFactionRules(old.allowInternalConflict(), old.allowFriendlyFire(),
					old.groupRevenge(), value, old.defaultAttitude(), old.evasiveSpeedMultiplier());
			default -> throw error("Unknown rule: " + rule);
		};
		data(source).update(id, faction -> faction.withRules(updated));
		return success(source, "Set " + id + " " + rule + " to " + value, 1);
	}

	private static int setAttitudeRule(CommandSourceStack source, String id, String value) throws CommandSyntaxException {
		PmbFactionRules old = requireFaction(source, id).rules();
		PmbFactionAttitude attitude = parseAttitude(value);
		PmbFactionRules updated = new PmbFactionRules(old.allowInternalConflict(), old.allowFriendlyFire(),
				old.groupRevenge(), old.overrideTeamRules(), attitude, old.evasiveSpeedMultiplier());
		data(source).update(id, faction -> faction.withRules(updated));
		return success(source, "Set " + id + " defaultAttitude to " + attitude.serializedName(), 1);
	}

	private static int setSpeedRule(CommandSourceStack source, String id, float value) throws CommandSyntaxException {
		PmbFactionRules old = requireFaction(source, id).rules();
		PmbFactionRules updated = new PmbFactionRules(old.allowInternalConflict(), old.allowFriendlyFire(),
				old.groupRevenge(), old.overrideTeamRules(), old.defaultAttitude(), value);
		data(source).update(id, faction -> faction.withRules(updated));
		return success(source, "Set " + id + " evasiveSpeedMultiplier to " + value, 1);
	}

	private static int putRelationship(CommandSourceStack source, String id, int index, boolean replace,
			String attitudeName, CompoundTag targetTag) throws CommandSyntaxException {
		PmbFactionDefinition definition = requireFaction(source, id);
		PmbFactionRelationship relationship = new PmbFactionRelationship(parseAttitude(attitudeName),
				parseTarget(source, targetTag));
		List<PmbFactionRelationship> relationships = new ArrayList<>(definition.relationships());
		if (index < 0) {
			index = relationships.size();
		}
		if (replace) {
			requireIndex(index, relationships.size());
			relationships.set(index, relationship);
		} else {
			if (index > relationships.size()) throw error("Relationship index out of range: " + index);
			relationships.add(index, relationship);
		}
		int resultIndex = index;
		data(source).update(id, faction -> faction.withRelationships(relationships));
		return success(source, (replace ? "Replaced" : "Added") + " relationship " + resultIndex + " in " + id, 1);
	}

	private static int removeRelationship(CommandSourceStack source, String id, int index) throws CommandSyntaxException {
		List<PmbFactionRelationship> relationships = new ArrayList<>(requireFaction(source, id).relationships());
		requireIndex(index, relationships.size());
		relationships.remove(index);
		data(source).update(id, faction -> faction.withRelationships(relationships));
		return success(source, "Removed relationship " + index + " from " + id, 1);
	}

	private static int moveRelationship(CommandSourceStack source, String id, int from, int to)
			throws CommandSyntaxException {
		List<PmbFactionRelationship> relationships = new ArrayList<>(requireFaction(source, id).relationships());
		requireIndex(from, relationships.size());
		requireIndex(to, relationships.size());
		PmbFactionRelationship relationship = relationships.remove(from);
		relationships.add(to, relationship);
		data(source).update(id, faction -> faction.withRelationships(relationships));
		return success(source, "Moved relationship " + from + " to " + to + " in " + id, 1);
	}

	private static int listRelationships(CommandSourceStack source, String id) throws CommandSyntaxException {
		List<PmbFactionRelationship> relationships = requireFaction(source, id).relationships();
		if (relationships.isEmpty()) {
			return success(source, id + " has no relationships", 0);
		}
		for (int i = 0; i < relationships.size(); i++) {
			PmbFactionRelationship relationship = relationships.get(i);
			int index = i;
			source.sendSuccess(() -> Component.literal("[" + index + "] "
					+ relationship.attitude().serializedName() + " " + relationship.target()), false);
		}
		return relationships.size();
	}

	private static int setMembers(CommandSourceStack source, String id, Collection<? extends Entity> entities)
			throws CommandSyntaxException {
		requireFaction(source, id);
		List<LivingEntity> living = requireLiving(entities);
		living.forEach(entity -> ((PmbFactionHolder) entity).pmb$setFactionId(id));
		return success(source, "Assigned " + living.size() + " living entities to " + id, living.size());
	}

	private static int clearMembers(CommandSourceStack source, Collection<? extends Entity> entities)
			throws CommandSyntaxException {
		List<LivingEntity> living = requireLiving(entities);
		living.forEach(entity -> ((PmbFactionHolder) entity).pmb$setFactionId(null));
		return success(source, "Cleared faction from " + living.size() + " living entities", living.size());
	}

	private static int getMember(CommandSourceStack source, Entity entity) throws CommandSyntaxException {
		if (!(entity instanceof LivingEntity living)) throw error("Entity is not a LivingEntity: " + entity.getName().getString());
		String id = PmbFactionResolver.factionOf(living);
		return success(source, living.getName().getString() + " faction: " + (id == null ? "<none>" : id), id == null ? 0 : 1);
	}

	private static int mutateReputation(CommandSourceStack source, String id, Collection<ServerPlayer> players,
			int value, boolean add) throws CommandSyntaxException {
		PmbFactionDefinition definition = requireFaction(source, id);
		Map<String, PmbFactionPlayerData> records = new LinkedHashMap<>(definition.players());
		for (ServerPlayer player : players) {
			String uuid = player.getUUID().toString();
			PmbFactionPlayerData old = records.getOrDefault(uuid, PmbFactionPlayerData.DEFAULT);
			int updated;
			try {
				updated = add ? Math.addExact(old.reputation(), value) : value;
			} catch (ArithmeticException exception) {
				throw error("Reputation overflow for " + player.getName().getString());
			}
			records.put(uuid, new PmbFactionPlayerData(updated, old.roles()));
		}
		data(source).update(id, faction -> faction.withPlayers(records));
		return success(source, (add ? "Added reputation for " : "Set reputation for ") + players.size() + " players", players.size());
	}

	private static int getReputation(CommandSourceStack source, String id, Collection<ServerPlayer> players)
			throws CommandSyntaxException {
		PmbFactionDefinition definition = requireFaction(source, id);
		for (ServerPlayer player : players) {
			int value = definition.players().getOrDefault(player.getUUID().toString(), PmbFactionPlayerData.DEFAULT).reputation();
			source.sendSuccess(() -> Component.literal(player.getName().getString() + " reputation in " + id + ": " + value), false);
		}
		return players.size();
	}

	private static int mutateRole(CommandSourceStack source, String id, Collection<ServerPlayer> players,
			String role, boolean add) throws CommandSyntaxException {
		if (role.isBlank()) throw error("Role cannot be empty");
		PmbFactionDefinition definition = requireFaction(source, id);
		Map<String, PmbFactionPlayerData> records = new LinkedHashMap<>(definition.players());
		for (ServerPlayer player : players) {
			String uuid = player.getUUID().toString();
			PmbFactionPlayerData old = records.getOrDefault(uuid, PmbFactionPlayerData.DEFAULT);
			Set<String> roles = new HashSet<>(old.roles());
			if (add) roles.add(role); else roles.remove(role);
			records.put(uuid, new PmbFactionPlayerData(old.reputation(), roles.stream().sorted().toList()));
		}
		data(source).update(id, faction -> faction.withPlayers(records));
		return success(source, (add ? "Added role " : "Removed role ") + role + " for " + players.size() + " players", players.size());
	}

	private static int clearRoles(CommandSourceStack source, String id, Collection<ServerPlayer> players)
			throws CommandSyntaxException {
		PmbFactionDefinition definition = requireFaction(source, id);
		Map<String, PmbFactionPlayerData> records = new LinkedHashMap<>(definition.players());
		for (ServerPlayer player : players) {
			String uuid = player.getUUID().toString();
			PmbFactionPlayerData old = records.getOrDefault(uuid, PmbFactionPlayerData.DEFAULT);
			records.put(uuid, new PmbFactionPlayerData(old.reputation(), List.of()));
		}
		data(source).update(id, faction -> faction.withPlayers(records));
		return success(source, "Cleared roles for " + players.size() + " players", players.size());
	}

	private static int listRoles(CommandSourceStack source, String id, Collection<ServerPlayer> players)
			throws CommandSyntaxException {
		PmbFactionDefinition definition = requireFaction(source, id);
		for (ServerPlayer player : players) {
			List<String> roles = definition.players().getOrDefault(player.getUUID().toString(), PmbFactionPlayerData.DEFAULT).roles();
			source.sendSuccess(() -> Component.literal(player.getName().getString() + " roles in " + id + ": "
					+ (roles.isEmpty() ? "<none>" : String.join(", ", roles))), false);
		}
		return players.size();
	}

	private static PmbFactionTarget parseTarget(CommandSourceStack source, CompoundTag tag) throws CommandSyntaxException {
		for (String key : tag.keySet()) {
			if (!TARGET_KEYS.contains(key)) throw error("Unknown relationship target key: " + key);
		}
		PmbFactionTarget target = new PmbFactionTarget(readStrings(tag, "factions"), readStrings(tag, "teams"),
				readStrings(tag, "types"), readStrings(tag, "tags"), readStrings(tag, "reputation"),
				readStrings(tag, "roles"));
		if (target.isEmpty()) throw error("Relationship target cannot be empty");
		PmbFactionSavedData data = data(source);
		for (String value : target.factions()) {
			if (!data.contains(value)) throw error("Unknown target faction: " + value);
		}
		for (String value : target.teams()) {
			if (source.getServer().getScoreboard().getPlayerTeam(value) == null) throw error("Unknown team: " + value);
		}
		for (String value : target.types()) validateType(value);
		for (String value : target.reputation()) {
			try { PmbReputationRange.parse(value); }
			catch (IllegalArgumentException exception) { throw error(exception.getMessage()); }
		}
		if (target.tags().stream().anyMatch(String::isBlank) || target.roles().stream().anyMatch(String::isBlank)) {
			throw error("Tags and roles cannot contain empty strings");
		}
		return target;
	}

	private static List<String> readStrings(CompoundTag tag, String key) throws CommandSyntaxException {
		if (!tag.contains(key)) return List.of();
		ListTag list = tag.getListOrEmpty(key);
		List<String> result = new ArrayList<>(list.size());
		for (int i = 0; i < list.size(); i++) {
			var value = list.getString(i);
			if (value.isEmpty()) throw error("Target " + key + " must be a string list");
			result.add(value.get());
		}
		return result;
	}

	private static void validateType(String value) throws CommandSyntaxException {
		boolean tag = value.startsWith("#");
		Identifier id = Identifier.tryParse(tag ? value.substring(1) : value);
		if (id == null) throw error("Invalid entity type or tag: " + value);
		if (tag) {
			boolean exists = BuiltInRegistries.ENTITY_TYPE.getTags().anyMatch(named -> named.key().location().equals(id));
			if (!exists) throw error("Unknown entity type tag: " + value);
		} else if (!BuiltInRegistries.ENTITY_TYPE.containsKey(id)) {
			throw error("Unknown entity type: " + value);
		}
	}

	private static List<LivingEntity> requireLiving(Collection<? extends Entity> entities) throws CommandSyntaxException {
		List<LivingEntity> result = new ArrayList<>();
		for (Entity entity : entities) {
			if (!(entity instanceof LivingEntity living)) throw error("Selection contains a non-living entity: " + entity.getName().getString());
			result.add(living);
		}
		return result;
	}

	private static int clearDeletedMemberships(MinecraftServer server, String id) {
		int count = 0;
		for (ServerLevel level : server.getAllLevels()) {
			for (Entity entity : level.getAllEntities()) {
				if (entity instanceof LivingEntity living && id.equals(PmbFactionResolver.factionOf(living))) {
					((PmbFactionHolder) living).pmb$setFactionId(null);
					count++;
				}
			}
		}
		return count;
	}

	private static PmbFactionDefinition requireFaction(CommandSourceStack source, String id) throws CommandSyntaxException {
		PmbFactionDefinition definition = data(source).get(id);
		if (definition == null) throw error("Unknown faction: " + id);
		return definition;
	}

	private static PmbFactionAttitude parseAttitude(String value) throws CommandSyntaxException {
		try { return PmbFactionAttitude.parse(value); }
		catch (IllegalArgumentException exception) { throw error(exception.getMessage()); }
	}

	private static boolean parseBoolean(String value) throws CommandSyntaxException {
		return switch (value.toLowerCase(java.util.Locale.ROOT)) {
			case "true", "1b" -> true;
			case "false", "0b" -> false;
			default -> throw error("Expected boolean true, false, 1b, or 0b; got: " + value);
		};
	}

	private static void requireIndex(int index, int size) throws CommandSyntaxException {
		if (index < 0 || index >= size) throw error("Relationship index out of range: " + index);
	}

	private static PmbFactionSavedData data(CommandSourceStack source) {
		return PmbFactionSavedData.get(source.getServer());
	}

	private static String id(com.mojang.brigadier.context.CommandContext<CommandSourceStack> context, String name) {
		return PmbFactionNameArgument.getFactionName(context, name);
	}

	private static CommandSyntaxException error(String message) {
		return ERROR.create(message);
	}

	private static int success(CommandSourceStack source, String message, int result) {
		source.sendSuccess(() -> Component.literal(message), true);
		return result;
	}
}
