package com.pmb.network;

import com.pmb.PortableMobBehaviour;
import com.pmb.ai.PmbAiHolder;
import com.pmb.ai.PmbSchedulerHolder;
import com.pmb.ai.PmbSkillDebugFormatter;
import com.pmb.ai.PmbSkillDebugSnapshot;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.permissions.Permissions;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;

/** Permission-gated PMB scheduler diagnostics for the requester and server log. */
public final class PmbSkillDebugNetworking {
	private static final long REQUEST_INTERVAL_NANOS = TimeUnit.MILLISECONDS.toNanos(250L);
	private static final Map<UUID, Long> LAST_REQUEST = new HashMap<>();

	private PmbSkillDebugNetworking() {}

	public static void initialize() {
		PayloadTypeRegistry.serverboundPlay().register(
				PmbProtocolPackets.SkillDebugRequest.TYPE, PmbProtocolPackets.SkillDebugRequest.CODEC);
		ServerPlayNetworking.registerGlobalReceiver(PmbProtocolPackets.SkillDebugRequest.TYPE,
				(payload, context) -> handle(payload, context));
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> LAST_REQUEST.remove(handler.player.getUUID()));
	}

	private static void handle(PmbProtocolPackets.SkillDebugRequest payload, ServerPlayNetworking.Context context) {
		var player = context.player();
		if (!player.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER)) return;
		long now = System.nanoTime();
		Long previous = LAST_REQUEST.get(player.getUUID());
		if (previous != null && now - previous < REQUEST_INTERVAL_NANOS) return;
		LAST_REQUEST.put(player.getUUID(), now);

		Entity entity = player.level().getEntity(payload.entityId());
		if (!(entity instanceof Mob mob) || mob.level() != player.level()) return;
		if (!player.isWithinEntityInteractionRange(mob, 0.0D) || !player.hasLineOfSight(mob)) return;
		if (!((PmbAiHolder) mob).pmb$getAiData().isConfigured()) return;

		PmbSkillDebugSnapshot snapshot = ((PmbSchedulerHolder) mob).pmb$getSkillScheduler().lastDebugSnapshot();
		String entityType = BuiltInRegistries.ENTITY_TYPE.getKey(mob.getType()).toString();
		if (snapshot == null) {
			PortableMobBehaviour.LOGGER.info("{}", PmbSkillDebugFormatter.formatUnavailableForLog(
					entityType, mob.getId(), mob.getUUID()));
			player.sendSystemMessage(PmbSkillDebugFormatter.formatUnavailableForChat(
					entityType, mob.getId(), mob.getUUID()));
			return;
		}
		PortableMobBehaviour.LOGGER.info("{}", snapshot.formatForLog(mob.tickCount));
		player.sendSystemMessage(snapshot.formatForChat(mob.tickCount));
	}
}
