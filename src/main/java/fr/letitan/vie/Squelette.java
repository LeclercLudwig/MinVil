package fr.letitan.vie;

import fr.letitan.monde.FormeTitan;
import fr.letitan.vie.AnimationTitan.AnglesPatte;
import fr.letitan.vie.AnimationTitan.Patte;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * Le squelette du Titan : où sont accrochées la tête et les pattes, et comment les angles
 * de {@link AnimationTitan} placent chaque pièce.
 *
 * Chaque chaîne (la tête et sa mâchoire, ou une patte et ses trois segments) a une ancre fixe
 * dans le monde : le cou ou la hanche. Les poses sont données par rapport à cette ancre.
 */
public final class Squelette {

	/** Position et orientation d'une pièce, par rapport à l'ancre de sa chaîne. */
	public record Pose(Vector3f pivot, Quaternionf rotation) {
		/** Où placer le coin d'une boîte de la pièce, par rapport à l'ancre. */
		public Vector3f coin(Boite boite) {
			return rotation.transform(new Vector3f(boite.x0(), boite.y0(), boite.z0())).add(pivot);
		}
	}

	private static final int PATTE_X = 62;
	private static final int PATTE_Z_AVANT = -130, PATTE_Z_ARRIERE = 150;

	private Squelette() {
	}

	/** Le cou, au bout du corps. */
	public static Vector3f ancreTete() {
		return new Vector3f(0, FormeTitan.Y_CENTRE + 4, FormeTitan.AVANT + 12);
	}

	/** La hanche (ou l'épaule), un peu enfoncée dans le ventre. */
	public static Vector3f ancrePatte(Patte patte) {
		int x = patte.cote * PATTE_X;
		int z = patte.avant ? PATTE_Z_AVANT : PATTE_Z_ARRIERE;
		return new Vector3f(x, FormeTitan.basDuVentre(x, z) + 8, z);
	}

	public static Pose tete(double temps, double veille, double marche) {
		Quaternionf rotation = new Quaternionf()
				.rotateY((float) -AnimationTitan.lacetTete(temps, veille, marche))
				.rotateX((float) AnimationTitan.tangageTete(temps, veille, marche));
		return new Pose(new Vector3f(), rotation);
	}

	public static Pose machoire(Pose tete, double temps, double veille, double marche) {
		Vector3f charniere = tete.rotation().transform(
				new Vector3f(0, ModeleTitan.CHARNIERE_Y, ModeleTitan.CHARNIERE_Z)).add(tete.pivot());
		Quaternionf rotation = new Quaternionf(tete.rotation())
				.rotateX((float) -AnimationTitan.machoire(temps, veille, marche));
		return new Pose(charniere, rotation);
	}

	/** Les trois segments d'une patte : cuisse, jambe, pied. */
	public static Pose[] patte(Patte patte, double temps, double veille, double marche) {
		AnglesPatte angles = AnimationTitan.patte(patte, temps, veille, marche);
		Quaternionf cuisse = new Quaternionf().rotateX((float) angles.cuisse());
		Quaternionf jambe = new Quaternionf(cuisse).rotateX((float) angles.genou());
		Quaternionf pied = new Quaternionf(jambe).rotateX((float) angles.pied());
		Vector3f genou = cuisse.transform(new Vector3f(0, -ModeleTitan.CUISSE, 0));
		Vector3f cheville = jambe.transform(new Vector3f(0, -ModeleTitan.JAMBE, 0)).add(genou);
		return new Pose[] {new Pose(new Vector3f(), cuisse), new Pose(genou, jambe), new Pose(cheville, pied)};
	}
}
