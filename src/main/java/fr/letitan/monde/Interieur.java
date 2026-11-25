package fr.letitan.monde;

import fr.letitan.monde.FormeTitan.Matiere;

import java.util.List;

/**
 * L'intérieur du Titan : des cavités creusées dans sa chair, reliées entre elles.
 *
 * On entre par l'évent, un trou dans le dos près du cou (le Titan y souffle). Il descend
 * dans le grand conduit qui court le long du corps, du fond de la gorge jusqu'à l'estomac.
 * Du conduit partent les bronches vers les deux poumons et un passage vers le cœur. Du cœur
 * part la grande veine, une rivière de sang qui coule jusqu'à la queue, où un puits ressort
 * sur le dos.
 *
 * Indépendant de Minecraft, comme {@link FormeTitan} : testé seul.
 */
public final class Interieur {

	/** Les parties de l'intérieur. */
	public enum Organe { EVENT, SORTIE, COEUR, POUMON, ESTOMAC, VEINE, CONDUIT }

	/** Une cavité : un ellipsoïde, ou un tube qui suit une ligne brisée. */
	private interface Cavite {
		Organe organe();

		/** Distance « normalisée » : moins de 1 à l'intérieur. */
		double distance(double x, double y, double z);

		/** Rayon typique, pour convertir une épaisseur de paroi en distance normalisée. */
		double rayon();

		double xMin();

		double xMax();

		double yMin();

		double yMax();

		double zMin();

		double zMax();
	}

	private record Boule(Organe organe, double cx, double cy, double cz, double rx, double ry, double rz) implements Cavite {
		@Override
		public double distance(double x, double y, double z) {
			double dx = (x - cx) / rx, dy = (y - cy) / ry, dz = (z - cz) / rz;
			return Math.sqrt(dx * dx + dy * dy + dz * dz);
		}

		@Override
		public double rayon() {
			return Math.min(rx, Math.min(ry, rz));
		}

		public double xMin() { return cx - rx; }
		public double xMax() { return cx + rx; }
		public double yMin() { return cy - ry; }
		public double yMax() { return cy + ry; }
		public double zMin() { return cz - rz; }
		public double zMax() { return cz + rz; }
	}

	private record Tube(Organe organe, double[][] points, double r) implements Cavite {
		@Override
		public double distance(double x, double y, double z) {
			return Math.sqrt(plusProche(x, y, z)[3]) / r;
		}

		/** Le point de la ligne le plus proche : {x, y, z, distance au carré, indice du segment}. */
		double[] plusProche(double x, double y, double z) {
			double[] meilleur = null;
			for (int i = 0; i + 1 < points.length; i++) {
				double[] a = points[i], b = points[i + 1];
				double abx = b[0] - a[0], aby = b[1] - a[1], abz = b[2] - a[2];
				double t = ((x - a[0]) * abx + (y - a[1]) * aby + (z - a[2]) * abz) / (abx * abx + aby * aby + abz * abz);
				t = Math.max(0, Math.min(1, t));
				double px = a[0] + t * abx, py = a[1] + t * aby, pz = a[2] + t * abz;
				double d = (x - px) * (x - px) + (y - py) * (y - py) + (z - pz) * (z - pz);
				if (meilleur == null || d < meilleur[3]) {
					meilleur = new double[] {px, py, pz, d, i};
				}
			}
			return meilleur;
		}

		@Override
		public double rayon() {
			return r;
		}

		private double borne(int axe, boolean max) {
			double v = max ? Double.NEGATIVE_INFINITY : Double.POSITIVE_INFINITY;
			for (double[] p : points) {
				v = max ? Math.max(v, p[axe] + r) : Math.min(v, p[axe] - r);
			}
			return v;
		}

		public double xMin() { return borne(0, false); }
		public double xMax() { return borne(0, true); }
		public double yMin() { return borne(1, false); }
		public double yMax() { return borne(1, true); }
		public double zMin() { return borne(2, false); }
		public double zMax() { return borne(2, true); }
	}

	// --- Les organes -----------------------------------------------------------

	/** Le haut de l'évent, sur le dos (on y entre), et le haut du puits de sortie, près de la queue. */
	public static final int[] EVENT = {12, -168}, SORTIE_HAUT = {-12, 213};
	public static final double[] COEUR = {-24, 68, -95};
	public static final double[] ESTOMAC = {0, 62, 55};
	public static final double[][] POUMONS = {{-28, 75, -140}, {28, 75, -140}};
	/** Hauteur de l'acide, au fond de l'estomac. */
	public static final int Y_ACIDE = 53;

