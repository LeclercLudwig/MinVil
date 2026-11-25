package fr.letitan.vie;

import java.util.ArrayList;
import java.util.List;

/**
 * Une boîte du modèle du Titan, dans le repère de sa pièce (en blocs).
 * Repère : x vers l'est, y vers le haut, z vers le sud ; le Titan regarde vers le nord (z négatif).
 */
public record Boite(float x0, float y0, float z0, float x1, float y1, float z1, Bloc bloc) {

	/** De quoi la boîte est faite. */
	public enum Bloc { PEAU, ECAILLE, OS, OEIL, NARINE, GUEULE }

	public Boite {
		if (x1 <= x0 || y1 <= y0 || z1 <= z0) {
			throw new IllegalArgumentException("boîte vide : " + x0 + " " + y0 + " " + z0 + " " + x1 + " " + y1 + " " + z1);
		}
	}

	/** Une boîte symétrique en x : de -largeur/2 à +largeur/2. */
	public static Boite centree(float largeur, float y0, float y1, float z0, float z1, Bloc bloc) {
		return new Boite(-largeur / 2, y0, z0, largeur / 2, y1, z1, bloc);
	}

	/** La même boîte, reflétée de l'autre côté (x -> -x). */
	public Boite miroir() {
		return new Boite(-x1, y0, z0, -x0, y1, z1, bloc);
	}

	public float largeur() {
		return x1 - x0;
	}

	public float hauteur() {
		return y1 - y0;
	}

	public float profondeur() {
		return z1 - z0;
	}

	/**
	 * Découpe la boîte en tuiles d'au plus {@code max} blocs de côté, en ne gardant que celles
	 * de la surface : l'intérieur ne se voit pas. Une tuile est un bloc agrandi ; la découper
	 * évite d'étirer la texture sur toute la longueur d'une grande boîte.
	 */
	public List<Boite> tuiles(float max) {
		int nx = Math.max(1, (int) Math.ceil(largeur() / max));
		int ny = Math.max(1, (int) Math.ceil(hauteur() / max));
		int nz = Math.max(1, (int) Math.ceil(profondeur() / max));
		float dx = largeur() / nx, dy = hauteur() / ny, dz = profondeur() / nz;
		List<Boite> tuiles = new ArrayList<>();
		for (int i = 0; i < nx; i++) {
			for (int j = 0; j < ny; j++) {
				for (int k = 0; k < nz; k++) {
					boolean surface = i == 0 || j == 0 || k == 0 || i == nx - 1 || j == ny - 1 || k == nz - 1;
					if (surface) {
						tuiles.add(new Boite(x0 + i * dx, y0 + j * dy, z0 + k * dz,
								x0 + (i + 1) * dx, y0 + (j + 1) * dy, z0 + (k + 1) * dz, bloc));
					}
				}
			}
		}
		return tuiles;
	}
}
