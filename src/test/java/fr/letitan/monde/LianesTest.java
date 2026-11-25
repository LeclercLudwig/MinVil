package fr.letitan.monde;

import fr.letitan.vie.Accroche;
import fr.letitan.vie.AnimationTitan.Patte;
import fr.letitan.vie.Squelette;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LianesTest {

	/** La position x de la liane à cette hauteur, du côté ouest, ou null. */
	private static Integer lianeOuest(int y, int z) {
		for (int x = -1; x > -100; x--) {
			if (FormeTitan.liane(x, y, z) != 0) {
				return x;
			}
		}
		return null;
	}

	@Test
	void unCheminDeLianesMeneDuMilieuDeLaPatteAuDos() {
		for (Patte patte : Patte.values()) {
			if (patte.cote > 0) {
				continue;
			}
			int z = (int) Squelette.ancrePatte(patte).z;
			Integer precedente = null;
			int y = FormeTitan.Y_LIANES_BAS;
			for (; lianeOuest(y, z) != null; y++) {
				int x = lianeOuest(y, z);
				assertEquals(1, FormeTitan.liane(x, y, z), "accrochée vers le corps, à l'est");
				if (precedente != null) {
					// D'une rangée à l'autre, la liane se décale de deux blocs au plus : le bloc entre
					// les deux est une marche de peau, sur laquelle on monte.
					assertTrue(Math.abs(x - precedente) <= 2, "liane interrompue à y = " + y);
					if (Math.abs(x - precedente) == 2) {
						assertEquals(FormeTitan.Matiere.PEAU, FormeTitan.matiere((x + precedente) / 2, y - 1, z));
					}
				}
				precedente = x;
			}
			// Au-dessus de la dernière liane : le dos, où l'on peut marcher.
			int xHaut = precedente + 1;
			assertTrue(y > FormeTitan.Y_CENTRE + 10, "les lianes s'arrêtent trop bas : " + y);
			assertTrue(FormeTitan.hautDuDos(xHaut, z) >= y - 2, "le haut des lianes ne mène pas au dos");
		}
	}

	@Test
	void leBasDesLianesEstAPorteeDeLaPatte() {
		for (Patte patte : Patte.values()) {
			Vector3f hanche = Squelette.ancrePatte(patte);
			int z = (int) hanche.z;
			int y = FormeTitan.Y_LIANES_BAS + 1;
			int x = 0;
			for (int i = 1; i < 100; i++) {
				int xi = patte.cote * i;
				if (FormeTitan.liane(xi, y, z) != 0) {
					x = xi;
				}
			}
			assertTrue(x != 0, "pas de liane en bas pour " + patte);
			// Debout, à l'arrêt : la patte touche le rideau de lianes.
			assertNotNull(Accroche.patteProche(x + 0.5, y + 0.9, z + 0.5, 0, 1, 0), "la patte " + patte + " n'atteint pas les lianes");
		}
	}
}
