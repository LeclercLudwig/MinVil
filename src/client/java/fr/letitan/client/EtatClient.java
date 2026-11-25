package fr.letitan.client;

import fr.letitan.Rythme;
import fr.letitan.voyage.EtatTitanPayload;
import fr.letitan.voyage.Navigation;
import fr.letitan.voyage.Repere;
import net.minecraft.client.multiplayer.ClientLevel;

/**
 * Ce que le client sait du voyage du Titan : s'il marche, où il est dans le monde d'en bas,
 * et la distance parcourue. Entre deux messages du serveur, la marche est prolongée comme
 * le serveur la calcule, pour que le sol, les nuages et le Titan vu du sol bougent sans à-coups.
 */
public final class EtatClient {
	/** Au-delà, on ne prolonge plus : le serveur ne répond plus (jeu en pause...). */
	private static final double PROLONGATION_MAX = 200;
	/** Vitesse du passage veille/sommeil ou marche/arrêt, comme sur le serveur. */
	private static final double TRANSITION = 0.02;

	private static EtatTitanPayload etat;
	private static double altitudePrecedente;
	private static long tempsPrecedent;
	private static double veille = -1, marcheLissee = -1;

	private EtatClient() {
	}

	static void recevoir(EtatTitanPayload nouveau) {
		if (etat != null && nouveau.temps() > etat.temps()) {
			altitudePrecedente = etat.altitude();
			tempsPrecedent = etat.temps();
		} else {
			altitudePrecedente = nouveau.altitude();
			tempsPrecedent = nouveau.temps();
		}
		etat = nouveau;
	}

	/** Oublie tout (en quittant la partie). */
	static void oublier() {
		etat = null;
		veille = -1;
		marcheLissee = -1;
	}

	public static boolean connu() {
		return etat != null;
	}

	public static boolean marche() {
		return etat == null || etat.marche();
	}

	private static double ecoule(double tempsActuel) {
		return etat.marche() ? Math.min(PROLONGATION_MAX, Math.max(0, tempsActuel - etat.temps())) : 0;
	}

	/** Distance parcourue par le Titan au temps de jeu donné (avec fraction de tick). */
	public static double odometre(double tempsActuel) {
		return etat == null ? 0 : etat.odometre() + Navigation.VITESSE * etat.allure() * ecoule(tempsActuel);
	}

	/** Où est le Titan dans le monde d'en bas au temps de jeu donné, ou null si on ne le sait pas encore. */
	public static Repere repere(double tempsActuel) {
		if (etat == null) {
			return null;
		}
		double[] p = Navigation.prolonger(etat.x(), etat.z(), etat.cap(), etat.capVise(), etat.allure(), ecoule(tempsActuel));
		// L'altitude change lentement : on prolonge sa pente entre les deux derniers messages.
		double pente = etat.temps() > tempsPrecedent
				? (etat.altitude() - altitudePrecedente) / (etat.temps() - tempsPrecedent) : 0;
		double depuis = Math.min(40, Math.max(0, tempsActuel - etat.temps()));
		return new Repere(p[0], p[1], p[2], etat.altitude() + pente * depuis);
	}

	// --- Allure du Titan (pour le dessiner vu du sol) ----------------------------

	/** À chaque tick du client : veille et marche glissent doucement, comme sur le serveur. */
	static void tick(ClientLevel niveau) {
		if (niveau.getGameTime() % 4 != 0 && veille >= 0) {
			return;
		}
		double cibleVeille = Rythme.eveille(niveau) ? 1 : 0;
		double cibleMarche = marche() ? 1 : 0;
		if (veille < 0) {
			veille = cibleVeille;
			marcheLissee = cibleMarche;
		}
		veille += Math.max(-TRANSITION, Math.min(TRANSITION, cibleVeille - veille));
		marcheLissee += Math.max(-TRANSITION, Math.min(TRANSITION, cibleMarche - marcheLissee));
	}

	public static double veille() {
		return Math.max(0, veille);
	}

	public static double marcheLissee() {
		return Math.max(0, marcheLissee);
	}
}
