package fr.letitan.vie;

import fr.letitan.Rythme;

/**
 * Les mouvements du Titan à un instant donné : angles de la tête, de la mâchoire et des pattes.
 *
 * Tout dépend du temps de jeu, comme {@link Rythme} : une patte touche le sol exactement
 * quand on entend le pas. {@code veille} va de 0 (endormi) à 1 (éveillé) et passe
 * doucement de l'un à l'autre à l'aube et au crépuscule. {@code marche} va de 0 (arrêté,
 * debout, la tête baissée pour boire ou brouter) à 1 (en marche).
 *
 * Les angles sont en radians, autour de l'axe est-ouest (tangage) ou vertical (lacet).
 * Un angle positif porte vers l'avant le bout d'une pièce qui pend (patte) ou relève
 * le bout d'une pièce tendue vers l'avant (tête, mâchoire).
 */
public final class AnimationTitan {

	/** Les quatre pattes, dans l'ordre où elles touchent le sol. */
	public enum Patte {
		AVANT_GAUCHE(-1, true, 0),
		ARRIERE_DROITE(1, false, 1),
		AVANT_DROITE(1, true, 2),
		ARRIERE_GAUCHE(-1, false, 3);

		/** -1 à l'ouest (gauche du Titan, qui regarde le nord), 1 à l'est. */
		public final int cote;
		public final boolean avant;
		/** Rang dans la marche : la patte se pose au pas numéro {@code ordre} du cycle. */
		public final int ordre;

		Patte(int cote, boolean avant, int ordre) {
			this.cote = cote;
			this.avant = avant;
			this.ordre = ordre;
		}
	}

	/** Un cycle complet de marche : chaque patte s'est posée une fois. */
	public static final int CYCLE = 4 * Rythme.PERIODE;
	/** Part du cycle où la patte est au sol (le reste, elle est levée et revient vers l'avant). */
	static final double APPUI = 0.75;
	/** Amplitude du balancement d'une cuisse. */
	private static final double AMPLITUDE = 0.32;
	/** Un rugissement toutes les deux minutes environ, pendant 4 secondes. */
	static final int PERIODE_RUGISSEMENT = 2400, DUREE_RUGISSEMENT = 80;
	/** Un clignement toutes les 5 secondes environ. */
	private static final int PERIODE_CLIGNEMENT = 97, DUREE_CLIGNEMENT = 4;

	private AnimationTitan() {
	}

	/** Angles d'une patte : cuisse (à la hanche), jambe (au genou), pied (à la cheville). */
	public record AnglesPatte(double cuisse, double genou, double pied) {
	}

	// --- Pattes --------------------------------------------------------------

	/** Où en est cette patte dans son cycle : 0 au moment où elle se pose, puis jusqu'à 1. */
	public static double phase(Patte patte, double temps) {
		double decale = temps - patte.ordre * Rythme.PERIODE;
		return ((decale % CYCLE) + CYCLE) % CYCLE / CYCLE;
	}

	public static AnglesPatte patte(Patte patte, double temps, double veille, double marche) {
		double phase = phase(patte, temps);
		double cuisse, genou, pied;
		if (phase < APPUI) {
			// Au sol : la patte glisse de l'avant vers l'arrière, et le corps avance.
			double s = phase / APPUI;
			cuisse = AMPLITUDE * (1 - 2 * s);
			genou = -0.12;
			pied = -(cuisse + genou);
		} else {
			// Levée : la patte revient vers l'avant, genou plié pour décoller le pied.
			double s = (phase - APPUI) / (1 - APPUI);
			double leve = Math.sin(Math.PI * s);
			cuisse = -AMPLITUDE + 2 * AMPLITUDE * lisse(s);
			genou = -0.12 - 1.0 * leve;
			pied = -(cuisse + genou) + 0.5 * leve;
		}
		// À l'arrêt, il se tient droit ; endormi, ses pattes se figent, légèrement pliées.
		AnglesPatte debout = new AnglesPatte(0, -0.06, 0.06);
		AnglesPatte endormi = new AnglesPatte(0.05, -0.25, 0.2);
		double mouvement = veille * marche;
		return new AnglesPatte(
				melange(melange(endormi.cuisse, debout.cuisse, veille), cuisse, mouvement),
				melange(melange(endormi.genou, debout.genou, veille), genou, mouvement),
				melange(melange(endormi.pied, debout.pied, veille), pied, mouvement));
	}

