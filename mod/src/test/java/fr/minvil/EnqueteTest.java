package fr.minvil;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.StringReader;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Vérifie que les enquêtes embarquées se lisent et se jouent. */
class EnqueteTest {

	@Test
	void toutesLesEnquetesEmbarqueesSeLisent() throws IOException {
		for (int seed = 0; seed < Enquete.NOMBRE; seed++) {
			Enquete e = Enquete.charger(seed);
			assertEquals(seed, e.seed());
			List<Enquete.Villageois> vivants = e.vivants();
			assertTrue(vivants.size() >= 4, "seed " + seed + " : trop peu de villageois");
			for (Enquete.Villageois v : vivants) {
				assertFalse(e.declaration(v.id()).isEmpty(), "seed " + seed + " : " + v.id() + " muet");
				assertTrue(v.professionMinecraft().startsWith("minecraft:"), v.professionMinecraft());
				assertEquals(v.id(), e.trouver(v.nom().toUpperCase()).orElseThrow().id());
			}
			assertEquals(1, vivants.stream().filter(v -> e.estCoupable(v.id())).count(),
					"seed " + seed + " : il faut exactement un coupable");
		}
	}

	@Test
	void laFouilleAjouteLaReconnaissanceDeLObjet() throws IOException {
		for (int seed = 0; seed < Enquete.NOMBRE; seed++) {
			Enquete e = Enquete.charger(seed);
			int avant = e.vivants().stream().mapToInt(v -> e.declaration(v.id()).size()).sum();
			e.trouverIndice();
			int apres = e.vivants().stream().mapToInt(v -> e.declaration(v.id()).size()).sum();
			assertTrue(apres > avant, "seed " + seed + " : personne ne reconnaît l'objet");
		}
	}

	@Test
	void lAccusationEtLExplicationConcordent() throws IOException {
		Enquete e = Enquete.charger(0);
		assertTrue(e.estCoupable(e.trouver("Aldric").orElseThrow().id()));
		assertFalse(e.estCoupable(e.trouver("Isembert").orElseThrow().id()));
		assertTrue(e.explication().get(0).startsWith("Aldric"));
	}

	@Test
	void unFormatInconnuEstRefuse() {
		assertThrows(IOException.class,
				() -> Enquete.lire(new StringReader("{\"format\":\"autre\",\"version\":1}")));
		assertThrows(IOException.class,
				() -> Enquete.lire(new StringReader("{\"format\":\"minvil-enquete\",\"version\":2}")));
		assertThrows(IOException.class, () -> Enquete.lire(new StringReader("pas du json")));
	}
}
