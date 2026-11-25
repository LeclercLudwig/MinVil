package fr.letitan.monde;

import java.util.ArrayList;
import java.util.List;

/**
 * Forme du Titan : pour chaque bloc (x, y, z), de quoi il est fait.
 *
 * Indépendante de Minecraft, pour pouvoir être testée seule.
 * Le Titan marche vers le nord (z négatif) : la tête est au nord, la queue au sud.
 *
 * Le corps est une ellipse en coupe, dont la largeur varie le long de l'axe nord-sud.
 * Il est fait d'une peau épaisse (PEAU) qui enveloppe la chair (CHAIR). Le dos porte
 * de la terre et de l'herbe, et une crête de plaques d'os court le long de l'échine.
 * La tête et les pattes ne sont pas du terrain : elles sont animées (voir le paquet vie).
 */
public final class FormeTitan {

	/** Ce dont un bloc est fait. Le générateur traduit chaque matière en bloc Minecraft. */
	public enum Matiere { VIDE, HERBE, TERRE, PEAU, CHAIR, OS }

	/** Hauteur de l'axe du corps. */
	public static final int Y_CENTRE = 70;
	/** Début du corps (au cou) et fin de la queue, sur l'axe nord-sud. */
	public static final int AVANT = -220;
	public static final int ARRIERE = 260;
	/** Bas du monde : les pattes descendent jusque-là. */
	public static final int Y_MIN = -64;

	private static final double LARGEUR_MAX = 90;
	private static final int EPAISSEUR_PEAU = 5;
	private static final int EPAISSEUR_TERRE = 3;
	/** Au-delà de cette pente (en proportion de la demi-largeur), le dos est trop raide pour la terre. */
	private static final double BORD_TERRE = 0.82;


	private FormeTitan() {
	}

	// --- Corps ---------------------------------------------------------------

	/** Position le long du corps : 0 au cou, 1 au bout de la queue. */
	private static double t(double z) {
		return (z - AVANT) / (ARRIERE - AVANT);
	}

	/** Demi-largeur du corps à cette position nord-sud (0 hors du corps). */
	public static double demiLargeur(double z) {
		if (z < AVANT || z > ARRIERE) {
			return 0;
		}
		double largeur = LARGEUR_MAX * Math.pow(Math.sin(Math.PI * (t(z) * 0.9 + 0.08)), 0.7);
		// Le cou s'affine jusqu'à zéro, caché dans la tête : pas de tranche plate à l'avant.
		double cou = Math.min(1, t(z) / 0.1);
		return largeur * Math.sqrt(cou * (2 - cou));
	}

	/** Hauteur du dos au-dessus de l'axe, sur la ligne de l'échine. */
	private static double hauteurDos(double z) {
		return 0.5 * demiLargeur(z);
	}

	/** Haut du dos dans cette colonne, ou Integer.MIN_VALUE hors du corps. */
	public static int hautDuDos(int x, int z) {
		double w = demiLargeur(z);
		if (Math.abs(x) >= w) {
			return Integer.MIN_VALUE;
		}
		double e = ellipse(x, w);
		return (int) Math.floor(Y_CENTRE + hauteurDos(z) * e + bosses(x, z) * e);
	}

	/** Bas du ventre dans cette colonne (à n'appeler que dans le corps). */
	public static int basDuVentre(int x, int z) {
		double w = demiLargeur(z);
		double e = ellipse(x, w);
		return (int) Math.ceil(Y_CENTRE - 0.7 * hauteurDos(z) * e);
	}

	/** Profil d'une ellipse : 1 au centre, 0 au bord. */
	private static double ellipse(double x, double w) {
		double u = x / w;
		return u >= 1 ? 0 : Math.sqrt(1 - u * u);
	}

	/** Le corps, sans la crête, la tête ni les pattes. */
	private static Matiere corps(int x, int y, int z) {
		double w = demiLargeur(z);
		if (Math.abs(x) >= w) {
			return Matiere.VIDE;
		}
		int haut = hautDuDos(x, z);
		int bas = basDuVentre(x, z);
		if (y > haut || y < bas) {
			return Matiere.VIDE;
		}
		// La chair est dans une ellipse plus petite : la peau garde la même épaisseur partout.
		double wInterieur = w - EPAISSEUR_PEAU;
		if (Math.abs(x) < wInterieur) {
			double e = ellipse(x, wInterieur);
			double hInterieur = Math.max(0, hauteurDos(z) - EPAISSEUR_PEAU);
			double hautChair = Y_CENTRE + hInterieur * e + bosses(x, z) * e - EPAISSEUR_TERRE;
			double basChair = Y_CENTRE - 0.7 * hInterieur * e;
			if (y <= hautChair && y >= basChair) {
				return Matiere.CHAIR;
			}
		}
		boolean dosPlat = Math.abs(x) < w * BORD_TERRE;
		int profondeur = haut - y;
		if (dosPlat && profondeur == 0) {
			return Matiere.HERBE;
		}
		if (dosPlat && profondeur <= EPAISSEUR_TERRE) {
			return Matiere.TERRE;
		}
		return Matiere.PEAU;
	}