	// --- Tête ----------------------------------------------------------------

	/** Rotation de la tête autour du cou, vers la gauche ou la droite. */
	public static double lacetTete(double temps, double veille, double marche) {
		double enMarche = 0.22 * Math.sin(temps / 317) + 0.1 * Math.sin(temps / 131 + 1.3)
				+ 0.05 * Rythme.balancement(temps);
		double arrete = 0.08 * Math.sin(temps / 190);
		double eveille = melange(arrete, enMarche, marche);
		double endormi = 0.05 * Math.sin(temps / 900);
		return melange(endormi, eveille, veille);
	}

	/**
	 * Inclinaison de la tête : basse quand il dort, qui plonge à chaque pas, relevée quand il rugit,
	 * et tout en bas quand il s'arrête pour boire ou brouter.
	 */
	public static double tangageTete(double temps, double veille, double marche) {
		double enMarche = 0.06 * Math.sin(temps / 211) - 0.07 * plongee(temps) + 0.3 * rugissement(temps);
		double arrete = -0.6 + 0.05 * Math.sin(temps / 23);
		double eveille = melange(arrete, enMarche, marche);
		double endormi = -0.32 + 0.03 * Rythme.respiration(temps);
		return melange(endormi, eveille, veille);
	}

	/** La tête accompagne chaque pas : elle plonge en douceur puis remonte (plus fort pour les pattes avant). */
	private static double plongee(double temps) {
		double depuis = ((temps % Rythme.PERIODE) + Rythme.PERIODE) % Rythme.PERIODE;
		if (depuis >= 24) {
			return 0;
		}
		double bosse = Math.sin(Math.PI * depuis / 24);
		return bosse * bosse * Rythme.force((long) (temps - depuis));
	}

	/**
	 * Ouverture de la mâchoire (0 fermée) : il respire la gueule entrouverte, l'ouvre grand pour rugir,
	 * et la remue quand il boit ou broute.
	 */
	public static double machoire(double temps, double veille, double marche) {
		double enMarche = 0.04 + 0.03 * Math.sin(temps / 37) + 0.55 * rugissement(temps);
		double arrete = 0.1 + 0.08 * Math.max(0, Math.sin(temps / 23));
		return veille * melange(arrete, enMarche, marche);
	}

	/** Les yeux sont fermés pendant le sommeil et le temps d'un clignement. */
	public static boolean yeuxOuverts(double temps, double veille) {
		return veille > 0.5 && (long) temps % PERIODE_CLIGNEMENT >= DUREE_CLIGNEMENT;
	}

	/** Force du rugissement en cours, de 0 à 1 (les veilles comptent : on ne rugit pas en dormant). */
	public static double rugissement(double temps) {
		double r = ((temps % PERIODE_RUGISSEMENT) + PERIODE_RUGISSEMENT) % PERIODE_RUGISSEMENT;
		if (r >= DUREE_RUGISSEMENT) {
			return 0;
		}
		// Ouverture en 1 seconde, cri pendant 2 secondes, fermeture en 1 seconde.
		if (r < 20) {
			return lisse(r / 20);
		}
		if (r < 60) {
			return 1;
		}
		return lisse((DUREE_RUGISSEMENT - r) / 20);
	}

	/** Vrai au tick exact où commence un rugissement. */
	public static boolean debutRugissement(long temps) {
		return temps % PERIODE_RUGISSEMENT == 0;
	}

	// --- Outils --------------------------------------------------------------

	private static double lisse(double s) {
		s = Math.max(0, Math.min(1, s));
		return s * s * (3 - 2 * s);
	}

	private static double melange(double a, double b, double t) {
		return a + (b - a) * t;
	}
}
