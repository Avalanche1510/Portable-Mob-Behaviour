package com.pmb.client;

import com.pmb.network.PmbProtocolNetworking;
import com.pmb.network.PmbProtocolPackets;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientConfigurationConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientConfigurationNetworking;
import net.minecraft.network.chat.Component;

public class PortableMobBehaviourClient implements ClientModInitializer {
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
	}
}
