package fr.letitan.client;

import fr.letitan.silence.EtatMuet;
import fr.letitan.silence.MuetPayload;
import fr.letitan.voyage.Repere;

/**
 * Ce que le client sait du Titan muet : où il est (prolongé entre deux messages, d'après sa
 * vitesse), ce qu'il fait, ses kystes. Et les secousses violentes de ses charges.
 */
public final class EtatMuetClient {
	private static MuetPayload dernier, precedent;
	private static double secousse;

	private EtatMuetClient() {
	}

	static void recevoir(MuetPayload message) {
		if (dernier != null && message.temps() > dernier.temps()) {
			precedent = dernier;
		}
		dernier = message;
	}

	static void oublier() {
		dernier = null;
		precedent = null;
		secousse = 0;
	}

	private static boolean present() {
		return dernier != null && dernier.phase() != EtatMuet.Phase.ABSENT.ordinal();
	}

	/** Où il est au temps donné, ou null s'il n'y a pas de Titan muet. */
	static Repere repere(double temps) {
		if (!present()) {
			return null;
		}
		double x = dernier.x(), z = dernier.z();
		if (precedent != null && dernier.temps() > precedent.temps()) {
			double dt = dernier.temps() - precedent.temps();
			double avance = Math.min(30, Math.max(0, temps - dernier.temps()));
			x += (dernier.x() - precedent.x()) / dt * avance;
			z += (dernier.z() - precedent.z()) / dt * avance;
		}
		return new Repere(x, z, dernier.cap(), dernier.altitude());
	}

	static boolean marche() {
		return present() && (dernier.phase() == EtatMuet.Phase.APPROCHE.ordinal() || dernier.phase() == EtatMuet.Phase.FUITE.ordinal());
	}

	static int kystes() {
		return present() ? dernier.kystes() : 0;
	}

	static int cote() {
		return present() ? dernier.cote() : 1;
	}

	static float effondrement() {
		return present() ? dernier.effondrement() : 0;
	}

	// --- Secousses -----------------------------------------------------------

	static void secouer(float force) {
		secousse = Math.max(secousse, force);
	}

	/** Force de la secousse en cours ; elle s'amortit à chaque image. */
	public static double secousse() {
		secousse *= 0.93;
		return secousse < 0.01 ? 0 : secousse;
	}
}