	private static final Tube VEINE = new Tube(Organe.VEINE, new double[][] {
			{-24, 56, -95}, {-34, 57, -40}, {-34, 58, 60}, {-30, 60, 150}, {-12, 66, 212}}, 3);

	/** Par ordre de priorité : la première cavité qui contient un point lui donne son organe. */
	private static final List<Cavite> CAVITES = List.of(
			new Tube(Organe.EVENT, new double[][] {{EVENT[0], 104, EVENT[1]}, {2, 66, EVENT[1]}}, 2.5),
			new Tube(Organe.SORTIE, new double[][] {{-12, 69, 213}, {SORTIE_HAUT[0], 100, SORTIE_HAUT[1]}}, 2),
			new Boule(Organe.COEUR, COEUR[0], COEUR[1], COEUR[2], 11, 10, 11),
			new Boule(Organe.POUMON, POUMONS[0][0], POUMONS[0][1], POUMONS[0][2], 13, 10, 17),
			new Boule(Organe.POUMON, POUMONS[1][0], POUMONS[1][1], POUMONS[1][2], 13, 10, 17),
			new Boule(Organe.ESTOMAC, ESTOMAC[0], ESTOMAC[1], ESTOMAC[2], 28, 14, 36),
			VEINE,
			// Le grand conduit, puis les bronches et le passage vers le cœur.
			new Tube(Organe.CONDUIT, new double[][] {{0, 64, -198}, {0, 60, 25}}, 3.5),
			new Tube(Organe.CONDUIT, new double[][] {{0, 64, -140}, {-16, 72, -140}}, 2.5),
			new Tube(Organe.CONDUIT, new double[][] {{0, 64, -140}, {16, 72, -140}}, 2.5),
			new Tube(Organe.CONDUIT, new double[][] {{0, 63, -95}, {-14, 66, -95}}, 2.5));

	/** Épaisseur des parois propres à chaque organe (os de l'évent, rose des poumons...). */
	private static final double PAROI = 1.6;
	private static final double X_MIN, X_MAX, Y_MIN, Y_MAX, Z_MIN, Z_MAX;

	static {
		double x0 = 1e9, x1 = -1e9, y0 = 1e9, y1 = -1e9, z0 = 1e9, z1 = -1e9;
		for (Cavite c : CAVITES) {
			x0 = Math.min(x0, c.xMin());
			x1 = Math.max(x1, c.xMax());
			y0 = Math.min(y0, c.yMin());
			y1 = Math.max(y1, c.yMax());
			z0 = Math.min(z0, c.zMin());
			z1 = Math.max(z1, c.zMax());
		}
		X_MIN = x0 - PAROI - 1;
		X_MAX = x1 + PAROI + 1;
		Y_MIN = y0 - PAROI - 1;
		Y_MAX = y1 + PAROI + 1;
		Z_MIN = z0 - PAROI - 1;
		Z_MAX = z1 + PAROI + 1;
	}

	private Interieur() {
	}

	/** La boîte qui contient tout l'intérieur (et ses parois) : {xMin, yMin, zMin, xMax, yMax, zMax}. */
	public static int[] boite() {
		return new int[] {(int) Math.floor(X_MIN), (int) Math.floor(Y_MIN), (int) Math.floor(Z_MIN),
				(int) Math.ceil(X_MAX), (int) Math.ceil(Y_MAX), (int) Math.ceil(Z_MAX)};
	}

	private static boolean horsBoite(double x, double y, double z) {
		return x < X_MIN || x > X_MAX || y < Y_MIN || y > Y_MAX || z < Z_MIN || z > Z_MAX;
	}

	/** L'organe dont la cavité contient ce point (au centre du bloc), ou null. */
	public static Organe organe(double x, double y, double z) {
		if (horsBoite(x, y, z)) {
			return null;
		}
		for (Cavite c : CAVITES) {
			if (c.distance(x, y, z) < 1) {
				return c.organe();
			}
		}
		return null;
	}

