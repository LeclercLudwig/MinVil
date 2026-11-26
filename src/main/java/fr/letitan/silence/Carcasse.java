package fr.letitan.silence;

import fr.letitan.monde.Interieur;

import java.util.ArrayList;
import java.util.List;

/**
 * La carcasse d'un Titan mort, couchée dans le monde d'en bas : son squelette, tombé sur le
 * ventre. Une épine dorsale hérissée de vertèbres, portée par une cage de côtes assez haute
 * pour y marcher ; à l'avant, un crâne de dragon creux, sa mâchoire tombée devant ; les os
 * des pattes couchés à côté ; la queue qui s'amenuise. Au milieu de la cage, son cœur pétrifié.
 *
 * La forme est décrite dans le repère de la carcasse : {@code u} en travers, {@code v} le long
 * du corps (tête vers les v négatifs), {@code h} la hauteur au-dessus du sol. Indépendant de
 * Minecraft : testé seul.
 */
public record Carcasse(double cx, double cz, double angle, double echelle, int numero) {

	/** De quoi est fait un bloc de la carcasse. */
	public enum Os { OS, OS_USE, SCULK, COEUR, LARME }

	/** Les carcasses sont réparties sur une grille : au plus une par case de ce côté. */
	static final int CASE = 1600;
	/** Aucune carcasse près du point de départ. */
	static final double LOIN_DU_DEPART = 1000;
	/** Demi-longueur du squelette (à l'échelle 1), et rayon qui contient toute la carcasse. */
	static final double DEMI_LONGUEUR = 140, RAYON = 175;
	/** Les noms des Titans tombés, pour leurs carnets. */
	static final String[] NOMS = {"Vael", "Orm", "Sélène", "Brakka"};
	/** Numéro réservé à la carcasse du Titan muet vaincu. */
	public static final int MUET = 99;

	// --- Emplacements ----------------------------------------------------------

	/** Les carcasses dont la forme peut toucher ce point (à {@code marge} blocs près). */
	public static List<Carcasse> pres(double x, double z, double marge, long graine, double spawnX, double spawnZ) {
		List<Carcasse> trouvees = new ArrayList<>();
		int ci = (int) Math.floor(x / CASE), cj = (int) Math.floor(z / CASE);
		for (int i = ci - 1; i <= ci + 1; i++) {
			for (int j = cj - 1; j <= cj + 1; j++) {
				Carcasse c = dansLaCase(i, j, graine, spawnX, spawnZ);
				if (c != null && Math.hypot(c.cx - x, c.cz - z) <= RAYON * c.echelle + marge) {
					trouvees.add(c);
				}
			}
		}
		return trouvees;
	}

	/** La carcasse de cette case de la grille, s'il y en a une. */
	static Carcasse dansLaCase(int i, int j, long graine, double spawnX, double spawnZ) {
		int g = (int) (graine ^ (graine >>> 32));
		if (Interieur.hasard(i, g, j) > 0.55) {
			return null;
		}
		double x = (i + 0.2 + 0.6 * Interieur.hasard(i, g + 1, j)) * CASE;
		double z = (j + 0.2 + 0.6 * Interieur.hasard(i, g + 2, j)) * CASE;
		if (Math.hypot(x - spawnX, z - spawnZ) < LOIN_DU_DEPART) {
			return null;
		}
		double angle = Interieur.hasard(i, g + 3, j) * 2 * Math.PI;
		double echelle = 0.85 + 0.35 * Interieur.hasard(i, g + 4, j);
		int numero = (int) (Interieur.hasard(i, g + 5, j) * NOMS.length);
		return new Carcasse(x, z, angle, echelle, numero);
	}

	public String nom() {
		return numero == MUET ? "Titan muet" : NOMS[numero];
	}

	/** Identifiant stable de la carcasse (pour se souvenir de ce qui s'y est passé). */
	public long cle() {
		return ((long) Math.floor(cx) << 32) | ((long) Math.floor(cz) & 0xFFFFFFFFL);
	}

	// --- Repère ----------------------------------------------------------------

