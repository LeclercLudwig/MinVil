package fr.letitan.silence;

import fr.letitan.monde.FormeTitan;
import fr.letitan.voyage.Repere;

/**
 * La géométrie de l'affrontement entre notre Titan et le Titan muet, indépendante de
 * Minecraft (testée seule).
 *
 * Pour attaquer, le Titan muet se place flanc contre flanc avec le nôtre, tourné dans le même
 * sens, sur un côté ({@code cote} : +1 à droite, -1 à gauche, dans le repère de notre dos).
 * Puis il charge régulièrement : il se jette contre nous et recule. Sur le flanc qu'il nous
 * présente luisent ses kystes, que les Gardiens visent à l'arc.
 */
public final class Affrontement {
	/** Distance entre les deux échines quand il est en position (les flancs sont à une vingtaine de blocs). */
	public static final double ECART = 205;
	/** Il charge toutes les ce nombre de ticks ; la charge dure tant, et il s'approche de tant de blocs. */
	public static final int PERIODE_CHARGE = 160, DUREE_CHARGE = 50;
	public static final double ELAN = 32;
	/** Les kystes : nombre, rayon de la zone qu'une flèche doit toucher, et flèches pour en crever un. */
	public static final int KYSTES = 5, SOLIDITE = 4;
	public static final double RAYON_KYSTE = 5;

	private Affrontement() {
	}

	/** Où il se tient (repère de notre dos) quand il ne charge pas. */
	public static double[] position(int cote) {
		return new double[] {cote * ECART, 0};
	}

	/**
	 * Avancée de la charge (0 au repos, 1 au moment du choc), selon le tick depuis le début du
	 * combat : il prend son élan, se jette, puis recule lentement.
	 */
	public static double charge(long depuis) {
		long t = Math.floorMod(depuis, PERIODE_CHARGE);
		if (t >= DUREE_CHARGE) {
			return 0;
		}
		double s = t / (double) DUREE_CHARGE;
		// Vite vers nous (les 30 premiers pour cent), puis retour en douceur.
		return s < 0.3 ? Math.sin(s / 0.3 * Math.PI / 2) : 0.5 + 0.5 * Math.cos((s - 0.3) / 0.7 * Math.PI);
	}

	/** Vrai au tick du choc. */
	public static boolean choc(long depuis) {
		return Math.floorMod(depuis, PERIODE_CHARGE) == (long) (0.3 * DUREE_CHARGE);
	}

	/**
	 * Position du kyste {@code i} dans le repère du Titan muet. Ils sont sur le flanc qu'il nous
	 * présente : le gauche s'il est à notre droite, et inversement.
	 */
	public static double[] kyste(int i, int cote) {
		int s = -cote;
		return switch (i) {
			case 0 -> new double[] {s * 20, FormeTitan.hautDuDos(20, -175) + 2, -175};
			case 1 -> new double[] {s * 35, FormeTitan.hautDuDos(35, -90) + 2, -90};
			case 2 -> new double[] {s * 80, 72, -30};
			case 3 -> new double[] {s * 40, FormeTitan.hautDuDos(40, 70) + 2, 70};
			default -> new double[] {s * 70, 66, 150};
		};
	}

	/**
	 * Quel kyste vivant ce point du monde d'en bas touche-t-il (-1 si aucun) ? Le Titan muet est
	 * décrit par son repère {@code muet}.
	 */
	public static int kysteTouche(Repere muet, int cote, int[] solidite, double x, double y, double z) {
		double xd = muet.versDosX(x, z), yd = muet.versDosY(y), zd = muet.versDosZ(x, z);
		for (int i = 0; i < KYSTES; i++) {
			if (solidite[i] <= 0) {
				continue;
			}
			double[] k = kyste(i, cote);
			double dx = xd - k[0], dy = yd - k[1], dz = zd - k[2];
			if (dx * dx + dy * dy + dz * dz < RAYON_KYSTE * RAYON_KYSTE) {
				return i;
			}
		}
		return -1;
	}
}
