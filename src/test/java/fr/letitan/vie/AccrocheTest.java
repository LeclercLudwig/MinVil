package fr.letitan.vie;

import fr.letitan.vie.AnimationTitan.Patte;
import fr.letitan.voyage.Repere;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class AccrocheTest {

	@Test
	void onSAgrippeAuPiedDepuisLeSol() {
		// Le Titan arrêté, debout : on arrive au sol à côté de son pied avant gauche.
		Vector3f hanche = Squelette.ancrePatte(Patte.AVANT_GAUCHE);
		double y = Repere.Y_SOL + 1.5;
		Patte trouvee = null;
		for (double dz = -40; dz <= 10 && trouvee == null; dz += 1) {
			trouvee = Accroche.patteProche(hanche.x, y, hanche.z + dz, 0, 1, 0);
		}
		assertEquals(Patte.AVANT_GAUCHE, trouvee, "le pied doit toucher le sol, à portée de main");
	}

	@Test
	void onPeutGrimperJusquAuVentre() {
		Vector3f hanche = Squelette.ancrePatte(Patte.ARRIERE_DROITE);
		// Tout le long de la patte, à l'extérieur, on reste agrippé.
		for (double y = Repere.Y_SOL + 8; y < hanche.y; y += 2) {
			boolean prise = false;
			for (double dx = -20; dx <= 20 && !prise; dx += 0.5) {
				prise = Accroche.patteProche(hanche.x + dx, y, hanche.z, 0, 1, 0) != null;
			}
			assertEquals(true, prise, "rien à quoi s'agripper à la hauteur " + y);
		}
	}

	@Test
	void loinDesPattesOnNeSAgrippePas() {
		assertNull(Accroche.patteProche(0, 20, 0, 0, 1, 0));
		Vector3f hanche = Squelette.ancrePatte(Patte.AVANT_DROITE);
		assertNull(Accroche.patteProche(hanche.x + 40, 0, hanche.z, 0, 1, 0));
	}
}
