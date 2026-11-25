package fr.letitan.voyage;

/**
 * Comment le Titan se déplace sur la carte du monde d'en bas : il avance quand il est éveillé,
 * évite les océans, flâne au hasard, et s'arrête pour boire aux rivières ou brouter en forêt.
 *
 * Indépendante de Minecraft : le terrain est vu à travers {@link Terrain}, ce qui permet de
 * tester la navigation sur des cartes inventées.
 *
 * Cap en radians : 0 vers le nord (z négatif), pi/2 vers l'est (x positif).
 */
public final class Navigation {

	/** Ce que le Titan voit du terrain sous lui. */
	public enum Genre { OCEAN, RIVIERE, PLAGE, FORET, AUTRE, MORT }

	/** Le terrain d'en bas, vu de haut. */
	@FunctionalInterface
	public interface Terrain {
		Genre genre(double x, double z);
	}

	/** Pourquoi le Titan s'est arrêté. */
	public enum Arret { AUCUN, BOIRE, BROUTER }

	/** Blocs parcourus par tick de marche : environ 6 blocs par seconde, le pas d'une créature de cette taille. */
	public static final double VITESSE = 0.3;
	/** Durée d'un arrêt : deux minutes. */
	public static final int DUREE_ARRET = 2400;
	/** Temps minimal entre deux arrêts du même genre (en ticks de jeu : 24 000 = un jour). */
	static final long ATTENTE_BOIRE = 24000, ATTENTE_BROUTER = 36000;
	/** Toutes les combien de ticks le Titan choisit sa direction, et à quelle distance il regarde. */
	static final int CHOIX_CAP = 100;
	static final double REGARD = 300;
	/** Virage maximal par tick (environ 11 degrés toutes les 5 secondes). */
	static final double VIRAGE = 0.002;

	private Navigation() {
	}

	/**
	 * Un tick de voyage. {@code eveille} : le Titan dort la nuit et ne bouge pas.
	 * Renvoie vrai si l'état « marche ou arrêt » a changé (pour prévenir les joueurs).
	 */
	public static boolean tick(EtatVoyage etat, Terrain terrain, long temps, boolean eveille) {
		if (!eveille || etat.allure <= 0) {
			return false;
		}
		if (etat.arretRestant > 0) {
			etat.arretRestant--;
			if (etat.arretRestant == 0) {
				etat.arret = Arret.AUCUN;
				return true;
			}
			return false;
		}
		if (temps % CHOIX_CAP == 0) {
			etat.capVise = choisirCap(etat, terrain);
		}
		etat.cap = tourner(etat.cap, etat.capVise);
		double pas = VITESSE * etat.allure;
		etat.x += Math.sin(etat.cap) * pas;
		etat.z -= Math.cos(etat.cap) * pas;
		etat.odometre += pas;
		if (temps % 20 == 0) {
			return verifierArret(etat, terrain.genre(etat.x, etat.z), temps);
		}
		return false;
	}

	/** Le cap après un tick : il tourne doucement vers le cap visé, par le plus court chemin. */
	public static double tourner(double cap, double capVise) {
		double ecart = Math.IEEEremainder(capVise - cap, 2 * Math.PI);
		return cap + Math.max(-VIRAGE, Math.min(VIRAGE, ecart));
	}

	/**
	 * Prolonge la marche de {@code ticks} ticks (avec fraction) à partir d'une position connue,
	 * comme le ferait {@link #tick} sans arrêt ni nouveau cap : le client s'en sert pour
	 * placer le Titan entre deux messages du serveur. Renvoie {x, z, cap}.
	 */
	public static double[] prolonger(double x, double z, double cap, double capVise, double allure, double ticks) {
		double pas = VITESSE * allure;
		int entiers = (int) Math.floor(ticks);
		for (int i = 0; i < entiers; i++) {
			cap = tourner(cap, capVise);
			x += Math.sin(cap) * pas;
			z -= Math.cos(cap) * pas;
		}
		double reste = ticks - entiers;
		if (reste > 0) {
			double prochain = tourner(cap, capVise);
			x += Math.sin(prochain) * pas * reste;
			z -= Math.cos(prochain) * pas * reste;
			cap += (prochain - cap) * reste;
		}
		return new double[] {x, z, cap};
	}

	/** Regarde devant lui dans plusieurs directions, et préfère la terre ferme sans trop tourner. */
	static double choisirCap(EtatVoyage etat, Terrain terrain) {
		// Une envie de tourner qui change lentement avec la distance parcourue : il flâne.
		double envie = 0.6 * Math.sin(etat.odometre / 1700.0) + 0.3 * Math.sin(etat.odometre / 530.0 + 2.0);
		double meilleurCap = etat.cap;
		double meilleurScore = Double.NEGATIVE_INFINITY;
		for (int k = -6; k <= 6; k++) {
			double cap = etat.cap + k * Math.toRadians(25);
			double score = -Math.abs(cap - (etat.cap + envie));
			Genre devant = terrain.genre(etat.x + Math.sin(cap) * REGARD, etat.z - Math.cos(cap) * REGARD);
			Genre proche = terrain.genre(etat.x + Math.sin(cap) * REGARD / 2, etat.z - Math.cos(cap) * REGARD / 2);
			if (devant == Genre.OCEAN) {
				score -= 10;
			}
			if (proche == Genre.OCEAN) {
				score -= 20;
			}
			// Les terres mortes : il les sent, et les contourne s'il le peut.
			if (devant == Genre.MORT) {
				score -= 4;
			}
			if (proche == Genre.MORT) {
				score -= 8;
			}
			if (score > meilleurScore) {
				meilleurScore = score;
				meilleurCap = cap;
			}
		}
		return meilleurCap;
	}

	private static boolean verifierArret(EtatVoyage etat, Genre sous, long temps) {
		if (sous == Genre.RIVIERE && temps - etat.dernierBoire >= ATTENTE_BOIRE) {
			arreter(etat, Arret.BOIRE);
			etat.dernierBoire = temps;
			return true;
		}
		if (sous == Genre.FORET && temps - etat.dernierBrouter >= ATTENTE_BROUTER) {
			arreter(etat, Arret.BROUTER);
			etat.dernierBrouter = temps;
			return true;
		}
		return false;
	}

	private static void arreter(EtatVoyage etat, Arret arret) {
		etat.arret = arret;
		etat.arretRestant = DUREE_ARRET;
	}

	/** Le cap en toutes lettres, sur la rose des vents. */
	public static String direction(double cap) {
		String[] noms = {"le nord", "le nord-est", "l'est", "le sud-est", "le sud", "le sud-ouest", "l'ouest", "le nord-ouest"};
		int i = (int) Math.round(cap / (Math.PI / 4));
		return noms[Math.floorMod(i, 8)];
	}

	/**
	 * Position au sol, dans le monde d'en bas, d'un point du dos du Titan.
	 * Sur le dos, le Titan regarde toujours vers le nord ; en bas, il regarde selon son cap.
	 */
	public static double[] auSol(EtatVoyage etat, double xDos, double zDos) {
		double avant = -zDos;
		double droite = xDos;
		double sin = Math.sin(etat.cap), cos = Math.cos(etat.cap);
		return new double[] {etat.x + droite * cos + avant * sin, etat.z + droite * sin - avant * cos};
	}
}