	/** Coordonnées dans le repère de la carcasse, ramenées à l'échelle 1 : {u, v}. */
	public double[] local(double x, double z) {
		double dx = x - cx, dz = z - cz, c = Math.cos(angle), s = Math.sin(angle);
		return new double[] {(dx * c + dz * s) / echelle, (-dx * s + dz * c) / echelle};
	}

	/** Un point du repère de la carcasse (u, v, à l'échelle 1), dans le monde : {x, z}. */
	public double[] monde(double u, double v) {
		double c = Math.cos(angle), s = Math.sin(angle);
		u *= echelle;
		v *= echelle;
		return new double[] {cx + u * c - v * s, cz + u * s + v * c};
	}

	// --- Forme -----------------------------------------------------------------

	/** Hauteur de l'épine dorsale : haute sur la cage, basse au cou et sur la queue. */
	static double epine(double v) {
		double t = (v + 30) / 70;
		return 2 + 22 * Math.exp(-t * t * 1.2);
	}

	/** Demi-largeur de la cage de côtes. */
	static double cage(double v) {
		return 0.95 * epine(v) + 4;
	}

	/** Le cœur pétrifié, au milieu de la cage, et le coffre des reliques devant lui. */
	public static final double[] COEUR = {0, 5, -35}, RELIQUES = {0, 0, -27}, CRANE = {0, 3, -122};

	/** Ce qu'il y a en ce point (repère de la carcasse, à l'échelle 1), ou null. */
	public static Os os(double u, double h, double v) {
		if (v < -170 || v > DEMI_LONGUEUR + 2 || Math.abs(u) > 45 || h < -3 || h > 45) {
			return null;
		}
		boolean dedans = coeur(u, h, v) || crane(u, h, v) || machoire(u, h, v) || colonne(u, h, v)
				|| cotes(u, h, v) || femurs(u, h, v);
		if (!dedans) {
			return null;
		}
		if (coeur(u, h, v)) {
			return Interieur.hasard((int) (u * 7), (int) (h * 7), (int) (v * 7)) < 0.12 ? Os.LARME : Os.COEUR;
		}
		double usure = Interieur.hasard((int) Math.floor(u), (int) Math.floor(h), (int) Math.floor(v));
		return usure < 0.08 ? Os.SCULK : usure < 0.3 ? Os.OS_USE : Os.OS;
	}

	/** L'épine dorsale : un cordon d'os, une vertèbre tous les cinq blocs, chacune hérissée d'une épine. */
	private static boolean colonne(double u, double h, double v) {
		if (v < -108 || v > DEMI_LONGUEUR) {
			return false;
		}
		double hs = epine(v);
		if (Math.abs(u) <= 0.6 && Math.abs(h - hs) <= 0.6) {
			return true;
		}
		double vk = Math.round(v / 5) * 5;
		if (Math.abs(v - vk) <= 1.5 && Math.abs(u) <= 1.5 && Math.abs(h - hs) <= 1.5) {
			return true;
		}
		double pointe = 3 + 0.25 * hs;
		return Math.abs(v - vk) <= 0.6 && Math.abs(u) <= 0.6 && h > hs && h <= hs + 1.5 + pointe;
	}

	/** Les côtes : des arcs de l'épine jusqu'au sol, tous les sept blocs, certaines brisées. */
	private static boolean cotes(double u, double h, double v) {
		if (v < -88 || v > 28 || h < -2) {
			return false;
		}
		double vr = -85 + Math.round((v + 85) / 7) * 7;
		if (Math.abs(v - vr) > 1.2 || vr > 25) {
			return false;
		}
		double hs = epine(vr), w = cage(vr);
		// Distance à l'arc (un demi-ellipse de largeur w et de hauteur hs).
		double hh = Math.max(h, 0);
		double rayon = Math.sqrt((u / w) * (u / w) + (hh / hs) * (hh / hs));
		double ecart = Math.abs(rayon - 1) * Math.min(w, hs);
		if (ecart > 1.2) {
			return false;
		}
		// Quelques côtes sont brisées : il leur manque un morceau.
		int cote = (int) Math.round((vr + 85) / 7) * (u < 0 ? -1 : 1);
		double casse = Interieur.hasard(cote, 3, 9);
		if (casse < 0.3) {
			double angle = Math.atan2(Math.abs(u) / w, hh / hs);
			double debut = 0.3 + casse * 2;
			return angle < debut || angle > debut + 0.35;
		}
		return true;
	}

