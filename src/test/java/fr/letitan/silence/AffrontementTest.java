package fr.letitan.silence;

import fr.letitan.voyage.Repere;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AffrontementTest {

	@Test
	void uneFlecheSurUnKysteLeTouche() {
		Repere notre = new Repere(500, -300, 0.7, 72);
		double[] p = Affrontement.position(1);
		Repere muet = new Repere(notre.versSolX(p[0], p[1]), notre.versSolZ(p[0], p[1]), notre.cap, 70);
		int[] solidite = {4, 4, 4, 4, 4};
		for (int i = 0; i < Affrontement.KYSTES; i++) {
			double[] k = Affrontement.kyste(i, 1);
			double x = muet.versSolX(k[0], k[2]), z = muet.versSolZ(k[0], k[2]), y = muet.versSolY(k[1]);
			assertEquals(i, Affrontement.kysteTouche(muet, 1, solidite, x + 1, y - 1, z + 2));
		}
		// Loin des kystes : rien.
		assertEquals(-1, Affrontement.kysteTouche(muet, 1, solidite, muet.x, muet.versSolY(200), muet.z));
		// Un kyste crevé ne compte plus.
		double[] k = Affrontement.kyste(2, 1);
		solidite[2] = 0;
		assertEquals(-1, Affrontement.kysteTouche(muet, 1, solidite, muet.versSolX(k[0], k[2]), muet.versSolY(k[1]), muet.versSolZ(k[0], k[2])));
	}

	@Test
	void lesKystesSontDuCoteQuIlNousPresente() {
		for (int cote : new int[] {-1, 1}) {
			for (int i = 0; i < Affrontement.KYSTES; i++) {
				// Vus depuis notre dos, ils sont entre son échine et nous.
				double xNotre = Affrontement.position(cote)[0] + Affrontement.kyste(i, cote)[0];
				assertTrue(Math.abs(xNotre) < Affrontement.ECART, "kyste " + i + " du mauvais côté");
			}
		}
	}

	@Test
	void laChargeVaEtVient() {
		assertEquals(0, Affrontement.charge(Affrontement.DUREE_CHARGE + 10), 1e-9);
		long choc = (long) (0.3 * Affrontement.DUREE_CHARGE);
		assertTrue(Affrontement.choc(choc));
		assertEquals(1, Affrontement.charge(choc), 1e-6);
		assertTrue(Affrontement.charge(Affrontement.DUREE_CHARGE - 1) < 0.05);
	}
}
