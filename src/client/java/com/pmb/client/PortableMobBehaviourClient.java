package com.pmb.client;

import com.pmb.network.PmbProtocolNetworking;
import com.pmb.network.PmbProtocolPackets;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientConfigurationConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientConfigurationNetworking;
import net.minecraft.network.chat.Component;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.EntityHitResult;
import com.mojang.blaze3d.platform.InputConstants;
import org.lwjgl.glfw.GLFW;

public class PortableMobBehaviourClient implements ClientModInitializer {
	private static final KeyMapping SKILL_DEBUG = KeyMappingHelper.registerKeyMapping(new KeyMapping(
			"key.portable-mob-behaviour.skill_debug", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_K,
			KeyMapping.Category.MISC));

	@Override
	public void onInitializeClient() {
		ClientConfigurationNetworking.registerGlobalReceiver(PmbProtocolPackets.Challenge.TYPE,
				(payload, context) -> context.responseSender().sendPacket(
						new PmbProtocolPackets.Response(PmbProtocolNetworking.NETWORK_PROTOCOL)));

		ClientConfigurationConnectionEvents.START.register((listener, client) -> {
			if (!ClientConfigurationNetworking.canSend(PmbProtocolPackets.Response.TYPE)) {
				ClientConfigurationNetworking.getSender().disconnect(Component.literal(
						"Portable Mob Behaviour is required on the server (network protocol "
								+ PmbProtocolNetworking.NETWORK_PROTOCOL + ")."));
			}
		});

		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			while (SKILL_DEBUG.consumeClick()) requestSkillDebug(client);
		});
	}

	private static void requestSkillDebug(Minecraft client) {
		if (!(client.hitResult instanceof EntityHitResult hit)
				|| !ClientPlayNetworking.canSend(PmbProtocolPackets.SkillDebugRequest.TYPE)) return;
		ClientPlayNetworking.send(new PmbProtocolPackets.SkillDebugRequest(hit.getEntity().getId()));
	}
}
