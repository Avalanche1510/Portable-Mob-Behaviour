package com.pmb.network;

import java.util.function.Consumer;

import com.pmb.PortableMobBehaviour;
import net.fabricmc.fabric.api.networking.v1.FabricServerConfigurationPacketListenerImpl;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerConfigurationConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerConfigurationNetworking;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.server.network.ConfigurationTask;

public final class PmbProtocolNetworking {
	public static final int NETWORK_PROTOCOL = 1;
	public static final ConfigurationTask.Type TASK_TYPE = new ConfigurationTask.Type(
			PortableMobBehaviour.MOD_ID + ":protocol");

	private PmbProtocolNetworking() {}

	public static void initialize() {
		PayloadTypeRegistry.clientboundConfiguration().register(
				PmbProtocolPackets.Challenge.TYPE, PmbProtocolPackets.Challenge.CODEC);
		PayloadTypeRegistry.serverboundConfiguration().register(
				PmbProtocolPackets.Response.TYPE, PmbProtocolPackets.Response.CODEC);

		ServerConfigurationNetworking.registerGlobalReceiver(PmbProtocolPackets.Response.TYPE,
				(payload, context) -> {
					if (payload.protocol() != NETWORK_PROTOCOL) {
						context.responseSender().disconnect(Component.literal(
								"Portable Mob Behaviour network protocol mismatch. Server: "
										+ NETWORK_PROTOCOL + ", client: " + payload.protocol()));
						return;
					}
					try {
						((FabricServerConfigurationPacketListenerImpl) context.packetListener())
								.completeTask(TASK_TYPE);
					} catch (IllegalStateException exception) {
						context.responseSender().disconnect(Component.literal(
								"Portable Mob Behaviour received an unexpected protocol response."));
					}
				});

		ServerConfigurationConnectionEvents.CONFIGURE.register((listener, server) -> {
			if (!ServerConfigurationNetworking.canSend(listener, PmbProtocolPackets.Challenge.TYPE)) {
				listener.disconnect(Component.literal(
						"Portable Mob Behaviour is required on the client (network protocol "
								+ NETWORK_PROTOCOL + ")."));
				return;
			}
			((FabricServerConfigurationPacketListenerImpl) listener).addTask(new ProtocolTask());
		});
	}

	private static final class ProtocolTask implements ConfigurationTask {
		@Override
		public void start(Consumer<Packet<?>> sender) {
			sender.accept(ServerConfigurationNetworking.createClientboundPacket(
					new PmbProtocolPackets.Challenge(NETWORK_PROTOCOL)));
		}

		@Override
		public Type type() {
			return TASK_TYPE;
		}
	}
}
