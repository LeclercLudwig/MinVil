package fr.letitan.voyage;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RepereTest {

	@Test
	void allerRetourEntreLeDosEtLeSol() {
		Repere r = new Repere(1234.5, -876.25, 2.1, 87);
		double xs = r.versSolX(37, -120), zs = r.versSolZ(37, -120);
		assertEquals(37, r.versDosX(xs, zs), 1e-9);
		assertEquals(-120, r.versDosZ(xs, zs), 1e-9);
		assertEquals(55, r.versDosY(r.versSolY(55)), 1e-9);
	}

	@Test
	void memeCorrespondanceQueNavigation() {
		EtatVoyage etat = new EtatVoyage();
		etat.x = 300;
		etat.z = -40;
		etat.cap = 0.7;
		double[] attendu = Navigation.auSol(etat, 12, -80);
		Repere r = etat.repere();
		assertEquals(attendu[0], r.versSolX(12, -80), 1e-9);
		assertEquals(attendu[1], r.versSolZ(12, -80), 1e-9);
	}

	@Test
	void lesPiedsTouchentLeSol() {
		Repere r = new Repere(0, 0, 0, 72);
		assertEquals(72, r.versSolY(Repere.Y_SOL), 1e-9);
	}

	@Test
	void leClientPrevoitLaMarcheCommeLeServeur() {
		Navigation.Terrain plaines = (x, z) -> Navigation.Genre.AUTRE;
		EtatVoyage etat = new EtatVoyage();
		etat.cap = 0.3;
		etat.capVise = 1.4;
		double[] prevu = Navigation.prolonger(etat.x, etat.z, etat.cap, etat.capVise, 60);
		// Temps 1 à 60 : pas de nouveau choix de cap (tous les 100 ticks).
		for (long t = 1; t <= 60; t++) {
			Navigation.tick(etat, plaines, t, true);
		}
		assertEquals(etat.x, prevu[0], 1e-9);
		assertEquals(etat.z, prevu[1], 1e-9);
		assertEquals(etat.cap, prevu[2], 1e-9);
	}
}
