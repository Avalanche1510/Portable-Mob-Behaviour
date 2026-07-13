package com.pmb.network;

import com.pmb.PortableMobBehaviour;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public final class PmbProtocolPackets {
	private PmbProtocolPackets() {}

	public record Challenge(int protocol) implements CustomPacketPayload {
		public static final Type<Challenge> TYPE = new Type<>(PortableMobBehaviour.id("protocol_challenge"));
		public static final StreamCodec<FriendlyByteBuf, Challenge> CODEC = StreamCodec.composite(
				ByteBufCodecs.INT, Challenge::protocol, Challenge::new);

		@Override
		public Type<? extends CustomPacketPayload> type() {
			return TYPE;
		}
	}

	public record Response(int protocol) implements CustomPacketPayload {
		public static final Type<Response> TYPE = new Type<>(PortableMobBehaviour.id("protocol_response"));
		public static final StreamCodec<FriendlyByteBuf, Response> CODEC = StreamCodec.composite(
				ByteBufCodecs.INT, Response::protocol, Response::new);

		@Override
		public Type<? extends CustomPacketPayload> type() {
			return TYPE;
		}
	}
}
