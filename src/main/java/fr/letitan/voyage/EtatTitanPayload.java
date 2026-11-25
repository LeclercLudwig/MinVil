package fr.letitan.voyage;

import fr.letitan.LeTitan;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Message du serveur au client : où en est le Titan au temps de jeu {@code temps}.
 * Le client prolonge sa marche jusqu'au message suivant (voir {@link Navigation#prolonger}),
 * pour faire défiler le sol et les nuages sans à-coups, et pour le dessiner vu du sol.
 */
public record EtatTitanPayload(boolean marche, long temps, double odometre, double x, double z,
		double cap, double capVise, double altitude) implements CustomPacketPayload {
	public static final Type<EtatTitanPayload> TYPE = new Type<>(LeTitan.id("etat"));
	public static final StreamCodec<ByteBuf, EtatTitanPayload> CODEC = StreamCodec.composite(
			ByteBufCodecs.BOOL, EtatTitanPayload::marche,
			ByteBufCodecs.VAR_LONG, EtatTitanPayload::temps,
			ByteBufCodecs.DOUBLE, EtatTitanPayload::odometre,
			ByteBufCodecs.DOUBLE, EtatTitanPayload::x,
			ByteBufCodecs.DOUBLE, EtatTitanPayload::z,
			ByteBufCodecs.DOUBLE, EtatTitanPayload::cap,
			ByteBufCodecs.DOUBLE, EtatTitanPayload::capVise,
			ByteBufCodecs.DOUBLE, EtatTitanPayload::altitude,
			EtatTitanPayload::new);

	public static EtatTitanPayload de(EtatVoyage etat, boolean marche, long temps) {
		return new EtatTitanPayload(marche, temps, etat.odometre, etat.x, etat.z, etat.cap, etat.capVise, etat.altitude);
	}

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
