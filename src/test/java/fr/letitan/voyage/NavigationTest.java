package fr.letitan.voyage;

import fr.letitan.voyage.Navigation.Arret;
import fr.letitan.voyage.Navigation.Genre;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NavigationTest {

	/** Des plaines partout. */
	private static final Navigation.Terrain PLAINES = (x, z) -> Genre.AUTRE;

	@Test
	void ilAvanceQuandIlEstEveille() {
		EtatVoyage etat = new EtatVoyage();
		for (long t = 1; t <= 1000; t++) {
			Navigation.tick(etat, PLAINES, t, true);
		}
		assertEquals(300, etat.odometre, 1e-6);
		double distance = Math.hypot(etat.x, etat.z);
		assertTrue(distance > 250, "le Titan a trop tourné en rond : " + distance);
	}

	@Test
	void laNuitIlNeBougePas() {
		EtatVoyage etat = new EtatVoyage();
		for (long t = 1; t <= 1000; t++) {
			Navigation.tick(etat, PLAINES, t, false);
		}
		assertEquals(0, etat.odometre);
		assertEquals(0, etat.x);
	}

	@Test
	void ilEviteLOcean() {
		// Un océan au nord (z < -200) ; le Titan part vers le nord, à 200 blocs du rivage.
		Navigation.Terrain cote = (x, z) -> z < -200 ? Genre.OCEAN : Genre.AUTRE;
		EtatVoyage etat = new EtatVoyage();
		for (long t = 0; t < 20000; t++) {
			Navigation.tick(etat, cote, t, true);
			assertTrue(etat.z > -200, "le Titan est entré dans l'océan au tick " + t + " (z = " + etat.z + ")");
		}
	}

	@Test
	void ilSArretePourBoirePuisRepart() {
		// Une rivière qui traverse tout, de z = -40 à z = -60.
		Navigation.Terrain riviere = (x, z) -> z < -40 && z > -60 ? Genre.RIVIERE : Genre.AUTRE;
		EtatVoyage etat = new EtatVoyage();
		long t = 0;
		while (etat.arret == Arret.AUCUN && t < 2000) {
			Navigation.tick(etat, riviere, ++t, true);
		}
		assertEquals(Arret.BOIRE, etat.arret);
		double odometre = etat.odometre;
		for (int i = 0; i < Navigation.DUREE_ARRET; i++) {
			assertFalse(etat.enMarche());
			Navigation.tick(etat, riviere, ++t, true);
		}
		assertEquals(odometre, etat.odometre, 1e-9, "il ne doit pas avancer pendant qu'il boit");
		assertTrue(etat.enMarche(), "il repart après l'arrêt");
		// Il ne s'arrête pas une deuxième fois dans la même rivière.
		for (int i = 0; i < 200; i++) {
			Navigation.tick(etat, riviere, ++t, true);
			assertTrue(etat.enMarche());
		}
	}

	@Test
	void onAtterritSousLEndroitOuLOnEtaitSurLeDos() {
		EtatVoyage etat = new EtatVoyage();
		etat.x = 1000;
		etat.z = 500;
		// Cap au nord : le dos est orienté comme le monde d'en bas.
		double[] p = Navigation.auSol(etat, 10, -20);
		assertEquals(1010, p[0], 1e-9);
		assertEquals(480, p[1], 1e-9);
		// Cap à l'est : l'avant du Titan (nord sur le dos) est l'est en bas, sa droite est le sud.
		etat.cap = Math.PI / 2;
		p = Navigation.auSol(etat, 10, -20);
		assertEquals(1020, p[0], 1e-9);
		assertEquals(510, p[1], 1e-9);
	}

	@Test
	void lesDirectionsSontEnToutesLettres() {
		assertEquals("le nord", Navigation.direction(0));
		assertEquals("l'est", Navigation.direction(Math.PI / 2));
		assertEquals("le nord-ouest", Navigation.direction(-Math.PI / 4));
		assertEquals("le sud", Navigation.direction(3 * Math.PI));
	}
}
