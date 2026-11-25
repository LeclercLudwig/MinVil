package fr.letitan.voyage;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import fr.letitan.voyage.Navigation.Arret;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

import static fr.letitan.LeTitan.id;

/**
 * Où en est le voyage du Titan, sauvegardé avec le monde : position sur la carte du monde
 * d'en bas, cap, distance parcourue, arrêt en cours, et biome actuel de son dos.
 */
public final class EtatVoyage extends SavedData {
	public static final Codec<EtatVoyage> CODEC = RecordCodecBuilder.create(i -> i.group(
			Codec.DOUBLE.fieldOf("x").forGetter(e -> e.x),
			Codec.DOUBLE.fieldOf("z").forGetter(e -> e.z),
			Codec.DOUBLE.fieldOf("cap").forGetter(e -> e.cap),
			Codec.DOUBLE.fieldOf("cap_vise").forGetter(e -> e.capVise),
			Codec.DOUBLE.fieldOf("odometre").forGetter(e -> e.odometre),
			Codec.STRING.fieldOf("arret").forGetter(e -> e.arret.name()),
			Codec.INT.fieldOf("arret_restant").forGetter(e -> e.arretRestant),
			Codec.LONG.fieldOf("dernier_boire").forGetter(e -> e.dernierBoire),
			Codec.LONG.fieldOf("dernier_brouter").forGetter(e -> e.dernierBrouter),
			Codec.STRING.fieldOf("biome_dos").forGetter(e -> e.biomeDos),
			Codec.BOOL.fieldOf("demarre").forGetter(e -> e.demarre),
			Codec.DOUBLE.optionalFieldOf("altitude", 64.0).forGetter(e -> e.altitude)
	).apply(i, EtatVoyage::new));

	// Les données de jeu (données de commande) n'ont pas besoin de conversion : on réutilise ce type.
	public static final SavedDataType<EtatVoyage> TYPE =
			new SavedDataType<>(id("voyage"), EtatVoyage::new, CODEC, DataFixTypes.SAVED_DATA_COMMAND_STORAGE);

	public double x, z, cap, capVise, odometre;
	public Arret arret = Arret.AUCUN;
	public int arretRestant;
	/** Dernier arrêt de chaque genre, en temps de jeu ; très négatif au départ : pas d'attente au premier. */
	public long dernierBoire = -1_000_000, dernierBrouter = -1_000_000;
	/** Biome du dos du Titan (identifiant), copié du terrain qu'il traverse. */
	public String biomeDos = "minecraft:forest";
	/** Faux tant que la position de départ n'a pas été choisie (au point d'apparition du monde). */
	public boolean demarre;
	/** Hauteur du sol sous les pieds du Titan, dans le monde d'en bas (lissée). */
	public double altitude = 64;

	public EtatVoyage() {
	}

	private EtatVoyage(double x, double z, double cap, double capVise, double odometre, String arret,
			int arretRestant, long dernierBoire, long dernierBrouter, String biomeDos, boolean demarre, double altitude) {
		this.x = x;
		this.z = z;
		this.cap = cap;
		this.capVise = capVise;
		this.odometre = odometre;
		this.arret = Arret.valueOf(arret);
		this.arretRestant = arretRestant;
		this.dernierBoire = dernierBoire;
		this.dernierBrouter = dernierBrouter;
		this.biomeDos = biomeDos;
		this.demarre = demarre;
		this.altitude = altitude;
	}

	/** Le lien entre le dos et le monde d'en bas, en ce moment. */
	public Repere repere() {
		return new Repere(x, z, cap, altitude);
	}

	public boolean enMarche() {
		return arret == Arret.AUCUN;
	}
}
