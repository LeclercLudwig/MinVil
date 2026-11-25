package fr.letitan.vie;

import fr.letitan.vie.Boite.Bloc;

import java.util.ArrayList;
import java.util.List;

/**
 * Le modèle en boîtes des parties animées du Titan : une tête de dragon (avec sa mâchoire)
 * et des pattes en trois segments (cuisse, jambe, pied griffu).
 *
 * Chaque pièce est décrite dans son propre repère, l'origine étant son articulation :
 * le cou pour la tête, la charnière pour la mâchoire, la hanche, le genou et la cheville
 * pour les pattes. Le Titan regarde vers le nord (z négatif).
 */
public final class ModeleTitan {

	/** Les pièces qui bougent. */
	public enum Piece { TETE, MACHOIRE, CUISSE, JAMBE, PIED }

	/** Agrandissement de la tête, et épaississement des pattes (leur longueur ne change pas). */
	private static final float ECHELLE_TETE = 1.3f, EPAISSEUR_PATTES = 1.35f;
	/** Charnière de la mâchoire, dans le repère de la tête. */
	public static final float CHARNIERE_Y = -7 * ECHELLE_TETE, CHARNIERE_Z = -24 * ECHELLE_TETE;
	/**
	 * Taille maximale d'une tuile, en blocs. Chaque tuile est une entité : plus grandes,
	 * elles sont moins nombreuses (moins de calcul), mais leur texture est plus étirée.
	 */
	public static final float TUILE = 16;
	/** Longueur de la cuisse (hanche -> genou) et de la jambe (genou -> cheville). */
	public static final float CUISSE = 38, JAMBE = 40;

	private ModeleTitan() {
	}

	public static List<Boite> boites(Piece piece) {
		return switch (piece) {
			case TETE -> echelle(tete(), ECHELLE_TETE, ECHELLE_TETE, ECHELLE_TETE);
			case MACHOIRE -> echelle(machoire(), ECHELLE_TETE, ECHELLE_TETE, ECHELLE_TETE);
			case CUISSE -> echelle(cuisse(), EPAISSEUR_PATTES, 1, EPAISSEUR_PATTES);
			case JAMBE -> echelle(jambe(), EPAISSEUR_PATTES, 1, EPAISSEUR_PATTES);
			case PIED -> echelle(pied(), EPAISSEUR_PATTES, 1, EPAISSEUR_PATTES);
		};
	}

	// --- Tête ----------------------------------------------------------------

	private static List<Boite> tete() {
		List<Boite> b = new ArrayList<>();
		// Base du cou, enfoncée dans le corps pour cacher la jointure.
		b.add(Boite.centree(26, -12, 12, -18, 10, Bloc.PEAU));
		// Crâne, puis museau qui s'affine en trois étages.
		b.add(Boite.centree(36, -8, 14, -48, -14, Bloc.PEAU));
		b.add(Boite.centree(26, -7, 9, -66, -48, Bloc.PEAU));
		b.add(Boite.centree(22, -7, 7, -80, -66, Bloc.PEAU));
		b.add(Boite.centree(18, -7, 5, -90, -80, Bloc.PEAU));
		// Écailles sur le dessus du crâne et du museau.
		b.add(Boite.centree(28, 14, 16, -44, -18, Bloc.ECAILLE));
		b.add(Boite.centree(16, 9, 11, -64, -48, Bloc.ECAILLE));
		// Palais rouge, visible quand la gueule s'ouvre.
		b.add(Boite.centree(20, -8, -7, -88, -26, Bloc.GUEULE));
		// Narines au bout du museau.
		b.addAll(paire(new Boite(3, 5, -90.5f, 7, 7, -84, Bloc.NARINE)));
		// Arcades au-dessus des yeux, et yeux sur les côtés.
		b.addAll(paire(new Boite(9, 12, -50, 20, 18, -32, Bloc.PEAU)));
		b.addAll(paire(new Boite(17.5f, 3, -46, 19.5f, 9, -36, Bloc.OEIL)));
		// Dents du haut, le long du museau.
		for (float z = -88; z <= -52; z += 6) {
			b.addAll(paire(new Boite(8, -11, z, 10, -7, z + 2, Bloc.OS)));
		}
		// Cornes : quatre segments qui partent vers l'arrière en montant.
		float[][] corne = {{9, 15, 12, 20, -24, -12}, {10, 15, 18, 27, -14, -2}, {11, 15, 25, 33, -4, 8}, {12, 15, 31, 38, 6, 16}};
		for (float[] s : corne) {
			b.addAll(paire(new Boite(s[0], s[2], s[4], s[1], s[3], s[5], Bloc.OS)));
		}
		// Crête d'épines au milieu du crâne, qui prolonge celle du dos.
		for (float z = -42; z <= -10; z += 10) {
			b.add(Boite.centree(3, 16, 22, z, z + 5, Bloc.OS));
		}
		// Collerette d'épines sur les joues.
		b.addAll(paire(new Boite(17, -4, -26, 25, 2, -18, Bloc.OS)));
		b.addAll(paire(new Boite(16, 2, -20, 22, 7, -14, Bloc.OS)));
		return b;
	}

