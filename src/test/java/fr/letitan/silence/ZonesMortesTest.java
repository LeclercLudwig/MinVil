package fr.letitan.silence;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ZonesMortesTest {

	@Test
	void uneBonnePartDuMondeEstMorteMaisPasTout() {
		ZonesMortes zones = new ZonesMortes(12345, 0, 0);
		int mortes = 0, total = 0;
		for (int x = -8000; x <= 8000; x += 40) {
			for (int z = -8000; z <= 8000; z += 40) {
				total++;
				if (zones.morte(x, z)) {
					mortes++;
				}
			}
		}
		double part = mortes / (double) total;
		assertTrue(part > 0.08 && part < 0.35, "part de terres mortes : " + part);
	}

	@Test
	void leVoyageCommenceEnTerreVivante() {
		ZonesMortes zones = new ZonesMortes(987654321L, 250, -400);
		for (int x = -100; x <= 600; x += 10) {
			for (int z = -750; z <= -50; z += 10) {
				if (Math.hypot(x - 250, z + 400) < ZonesMortes.REFUGE) {
					assertFalse(zones.morte(x, z), "terre morte au refuge en " + x + " " + z);
				}
			}
		}
	}

	@Test
	void chaqueMondeASesPropresTerresMortes() {
		ZonesMortes a = new ZonesMortes(1, 0, 0), b = new ZonesMortes(2, 0, 0);
		int differences = 0;
		for (int x = 2000; x <= 6000; x += 100) {
			if (a.morte(x, 3000) != b.morte(x, 3000)) {
				differences++;
			}
		}
		assertNotEquals(0, differences);
		assertEquals(a.intensite(4000, 4000), new ZonesMortes(1, 0, 0).intensite(4000, 4000));
	}
}
