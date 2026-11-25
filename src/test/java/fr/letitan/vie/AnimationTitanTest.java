package fr.letitan.vie;

import fr.letitan.Rythme;
import fr.letitan.vie.AnimationTitan.AnglesPatte;
import fr.letitan.vie.AnimationTitan.Patte;
import fr.letitan.vie.ModeleTitan.Piece;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AnimationTitanTest {

	@Test
	void chaquePatteSePoseSurUnPas() {
		// Au pas numéro k du cycle, c'est la patte d'ordre k qui touche le sol (phase 0).
		for (long temps = 0; temps < 3 * AnimationTitan.CYCLE; temps += Rythme.PERIODE) {
			assertTrue(Rythme.pas(temps));
			int poses = 0;
			for (Patte patte : Patte.values()) {
				if (AnimationTitan.phase(patte, temps) == 0) {
					poses++;
					// Les pas forts sont ceux des pattes avant.
					assertEquals(patte.avant, Rythme.force(temps) == 1.0f, patte + " au temps " + temps);
				}
			}
			assertEquals(1, poses, "une seule patte se pose au temps " + temps);
		}
	}

	@Test
	void unePatteSePosePortéeVersLAvant() {
		AnglesPatte posee = AnimationTitan.patte(Patte.AVANT_GAUCHE, 0, 1, 1);
		AnglesPatte milieu = AnimationTitan.patte(Patte.AVANT_GAUCHE, AnimationTitan.CYCLE * 0.375, 1, 1);
		assertTrue(posee.cuisse() > 0.2, "la patte doit être en avant quand elle se pose");
		assertEquals(0, milieu.cuisse(), 0.01, "à mi-appui, la patte est à la verticale");
	}

	@Test
	void lesMouvementsSontContinus() {
		// D'un tick au suivant, aucun angle ne saute : pas d'à-coups dans l'animation.
		for (double veille : new double[] {0, 0.5, 1}) {
			for (Patte patte : Patte.values()) {
				AnglesPatte avant = AnimationTitan.patte(patte, 0, veille, 1);
				for (int temps = 1; temps <= 2 * AnimationTitan.CYCLE; temps++) {
					AnglesPatte apres = AnimationTitan.patte(patte, temps, veille, 1);
					assertTrue(Math.abs(apres.cuisse() - avant.cuisse()) < 0.05, "cuisse " + patte + " " + temps);
					assertTrue(Math.abs(apres.genou() - avant.genou()) < 0.08, "genou " + patte + " " + temps);
					assertTrue(Math.abs(apres.pied() - avant.pied()) < 0.1, "pied " + patte + " " + temps);
					avant = apres;
				}
			}
			for (int temps = 1; temps <= AnimationTitan.PERIODE_RUGISSEMENT; temps++) {
				assertTrue(Math.abs(AnimationTitan.tangageTete(temps, veille, 1) - AnimationTitan.tangageTete(temps - 1, veille, 1)) < 0.05, "tête " + temps);
				assertTrue(Math.abs(AnimationTitan.machoire(temps, veille, 1) - AnimationTitan.machoire(temps - 1, veille, 1)) < 0.05, "mâchoire " + temps);
			}
		}
	}

	@Test
	void endormiLesPattesNeBougentPas() {
		AnglesPatte a = AnimationTitan.patte(Patte.ARRIERE_DROITE, 10, 0, 1);
		AnglesPatte b = AnimationTitan.patte(Patte.ARRIERE_DROITE, 130, 0, 1);
		assertEquals(a, b);
		assertFalse(AnimationTitan.yeuxOuverts(500, 0));
		assertEquals(0, AnimationTitan.machoire(10, 0, 1));
	}

	@Test
	void aLArretIlSeTientDeboutEtBaisseLaTete() {
		// Arrêté mais éveillé : les pattes ne bougent plus, et la tête descend boire.
		AnglesPatte a = AnimationTitan.patte(Patte.AVANT_DROITE, 10, 1, 0);
		AnglesPatte b = AnimationTitan.patte(Patte.AVANT_DROITE, 170, 1, 0);
		assertEquals(a, b);
		assertTrue(AnimationTitan.tangageTete(500, 1, 0) < AnimationTitan.tangageTete(500, 1, 1) - 0.3);
	}

	@Test
	void ilRugitLaGueuleOuverte() {
		assertTrue(AnimationTitan.debutRugissement(AnimationTitan.PERIODE_RUGISSEMENT));
		assertEquals(1, AnimationTitan.rugissement(AnimationTitan.PERIODE_RUGISSEMENT + 40), 1e-9);
		assertTrue(AnimationTitan.machoire(AnimationTitan.PERIODE_RUGISSEMENT + 40, 1, 1) > 0.5);
		assertEquals(0, AnimationTitan.rugissement(AnimationTitan.PERIODE_RUGISSEMENT / 2.0));
	}

	@Test
	void leModeleResteRaisonnable() {
		// Chaque tuile est une entité : on en garde assez peu pour ne pas faire ramer le jeu.
		int total = 0;
		for (Piece piece : Piece.values()) {
			int tuiles = ModeleTitan.boites(piece).stream().mapToInt(b -> b.tuiles(ModeleTitan.TUILE).size()).sum();
			total += piece == Piece.TETE || piece == Piece.MACHOIRE ? tuiles : 4 * tuiles;
		}
		assertTrue(total < 450, "trop de blocs animés : " + total);
	}

	@Test
	void lesTuilesCouvrentLaSurface() {
		Boite cube = new Boite(0, 0, 0, 30, 30, 30, Boite.Bloc.PEAU);
		assertEquals(26, cube.tuiles(10).size(), "3 x 3 x 3 tuiles moins celle du milieu");
		assertEquals(1, new Boite(0, 0, 0, 4, 4, 4, Boite.Bloc.OS).tuiles(10).size());
	}
}
