package fr.letitan.monde;

/**
 * Bruit lisse déterministe (bruit de valeurs, interpolé), entre -1 et 1.
 * Le Titan est le même dans tous les mondes : ce bruit ne dépend pas de la graine du monde.
 */
public final class Bruit {
	private Bruit() {
	}

	public static double lisse(double x, double z, int canal) {
		int x0 = (int) Math.floor(x), z0 = (int) Math.floor(z);
		double fx = lissage(x - x0), fz = lissage(z - z0);
		double a = valeur(x0, z0, canal), b = valeur(x0 + 1, z0, canal);
		double c = valeur(x0, z0 + 1, canal), d = valeur(x0 + 1, z0 + 1, canal);
		double haut = a + (b - a) * fx, bas = c + (d - c) * fx;
		return haut + (bas - haut) * fz;
	}

	private static double lissage(double t) {
		return t * t * (3 - 2 * t);
	}

	/** Valeur pseudo-aléatoire entre -1 et 1 pour un point entier. */
	private static double valeur(int x, int z, int canal) {
		long h = x * 0x9E3779B97F4A7C15L + z * 0xC2B2AE3D27D4EB4FL + canal * 0x165667B19E3779F9L;
		h ^= h >>> 33;
		h *= 0xFF51AFD7ED558CCDL;
		h ^= h >>> 33;
		return (h >>> 11) / (double) (1L << 53) * 2 - 1;
	}
}