	/** Petites collines sur le dos, quelques blocs de haut. */
	private static double bosses(int x, int z) {
		return 3.5 * Bruit.lisse(x / 22.0, z / 22.0, 1) + 1.5 * Bruit.lisse(x / 9.0, z / 9.0, 2);
	}

	// --- Crête d'os ----------------------------------------------------------

	/** Plaques d'os triangulaires le long de l'échine. */
	private static Matiere crete(int x, int y, int z) {
		if (Math.abs(x) > 1 || z < AVANT + 10 || z > ARRIERE - 30) {
			return Matiere.VIDE;
		}
		int dansPlaque = Math.floorMod(z, 14);
		if (dansPlaque > 6) {
			return Matiere.VIDE;
		}
		double forme = 1 - Math.abs(dansPlaque - 3) / 3.5;
		double hauteur = 16 * hauteurDos(z) / (0.5 * LARGEUR_MAX) * forme;
		if (Math.abs(x) == 1) {
			hauteur *= 0.6;
		}
		int base = hautDuDos(0, z);
		return y > base && y <= base + hauteur ? Matiere.OS : Matiere.VIDE;
	}

	// --- Lianes ---------------------------------------------------------------

	/** Les lianes pendent du flanc au-dessus de chaque patte (positions nord-sud des hanches). */
	private static final int[] Z_LIANES = {-130, 150};
	private static final int DEMI_BANDE = 5;
	/** Elles descendent jusqu'à mi-patte, à portée de ceux qui y grimpent. */
	public static final int Y_LIANES_BAS = 34;

	/**
	 * Y a-t-il une liane en (x, y, z) ? Renvoie +1 si elle s'accroche au bloc à l'est (x + 1),
	 * -1 au bloc à l'ouest (x - 1), 0 s'il n'y en a pas.
	 *
	 * Sur le flanc, elles tapissent la peau jusqu'au dos ; sous le point le plus large du corps,
	 * le ventre se dérobe et elles pendent dans le vide, en rideau, jusqu'à mi-patte.
	 */
	public static int liane(int x, int y, int z) {
		if (x == 0 || y < Y_LIANES_BAS || !dansUneBande(z) || matiere(x, y, z) != Matiere.VIDE) {
			return 0;
		}
		int cote = x > 0 ? 1 : -1;
		if (y >= Y_CENTRE) {
			// Contre la peau : le bloc vers le corps en est.
			return matiere(x - cote, y, z) == Matiere.PEAU ? -cote : 0;
		}
		// Dans le vide, sous le bord le plus large du corps.
		int bord = (int) Math.ceil(demiLargeur(z)) - 1;
		return Math.abs(x) == bord + 1 ? -cote : 0;
	}

	private static boolean dansUneBande(int z) {
		for (int zl : Z_LIANES) {
			if (Math.abs(z - zl) <= DEMI_BANDE) {
				return true;
			}
		}
		return false;
	}

	/** Les colonnes (x, z) où pousse une liane, de chaque côté, pour les poser. */
	public static List<int[]> colonnesDeLianes() {
		List<int[]> colonnes = new ArrayList<>();
		for (int zl : Z_LIANES) {
			for (int z = zl - DEMI_BANDE; z <= zl + DEMI_BANDE; z++) {
				int bord = (int) Math.ceil(demiLargeur(z)) - 1;
				for (int x = 0; x <= bord + 1; x++) {
					colonnes.add(new int[] {x + 1, z});
					colonnes.add(new int[] {-x - 1, z});
				}
			}
		}
		return colonnes;
	}

	// --- Tout ----------------------------------------------------------------

	/** Ce dont est fait le bloc (x, y, z). */
	public static Matiere matiere(int x, int y, int z) {
		Matiere m = corps(x, y, z);
		if (m != Matiere.VIDE) {
			return m;
		}
		return crete(x, y, z);
	}

	/** Plus haut bloc plein de la colonne, ou Y_MIN - 1 si la colonne est vide. */
	public static int plusHautBloc(int x, int z, int yMax) {
		for (int y = yMax; y >= Y_MIN; y--) {
			if (matiere(x, y, z) != Matiere.VIDE) {
				return y;
			}
		}
		return Y_MIN - 1;
	}
}