	private static List<Boite> machoire() {
		List<Boite> b = new ArrayList<>();
		// Repère : origine à la charnière ; la mâchoire s'étend vers l'avant (z négatif).
		// (Comme la tête, elle est agrandie de ECHELLE_TETE après coup.)
		b.add(Boite.centree(24, -9, 0, -40, 0, Bloc.PEAU));
		b.add(Boite.centree(20, -8, 0, -56, -40, Bloc.PEAU));
		b.add(Boite.centree(16, -7, 0, -64, -56, Bloc.PEAU));
		// Langue et intérieur de la gueule.
		b.add(Boite.centree(16, -1, 0.5f, -60, -4, Bloc.GUEULE));
		// Dents du bas.
		for (float z = -62; z <= -30; z += 6) {
			b.addAll(paire(new Boite(6, 0, z, 8, 4, z + 2, Bloc.OS)));
		}
		// Barbillons d'os sous le menton.
		b.add(Boite.centree(4, -16, -9, -50, -46, Bloc.OS));
		b.addAll(paire(new Boite(6, -14, -40, 9, -9, -36, Bloc.OS)));
		return b;
	}

	// --- Pattes --------------------------------------------------------------

	private static List<Boite> cuisse() {
		List<Boite> b = new ArrayList<>();
		// Épaule massive, enfoncée dans le ventre, puis cuisse qui s'affine.
		b.add(Boite.centree(26, -14, 10, -13, 13, Bloc.PEAU));
		b.add(Boite.centree(20, -CUISSE - 4, -14, -10, 10, Bloc.PEAU));
		// Écailles sur le devant de la cuisse.
		b.add(Boite.centree(14, -30, -6, -12, -10, Bloc.ECAILLE));
		return b;
	}

	private static List<Boite> jambe() {
		List<Boite> b = new ArrayList<>();
		// Genou, puis jambe plus fine.
		b.add(Boite.centree(18, -8, 6, -9, 9, Bloc.PEAU));
		b.add(Boite.centree(13, -JAMBE - 2, -8, -6.5f, 6.5f, Bloc.PEAU));
		// Ergot d'os derrière la jambe.
		b.add(Boite.centree(3, -22, -12, 6.5f, 12, Bloc.OS));
		return b;
	}

	private static List<Boite> pied() {
		List<Boite> b = new ArrayList<>();
		// Cheville, puis pied qui s'étale vers l'avant.
		b.add(Boite.centree(15, -6, 4, -8, 8, Bloc.PEAU));
		b.add(Boite.centree(20, -10, -3, -22, 7, Bloc.PEAU));
		// Trois orteils et leurs griffes.
		for (float x : new float[] {-7, 0, 7}) {
			b.add(new Boite(x - 3, -10, -30, x + 3, -5, -22, Bloc.PEAU));
			b.add(new Boite(x - 1.5f, -11, -36, x + 1.5f, -7, -30, Bloc.OS));
		}
		return b;
	}

	private static List<Boite> echelle(List<Boite> boites, float sx, float sy, float sz) {
		return boites.stream().map(b -> new Boite(b.x0() * sx, b.y0() * sy, b.z0() * sz,
				b.x1() * sx, b.y1() * sy, b.z1() * sz, b.bloc())).toList();
	}

	/** La boîte et son reflet de l'autre côté de la tête ou de la patte. */
	private static List<Boite> paire(Boite droite) {
		return List.of(droite, droite.miroir());
	}
}
