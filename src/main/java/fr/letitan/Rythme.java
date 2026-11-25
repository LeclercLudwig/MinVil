package fr.letitan;

import net.minecraft.world.level.Level;

/**
 * Le rythme du Titan, calculé de la même façon par le serveur (sons, poussière)
 * et par le client (secousse de la caméra), à partir de l'heure du jeu :
 * pas besoin d'envoyer de messages réseau pour rester synchronisés.
 *
 * Le jour, le Titan marche : un pas toutes les {@link #PERIODE} ticks, les pattes
 * avant frappant plus fort que les pattes arrière. La nuit, il dort et respire.
 */
public final class Rythme {
	/** Ticks entre deux pas (20 ticks = 1 seconde). */
	public static final int PERIODE = 50;
	/** Durée d'une respiration pendant le sommeil. */
	private static final double RESPIRATION = 160;

	private Rythme() {
	}

	/** Le Titan marche le jour et dort la nuit. */
	public static boolean eveille(Level niveau) {
		return niveau.isBrightOutside();
	}

	/** Vrai au tick exact où une patte touche le sol. */
	public static boolean pas(long temps) {
		return temps % PERIODE == 0;
	}

	/** Les pattes avant (un pas sur deux) frappent plus fort. */
	public static float force(long temps) {
		return (temps / PERIODE) % 2 == 0 ? 1.0f : 0.6f;
	}

	/** Secousse après un pas, de 1 (impact) à 0, pour un temps avec fraction de tick. */
	public static double secousse(double temps) {
		double depuis = temps % PERIODE;
		if (depuis > 25) {
			return 0;
		}
		return Math.exp(-depuis / 5.0) * force((long) (temps - depuis));
	}

	/** Balancement lent du corps qui marche, entre -1 et 1 : un cycle toutes les quatre pattes. */
	public static double balancement(double temps) {
		return Math.sin(2 * Math.PI * temps / (4.0 * PERIODE));
	}

	/** Ticks entre deux battements du cœur : lent quand il dort. */
	public static final int BATTEMENT_EVEILLE = 30, BATTEMENT_ENDORMI = 50;

	/**
	 * Contraction du cœur, de 0 (repos) à 1 : un double coup (« poum-poum ») à chaque battement.
	 * {@code temps} avec fraction de tick.
	 */
	public static double battement(double temps, boolean eveille) {
		int periode = eveille ? BATTEMENT_EVEILLE : BATTEMENT_ENDORMI;
		double depuis = ((temps % periode) + periode) % periode;
		double premier = Math.exp(-depuis / 2.5);
		double second = depuis >= 6 ? 0.6 * Math.exp(-(depuis - 6) / 2.5) : 0;
		return Math.min(1, premier + second);
	}

	/** Vrai au tick où le cœur bat. */
	public static boolean bat(long temps, boolean eveille) {
		return temps % (eveille ? BATTEMENT_EVEILLE : BATTEMENT_ENDORMI) == 0;
	}

	/** Le Titan expire (souffle par l'évent) pendant la seconde moitié de chaque respiration. */
	public static boolean expire(double temps) {
		double phase = ((temps % RESPIRATION) + RESPIRATION) % RESPIRATION;
		return phase >= RESPIRATION / 2;
	}

	/** Vrai au tick où il commence à expirer. */
	public static boolean debutExpiration(long temps) {
		return temps % (long) RESPIRATION == (long) RESPIRATION / 2;
	}

	/** Souffle du Titan endormi, entre -1 et 1. */
	public static double respiration(double temps) {
		return Math.sin(2 * Math.PI * temps / RESPIRATION);
	}
}
