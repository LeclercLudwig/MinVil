package fr.letitan.silence;

import fr.letitan.LeTitan;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Message du serveur au client : un choc violent secoue la vue (de 0 à 1). */
public record SecoussePayload(float force) implements CustomPacketPayload {
	public static final Type<SecoussePayload> TYPE = new Type<>(LeTitan.id("secousse"));
	public static final StreamCodec<ByteBuf, SecoussePayload> CODEC = StreamCodec.composite(
			ByteBufCodecs.FLOAT, SecoussePayload::force, SecoussePayload::new);

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
