package fr.letitan.silence;

import fr.letitan.LeTitan;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Message du serveur au client : où est le Titan muet (pour le dessiner), ce qu'il fait, quels
 * kystes luisent encore, et de combien il s'est effondré.
 */
public record MuetPayload(int phase, long temps, double x, double z, double cap, double altitude, int cote, int kystes,
		float effondrement) implements CustomPacketPayload {
	public static final Type<MuetPayload> TYPE = new Type<>(LeTitan.id("titan_muet"));
	public static final StreamCodec<ByteBuf, MuetPayload> CODEC = StreamCodec.of((buf, p) -> {
		ByteBufCodecs.VAR_INT.encode(buf, p.phase);
		ByteBufCodecs.VAR_LONG.encode(buf, p.temps);
		buf.writeDouble(p.x);
		buf.writeDouble(p.z);
		buf.writeDouble(p.cap);
		buf.writeDouble(p.altitude);
		buf.writeByte(p.cote);
		buf.writeByte(p.kystes);
		buf.writeFloat(p.effondrement);
	}, buf -> new MuetPayload(ByteBufCodecs.VAR_INT.decode(buf), ByteBufCodecs.VAR_LONG.decode(buf), buf.readDouble(),
			buf.readDouble(), buf.readDouble(), buf.readDouble(), buf.readByte(), buf.readByte(), buf.readFloat()));

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
