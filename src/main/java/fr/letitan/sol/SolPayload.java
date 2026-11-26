package fr.letitan.sol;

import fr.letitan.LeTitan;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import java.util.ArrayList;
import java.util.List;

/**
 * Message du serveur au client : un lot de points de la carte du monde d'en bas, pour dessiner
 * le sol sous le Titan. Chaque point est une case de {@code pas} blocs de côté, avec sa
 * hauteur, s'il est sous l'eau, si le Silence l'a pris (terre morte), et son biome (indice
 * dans {@code biomes}).
 */
public record SolPayload(int pas, List<String> biomes, int[] cx, int[] cz, short[] hauteur, short[] biome, boolean[] eau,
		boolean[] mort, boolean[] os)
		implements CustomPacketPayload {
	public static final Type<SolPayload> TYPE = new Type<>(LeTitan.id("sol"));
	public static final StreamCodec<ByteBuf, SolPayload> CODEC = StreamCodec.of(SolPayload::ecrire, SolPayload::lire);

	public int taille() {
		return cx.length;
	}

	private static void ecrire(ByteBuf buf, SolPayload p) {
		ByteBufCodecs.VAR_INT.encode(buf, p.pas);
		ByteBufCodecs.VAR_INT.encode(buf, p.biomes.size());
		for (String biome : p.biomes) {
			ByteBufCodecs.STRING_UTF8.encode(buf, biome);
		}
		ByteBufCodecs.VAR_INT.encode(buf, p.taille());
		for (int i = 0; i < p.taille(); i++) {
			buf.writeInt(p.cx[i]);
			buf.writeInt(p.cz[i]);
			buf.writeShort(p.hauteur[i]);
			buf.writeShort(p.biome[i] | (p.eau[i] ? 0x4000 : 0) | (p.mort[i] ? 0x2000 : 0) | (p.os[i] ? 0x1000 : 0));
		}
	}

	private static SolPayload lire(ByteBuf buf) {
		int pas = ByteBufCodecs.VAR_INT.decode(buf);
		int nbBiomes = ByteBufCodecs.VAR_INT.decode(buf);
		List<String> biomes = new ArrayList<>(nbBiomes);
		for (int i = 0; i < nbBiomes; i++) {
			biomes.add(ByteBufCodecs.STRING_UTF8.decode(buf));
		}
		int n = ByteBufCodecs.VAR_INT.decode(buf);
		int[] cx = new int[n], cz = new int[n];
		short[] hauteur = new short[n], biome = new short[n];
		boolean[] eau = new boolean[n], mort = new boolean[n], os = new boolean[n];
		for (int i = 0; i < n; i++) {
			cx[i] = buf.readInt();
			cz[i] = buf.readInt();
			hauteur[i] = buf.readShort();
			short b = buf.readShort();
			biome[i] = (short) (b & 0x0FFF);
			eau[i] = (b & 0x4000) != 0;
			mort[i] = (b & 0x2000) != 0;
			os[i] = (b & 0x1000) != 0;
		}
		return new SolPayload(pas, biomes, cx, cz, hauteur, biome, eau, mort, os);
	}

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