	/** Le crâne : une coque d'os creuse, percée des orbites et des narines, ouverte en bas devant, avec ses cornes. */
	private static boolean crane(double u, double h, double v) {
		double du = u / 11, dh = (h - 9) / 9, dv = (v + 124) / 16;
		double d = Math.sqrt(du * du + dh * dh + dv * dv);
		boolean coque = d <= 1 && d >= 1 - 2.2 / 9;
		if (coque) {
			boolean orbite = Math.hypot(Math.abs(u) - 9, h - 12) < 3 && Math.abs(v + 128) < 4;
			boolean narine = Math.abs(Math.abs(u) - 3) < 1.5 && Math.abs(h - 9) < 1.5 && v < -136;
			boolean gueule = h < 5 && v < -128;
			if (!orbite && !narine && !gueule) {
				return true;
			}
		}
		// Les cornes, vers l'arrière et vers le haut.
		for (int cote : new int[] {-1, 1}) {
			if (segment(u, h, v, cote * 6, 16, -116, cote * 11, 30, -98) < 1.6) {
				return true;
			}
		}
		return false;
	}

	/** La mâchoire, tombée à plat devant le crâne, avec ses dents dressées. */
	private static boolean machoire(double u, double h, double v) {
		if (v < -162 || v > -130 || h > 3.5 || h < -1) {
			return false;
		}
		boolean branche = Math.abs(Math.abs(u) - 8 + (v + 130) * 0.12) < 1.3 && h <= 1;
		boolean menton = v < -159 && Math.abs(u) < 5 && h <= 1;
		boolean dent = Math.abs(Math.abs(u) - 8 + (v + 130) * 0.12) < 0.6 && Math.floorMod((int) Math.floor(v), 3) == 0 && h > 1;
		return branche || menton || dent;
	}

	/** Les os des pattes, couchés au sol de part et d'autre de la cage. */
	private static boolean femurs(double u, double h, double v) {
		for (double[] f : new double[][] {{-30, -70, -26, -40}, {31, -68, 36, -42}, {-24, 30, -32, 58}, {27, 28, 22, 56}}) {
			double d = segment(u, h, v, f[0], 1.5, f[1], f[2], 1.5, f[3]);
			double bout = Math.min(Math.hypot(u - f[0], v - f[1]), Math.hypot(u - f[2], v - f[3]));
			if (d < 2 || (bout < 3.2 && h < 4)) {
				return h > -1;
			}
		}
		return false;
	}

	private static boolean coeur(double u, double h, double v) {
		double du = (u - COEUR[0]) / 5, dh = (h - COEUR[1]) / 4, dv = (v - COEUR[2]) / 5;
		return du * du + dh * dh + dv * dv <= 1;
	}

	/** Distance d'un point au segment [a, b] (en u, h, v). */
	private static double segment(double u, double h, double v, double au, double ah, double av, double bu, double bh, double bv) {
		double eu = bu - au, eh = bh - ah, ev = bv - av;
		double t = ((u - au) * eu + (h - ah) * eh + (v - av) * ev) / (eu * eu + eh * eh + ev * ev);
		t = Math.max(0, Math.min(1, t));
		double pu = au + t * eu - u, ph = ah + t * eh - h, pv = av + t * ev - v;
		return Math.sqrt(pu * pu + ph * ph + pv * pv);
	}

	/** Sous la carcasse et autour, le sol est couvert de sculk : le Silence l'a prise. */
	public static boolean sousLaCarcasse(double u, double v) {
		return v > -168 && v < DEMI_LONGUEUR + 4 && Math.abs(u) < cage(Math.max(-88, Math.min(28, v))) + 8;
	}

	/** Vue de haut : y a-t-il des os dans cette colonne (pour la carte vue du dos du Titan) ? */
	public static boolean osVusDeHaut(double u, double v) {
		for (double h = -1; h <= 40; h += 1) {
			if (os(u, h, v) != null) {
				return true;
			}
		}
		return false;
	}
}
