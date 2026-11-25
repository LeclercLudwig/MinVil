package fr.letitan.monde;

import fr.letitan.monde.FormeTitan.Matiere;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FormeTitanTest {

	@Test
	void leDosEstCouvertDHerbeAuMilieuDuCorps() {
		int haut = FormeTitan.hautDuDos(20, 0);
		assertTrue(haut > FormeTitan.Y_CENTRE + 20, "dos trop bas : " + haut);
		assertEquals(Matiere.HERBE, FormeTitan.matiere(20, haut, 0));
		assertEquals(Matiere.TERRE, FormeTitan.matiere(20, haut - 1, 0));
		assertEquals(Matiere.VIDE, FormeTitan.matiere(20, haut + 1, 0));
	}

	@Test
	void leCorpsEstDeLaChairSousLaPeau() {
		assertEquals(Matiere.CHAIR, FormeTitan.matiere(0, FormeTitan.Y_CENTRE, 0));
	}

	@Test
	void laChairNestJamaisALAir() {
		// Aucun bloc de chair ne touche le vide : la peau l'enveloppe partout.
		int[][] voisins = {{1, 0, 0}, {-1, 0, 0}, {0, 1, 0}, {0, -1, 0}, {0, 0, 1}, {0, 0, -1}};
		// Pas fin près du cou, où la tête et le corps se rejoignent, plus large ailleurs.
		for (int z = FormeTitan.AVANT - 100; z <= FormeTitan.ARRIERE; z += z < FormeTitan.AVANT + 60 ? 1 : 7) {
			for (int x = -100; x <= 100; x += 3) {
				for (int y = 0; y <= 140; y += 2) {
					if (FormeTitan.matiere(x, y, z) != Matiere.CHAIR) {
						continue;
					}
					for (int[] v : voisins) {
						assertNotEquals(Matiere.VIDE, FormeTitan.matiere(x + v[0], y + v[1], z + v[2]),
								"chair à l'air en " + x + " " + y + " " + z);
					}
				}
			}
		}
	}

	@Test
	void leCouSeTermineEnPointe() {
		// Plus de tranche plate : le corps s'affine jusqu'à disparaître au bout du cou.
		assertEquals(0, FormeTitan.demiLargeur(FormeTitan.AVANT), 1e-9);
		assertTrue(FormeTitan.demiLargeur(FormeTitan.AVANT + 5) < FormeTitan.demiLargeur(FormeTitan.AVANT + 40));
	}

	@Test
	void horsDuCorpsCestLeVide() {
		assertEquals(Matiere.VIDE, FormeTitan.matiere(200, FormeTitan.Y_CENTRE, 0));
		assertEquals(Matiere.VIDE, FormeTitan.matiere(0, FormeTitan.Y_CENTRE, FormeTitan.ARRIERE + 50));
	}

	@Test
	void laCreteDOsDepasseDuDos() {
		int base = FormeTitan.hautDuDos(0, 3);
		assertEquals(Matiere.OS, FormeTitan.matiere(0, base + 3, 3));
	}
}
