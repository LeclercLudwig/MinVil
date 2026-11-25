package fr.letitan.silence;

import fr.letitan.monde.Bruit;

/**
 * Les terres mortes : là où le Silence a déjà gagné le monde d'en bas. Leur forme est fixée
 * pour chaque monde (par sa graine) : de grandes taches aux bords déchiquetés, qui laissent
 * libre une région autour du point d'apparition, là où le voyage commence.
 *
 * Indépendant de Minecraft : testé seul.
 */
public final class ZonesMortes {
	/** Au-dessus de ce seuil, la terre est morte ; entre le bord et lui, le sculk ne fait que l'effleurer. */
	public static final double MORTE = 0.42, BORD = 0.34;
	/** Rien ne meurt à moins de cette distance du point d'apparition. */
	public static final double REFUGE = 350;

	private final double decalageX, decalageZ, spawnX, spawnZ;

	public ZonesMortes(long graine, double spawnX, double spawnZ) {
		this.decalageX = Math.floorMod(graine, 100_003L) * 0.731;
		this.decalageZ = Math.floorMod(graine / 7, 100_019L) * 0.613;
		this.spawnX = spawnX;
		this.spawnZ = spawnZ;
	}

	/** Force du Silence en ce point, d'environ -1 à 1. */
	public double intensite(double x, double z) {
		double grand = Bruit.lisse(x / 420.0 + decalageX, z / 420.0 + decalageZ, 21);
		double detail = Bruit.lisse(x / 110.0 + decalageX, z / 110.0 + decalageZ, 22);
		double valeur = 0.72 * grand + 0.28 * detail;
		// Le refuge autour du point d'apparition, qui se referme en douceur.
		double distance = Math.hypot(x - spawnX, z - spawnZ);
		if (distance < REFUGE * 1.5) {
			valeur -= Math.max(0, 1 - distance / (REFUGE * 1.5)) * 1.2;
		}
		return valeur;
	}

	public boolean morte(double x, double z) {
		return intensite(x, z) >= MORTE;
	}
}
