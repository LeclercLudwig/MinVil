package fr.letitan.silence;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CarcasseTest {

	@Test
	void onMarcheSousLaCageDeCotes() {
		// Au milieu de la cage, à hauteur d'homme : de l'air.
		for (double v = -80; v <= 20; v += 3) {
			if (Math.abs(v - Carcasse.COEUR[2]) < 7) {
				continue;
			}
			assertNull(Carcasse.os(0, 1, v), "bloqué sous la cage en v = " + v);
			assertNull(Carcasse.os(0, 2, v), "bloqué sous la cage en v = " + v);
		}
		// Mais des côtes au-dessus et sur les côtés.
		int cotes = 0;
		for (double v = -85; v <= 25; v += 7) {
			for (double u = 0; u < 40; u += 0.5) {
				if (Carcasse.os(u, 1, v) != null) {
					cotes++;
					break;
				}
			}
		}
		assertTrue(cotes >= 12, "seulement " + cotes + " côtes touchent le sol");
	}

	@Test
	void leCoeurEtLesReliquesSontDansLaCage() {
		double[] c = Carcasse.COEUR;
		assertEquals(Carcasse.Os.COEUR, Carcasse.os(c[0], c[1], c[2]) == Carcasse.Os.LARME ? Carcasse.Os.COEUR : Carcasse.os(c[0], c[1], c[2]));
		double[] r = Carcasse.RELIQUES;
		assertNull(Carcasse.os(r[0], r[1], r[2]), "le coffre des reliques est dans les os");
		assertTrue(Carcasse.epine(r[2]) > 15, "les reliques ne sont pas à l'abri de la cage");
	}

	@Test
	void leCraneEstCreuxEtOnPeutYEntrer() {
		double[] c = Carcasse.CRANE;
		assertNull(Carcasse.os(c[0], c[1], c[2]), "le coffre du crâne est dans les os");
		assertNull(Carcasse.os(0, 9, -124), "le crâne n'est pas creux");
		assertNotNull(Carcasse.os(0, 17.5, -124), "le crâne n'a pas de dessus");
		// On y entre par la gueule, à l'avant, au ras du sol.
		assertNull(Carcasse.os(0, 2, -138));
	}

	@Test
	void lesCarcassesSontRaresEtLoinDuDepart() {
		Set<Long> vues = new HashSet<>();
		for (int i = -12; i <= 12; i++) {
			for (int j = -12; j <= 12; j++) {
				Carcasse c = Carcasse.dansLaCase(i, j, 42, 0, 0);
				if (c != null) {
					vues.add(c.cle());
					assertTrue(Math.hypot(c.cx(), c.cz()) >= Carcasse.LOIN_DU_DEPART);
					// On la retrouve depuis son centre.
					assertTrue(Carcasse.pres(c.cx(), c.cz(), 0, 42, 0, 0).contains(c));
				}
			}
		}
		// Une grille de 25 x 25 cases : une bonne part en a une, pas toutes.
		assertTrue(vues.size() > 150 && vues.size() < 500, vues.size() + " carcasses");
		List<Carcasse> a = Carcasse.pres(5000, 5000, 200, 42, 0, 0), b = Carcasse.pres(5000, 5000, 200, 42, 0, 0);
		assertEquals(a, b, "les carcasses doivent être toujours aux mêmes endroits");
	}

	@Test
	void allerEtRetourDansLeRepereDeLaCarcasse() {
		Carcasse c = new Carcasse(1000, -500, 1.1, 1.2, 0);
		double[] m = c.monde(12, -40), l = c.local(m[0], m[1]);
		assertEquals(12, l[0], 1e-9);
		assertEquals(-40, l[1], 1e-9);
	}
}
