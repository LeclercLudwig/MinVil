package fr.letitan.vie;

import fr.letitan.LeTitan;
import fr.letitan.vie.AnimationTitan.Patte;
import fr.letitan.vie.ModeleTitan.Piece;
import fr.letitan.vie.Squelette.Pose;
import fr.letitan.voyage.Repere;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * Où un joueur s'agrippe au Titan : à ses pattes, sur son dos comme dans le monde d'en bas
 * où elles touchent le sol. Toucher une patte suffit pour qu'elle te hisse jusqu'à son dos ;
 * accroupi, on redescend. (Des lianes pendent aussi de son flanc, voir {@link Lianes}.)
 *
 * Le serveur et le client font le même calcul, chacun avec ce qu'il sait du Titan.
 */
public final class Accroche {

	/** Ce qu'on sait du Titan, côté serveur ou côté client. */
	public interface Source {
		/** Où il est dans le monde d'en bas, ou null si on ne le sait pas. */
		Repere repere(Level niveau, double temps);

		double veille(Level niveau);

		double marche(Level niveau);
	}

	/** La patte hisse à cette vitesse (blocs par tick : 7 par seconde), et l'on redescend accroupi à celle-ci. */
	public static final double VITESSE_MONTEE = 0.35, VITESSE_DESCENTE = 0.3;
	/** À quelle distance d'une patte on peut s'y agripper. */
	private static final float PRISE = 0.8f;
	/**
	 * Sous le pied, la prise continue vers le bas : le Titan est posé à la hauteur moyenne du
	 * relief, et dans un creux, son pied ne touche pas tout à fait le sol.
	 */
	private static final float SOUS_LE_PIED = 40;
	/** Une patte ne descend pas à plus de cette distance (à l'horizontale) de sa hanche. */
	private static final double PORTEE_PATTE = 70;

	public static Source serveur, client;

	private static final Piece[] PIECES = {Piece.CUISSE, Piece.JAMBE, Piece.PIED};
	/** Les boîtes de chaque segment de patte, calculées une fois. */
	private static final java.util.List<java.util.List<Boite>> BOITES =
			java.util.Arrays.stream(PIECES).map(ModeleTitan::boites).toList();

	private Accroche() {
	}

	/** Le joueur peut-il grimper (s'agripper au Titan) là où il est ? */
	public static boolean agrippe(LivingEntity entite) {
		if (!(entite instanceof Player) || entite.isSpectator()) {
			return false;
		}
		Level niveau = entite.level();
		if (niveau.dimension() != LeTitan.DOS && niveau.dimension() != Level.OVERWORLD) {
			return false;
		}
		return aUnePatte(entite) != null;
	}

	/** La patte à laquelle le joueur est agrippé, ou null. */
	public static Patte aUnePatte(LivingEntity entite) {
		if (!(entite instanceof Player) || entite.isSpectator()) {
			return null;
		}
		Level niveau = entite.level();
		Source source = niveau.isClientSide() ? client : serveur;
		if (source == null) {
			return null;
		}
		double temps = niveau.getGameTime();
		double x = entite.getX(), y = entite.getY() + 0.9, z = entite.getZ();
		if (niveau.dimension() != LeTitan.DOS) {
			Repere repere = source.repere(niveau, temps);
			if (repere == null) {
				return null;
			}
			double xd = repere.versDosX(x, z), zd = repere.versDosZ(x, z);
			x = xd;
			z = zd;
			y = repere.versDosY(y);
		}
		return patteProche(x, y, z, temps, source.veille(niveau), source.marche(niveau));
	}

	/**
	 * La patte qui touche ce point du repère du dos (ou le contient), ou null.
	 * Indépendant de Minecraft : testé seul.
	 */
	public static Patte patteProche(double x, double y, double z, double temps, double veille, double marche) {
		for (Patte patte : Patte.values()) {
			Vector3f hanche = Squelette.ancrePatte(patte);
			if (Math.hypot(x - hanche.x, z - hanche.z) > PORTEE_PATTE || y > hanche.y + 15) {
				continue;
			}
			Pose[] segments = Squelette.patte(patte, temps, veille, marche);
			for (int i = 0; i < 3; i++) {
				// Le point dans le repère de la pièce : on défait la position puis la rotation.
				Vector3f local = new Vector3f((float) x, (float) y, (float) z).sub(hanche).sub(segments[i].pivot());
				new Quaternionf(segments[i].rotation()).conjugate().transform(local);
				float dessous = i == 2 ? SOUS_LE_PIED : PRISE;
				for (Boite b : BOITES.get(i)) {
					if (local.x > b.x0() - PRISE && local.x < b.x1() + PRISE
							&& local.y > b.y0() - dessous && local.y < b.y1() + PRISE
							&& local.z > b.z0() - PRISE && local.z < b.z1() + PRISE) {
						return patte;
					}
				}
			}
		}
		return null;
	}
}
