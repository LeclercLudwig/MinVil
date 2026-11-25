package fr.letitan.monde;

import fr.letitan.monde.FormeTitan.Matiere;
import fr.letitan.monde.Interieur.Organe;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

class InterieurTest {
	private static final int[][] VOISINS = {{1, 0, 0}, {-1, 0, 0}, {0, 1, 0}, {0, -1, 0}, {0, 0, 1}, {0, 0, -1}};
	/** Tous les blocs où l'on peut aller (air, liquide, plante) depuis l'évent, à l'intérieur du corps. */
	private static final Set<Long> ATTEINTS = new HashSet<>();

	private static long cle(int x, int y, int z) {
		return ((long) (x + 1000) << 40) | ((long) (y + 1000) << 20) | (z + 1000);
	}

	@BeforeAll
	static void explorer() {
		// On descend dans l'évent depuis le dos.
		int x = Interieur.EVENT[0], z = Interieur.EVENT[1];
		int y = FormeTitan.hautDuDos(x, z);
		while (FormeTitan.matiere(x, y, z) != Matiere.CAVITE) {
			y--;
		}
		ArrayDeque<int[]> file = new ArrayDeque<>();
		file.add(new int[] {x, y, z});
		ATTEINTS.add(cle(x, y, z));
		while (!file.isEmpty()) {
			int[] p = file.poll();
			Organe ici = Interieur.organe(p[0] + 0.5, p[1] + 0.5, p[2] + 0.5);
			for (int[] v : VOISINS) {
				int nx = p[0] + v[0], ny = p[1] + v[1], nz = p[2] + v[2];
				Matiere m = FormeTitan.matiere(nx, ny, nz);
				if (m == Matiere.VIDE) {
					// L'air du dehors : seulement par l'évent et le puits de sortie.
					if (ici != Organe.EVENT && ici != Organe.SORTIE) {
						fail("l'intérieur s'ouvre sur le dehors en " + nx + " " + ny + " " + nz + " (" + ici + ")");
					}
					continue;
				}
				if (!m.plein() && ATTEINTS.add(cle(nx, ny, nz))) {
					file.add(new int[] {nx, ny, nz});
				}
			}
		}
	}

	private static boolean atteint(double[] centre) {
		return ATTEINTS.contains(cle((int) Math.floor(centre[0]), (int) Math.floor(centre[1]), (int) Math.floor(centre[2])));
	}

	@Test
	void depuisLEventOnAtteintTousLesOrganes() {
		assertTrue(atteint(Interieur.COEUR), "cœur");
		assertTrue(atteint(Interieur.POUMONS[0]), "poumon gauche");
		assertTrue(atteint(Interieur.POUMONS[1]), "poumon droit");
		assertTrue(atteint(Interieur.ESTOMAC), "estomac");
		assertTrue(atteint(new double[] {-34, 56, 10}), "veine");
	}

	@Test
	void laVeineMeneAuPuitsDeSortie() {
		int x = Interieur.SORTIE_HAUT[0], z = Interieur.SORTIE_HAUT[1];
		int y = FormeTitan.hautDuDos(x, z);
		assertTrue(atteint(new double[] {x, y - 2, z}), "le puits de sortie n'est pas relié à l'intérieur");
	}

	@Test
	void leSangEtLAcideNeDebordentPasDansLesAutresOrganes() {
		for (long cle : ATTEINTS) {
			int x = (int) (cle >>> 40) - 1000, y = (int) ((cle >>> 20) & 0xFFFFF) - 1000, z = (int) (cle & 0xFFFFF) - 1000;
			Matiere m = FormeTitan.matiere(x, y, z);
			if (m != Matiere.SANG && m != Matiere.ACIDE) {
				continue;
			}
			Organe organe = Interieur.organe(x + 0.5, y + 0.5, z + 0.5);
			for (int[] v : new int[][] {{1, 0, 0}, {-1, 0, 0}, {0, 0, 1}, {0, 0, -1}}) {
				Matiere voisin = FormeTitan.matiere(x + v[0], y, z + v[2]);
				if (voisin == Matiere.CAVITE || voisin == Matiere.VRILLE) {
					assertEquals(organe, Interieur.organe(x + v[0] + 0.5, y + 0.5, z + v[2] + 0.5),
							"le liquide de " + organe + " coule dans un autre organe en " + x + " " + y + " " + z);
				}
			}
		}
	}

	@Test
	void lInterieurEstAssezGrandPourSyPromener() {
		assertTrue(ATTEINTS.size() > 50_000, "seulement " + ATTEINTS.size() + " blocs");
	}
}
