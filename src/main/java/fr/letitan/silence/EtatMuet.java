package fr.letitan.silence;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import fr.letitan.voyage.Repere;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

import java.util.ArrayList;
import java.util.List;

import static fr.letitan.LeTitan.id;

/** Le Titan muet, sauvegardé avec le monde : où il est, ce qu'il fait, et ce qu'il reste de ses kystes. */
public final class EtatMuet extends SavedData {

	/** Ce qu'il fait. */
	public enum Phase { ABSENT, APPROCHE, COMBAT, FUITE, VAINCU }

	public static final Codec<EtatMuet> CODEC = RecordCodecBuilder.create(i -> i.group(
			Codec.STRING.optionalFieldOf("phase", "ABSENT").forGetter(e -> e.phase.name()),
			Codec.DOUBLE.optionalFieldOf("x", 0.0).forGetter(e -> e.x),
			Codec.DOUBLE.optionalFieldOf("z", 0.0).forGetter(e -> e.z),
			Codec.DOUBLE.optionalFieldOf("cap", 0.0).forGetter(e -> e.cap),
			Codec.DOUBLE.optionalFieldOf("altitude", 64.0).forGetter(e -> e.altitude),
			Codec.INT.optionalFieldOf("cote", 1).forGetter(e -> e.cote),
			Codec.INT.listOf().optionalFieldOf("solidite", List.of()).forGetter(e -> e.solidite),
			Codec.LONG.optionalFieldOf("debut", 0L).forGetter(e -> e.debut),
			Codec.LONG.optionalFieldOf("prochain", 0L).forGetter(e -> e.prochain),
			Codec.INT.optionalFieldOf("annonces", 0).forGetter(e -> e.annonces)
	).apply(i, EtatMuet::new));

	public static final SavedDataType<EtatMuet> TYPE =
			new SavedDataType<>(id("titan_muet"), EtatMuet::new, CODEC, DataFixTypes.SAVED_DATA_COMMAND_STORAGE);

	public Phase phase = Phase.ABSENT;
	public double x, z, cap, altitude = 64;
	/** De quel côté de notre Titan il attaque (+1 : à droite). */
	public int cote = 1;
	/** Flèches qu'il faut encore pour crever chaque kyste. */
	public final List<Integer> solidite = new ArrayList<>();
	/** Tick du début de la phase en cours ; tick à partir duquel il peut (re)venir. */
	public long debut, prochain;
	/** Ce qu'on a déjà annoncé pendant cette approche (bits). */
	public int annonces;

	public EtatMuet() {
	}

	private EtatMuet(String phase, double x, double z, double cap, double altitude, int cote, List<Integer> solidite,
			long debut, long prochain, int annonces) {
		this.phase = Phase.valueOf(phase);
		this.x = x;
		this.z = z;
		this.cap = cap;
		this.altitude = altitude;
		this.cote = cote;
		this.solidite.addAll(solidite);
		this.debut = debut;
		this.prochain = prochain;
		this.annonces = annonces;
	}

	public Repere repere() {
		return new Repere(x, z, cap, altitude);
	}

	public int[] solidites() {
		int[] s = new int[Affrontement.KYSTES];
		for (int i = 0; i < s.length; i++) {
			s[i] = i < solidite.size() ? solidite.get(i) : 0;
		}
		return s;
	}

	/** Les kystes encore vivants, en bits. */
	public int kystesVivants() {
		int masque = 0;
		int[] s = solidites();
		for (int i = 0; i < s.length; i++) {
			if (s[i] > 0) {
				masque |= 1 << i;
			}
		}
		return masque;
	}
}