	/** L'organe dont ce point est dans la paroi (à moins de {@link #PAROI} blocs de la cavité), ou null. */
	private static Organe paroi(double x, double y, double z) {
		Organe meilleur = null;
		double plusPres = Double.POSITIVE_INFINITY;
		for (Cavite c : CAVITES) {
			double d = (c.distance(x, y, z) - 1) * c.rayon();
			if (d < PAROI && d < plusPres) {
				plusPres = d;
				meilleur = c.organe();
			}
		}
		return meilleur;
	}

	/** L'ambiance d'un endroit : les entrailles (sang rouge, brume rouge) ou l'estomac (acide vert). */
	public enum Zone { ENTRAILLES, ESTOMAC }

	/** La zone d'ambiance en ce point (le biome qu'on y met), ou null au-dehors. */
	public static Zone zone(double x, double y, double z) {
		if (horsBoite(x, y, z)) {
			return null;
		}
		Organe ici = organe(x, y, z);
		if (ici != null) {
			return switch (ici) {
				case EVENT, SORTIE -> null;
				case ESTOMAC -> Zone.ESTOMAC;
				default -> Zone.ENTRAILLES;
			};
		}
		// Dans la chair, autour des cavités : la même ambiance que la cavité la plus proche.
		Zone zone = null;
		for (Cavite c : CAVITES) {
			if (c.organe() != Organe.EVENT && c.organe() != Organe.SORTIE && (c.distance(x, y, z) - 1) * c.rayon() < 4) {
				if (c.organe() == Organe.ESTOMAC) {
					return Zone.ESTOMAC;
				}
				zone = Zone.ENTRAILLES;
			}
		}
		return zone;
	}

	/** Le sang de la veine monte jusque-là, à cet endroit (un peu sous le milieu du tube). */
	private static double niveauDuSang(double x, double y, double z) {
		return VEINE.plusProche(x, y, z)[1] - 0.5;
	}

	/**
	 * Le courant de la veine en ce point : direction (x, z) normalisée, du cœur vers la queue.
	 */
	public static double[] courant(double x, double y, double z) {
		double[] p = VEINE.plusProche(x, y, z);
		double[] a = VEINE.points()[(int) p[4]], b = VEINE.points()[(int) p[4] + 1];
		double dx = b[0] - a[0], dz = b[2] - a[2], n = Math.hypot(dx, dz);
		return new double[] {dx / n, dz / n};
	}

	/**
	 * Ce qui remplace la matière du corps {@code base} en ce bloc à cause de l'intérieur
	 * (cavité, liquide, paroi), ou null si l'intérieur n'y change rien.
	 */
	static Matiere matiere(int x, int y, int z, Matiere base) {
		double cx = x + 0.5, cy = y + 0.5, cz = z + 0.5;
		if (horsBoite(cx, cy, cz)) {
			return null;
		}
		Organe o = organe(cx, cy, cz);
		if (o != null) {
			if (o == Organe.VEINE && cy < niveauDuSang(cx, cy, cz)) {
				return Matiere.SANG;
			}
			if (o == Organe.ESTOMAC && y <= Y_ACIDE) {
				return Matiere.ACIDE;
			}
			if ((o == Organe.CONDUIT || o == Organe.COEUR) && organe(cx, cy + 1, cz) == null && hasard(x, y, z) < 0.08) {
				return Matiere.VRILLE;
			}
			return Matiere.CAVITE;
		}
		Organe p = paroi(cx, cy, cz);
		if (p == null) {
			return null;
		}
		return switch (p) {
			case EVENT, SORTIE -> Matiere.OS;
			case POUMON -> {
				double h = hasard(x, y, z);
				yield h < 0.04 ? Matiere.LUMIERE_ROSE : h < 0.5 ? Matiere.POUMON : Matiere.POUMON_CLAIR;
			}
			case ESTOMAC -> {
				double h = hasard(x, y, z);
				yield h < 0.04 ? Matiere.LUMIERE_VERTE : h < 0.1 ? Matiere.GLU : Matiere.ESTOMAC;
			}
			default -> base == Matiere.CHAIR && hasard(x, y, z) < 0.05 ? Matiere.LUMIERE : null;
		};
	}

	/** Nombre pseudo-aléatoire entre 0 et 1, toujours le même pour un même bloc. */
	public static double hasard(int x, int y, int z) {
		long h = x * 73856093L ^ y * 19349663L ^ z * 83492791L;
		h ^= (h >>> 13);
		h *= 0x5bd1e995L;
		h ^= (h >>> 15);
		return (h & 0xFFFFFF) / (double) 0x1000000;
	}
}
