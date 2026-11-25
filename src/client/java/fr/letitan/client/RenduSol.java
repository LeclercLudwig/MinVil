package fr.letitan.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import fr.letitan.sol.CarteSol;
import fr.letitan.voyage.Repere;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;
import org.joml.Vector3fc;

/**
 * Le vrai sol du monde d'en bas, dessiné sous le Titan quand on est sur son dos : il défile
 * et tourne au rythme de sa marche, comme si le Titan marchait vraiment dessus.
 *
 * Deux maillages de la carte reçue du serveur : un fin sous le Titan, un large jusqu'à
 * l'horizon, sans recouvrement (le large s'arrête où commence le fin, avec une jupe pour
 * cacher les fentes entre les deux).
 */
final class RenduSol {
	private static final int N_FIN = CarteSol.RAYON_FIN / CarteSol.PAS_FIN;
	private static final int N_LARGE = CarteSol.RAYON_LARGE / CarteSol.PAS_LARGE;
	/** Distance à laquelle l'air voile le sol aux deux tiers environ (le bord de la carte est à 1 536 blocs). */
	private static final float PORTEE = 1000;
	private static final Vector3fc CIEL_PAR_DEFAUT = new Vector3f(0.62f, 0.74f, 0.9f);

	/** Un maillage : sommets à plat dans des tableaux réutilisés d'une image à l'autre. */
	private static final class Maillage {
		final int cote;
		final float[] x, y, z;
		final int[] c;
		final boolean[] connu;
		int i0, j0, pas;

		Maillage(int n) {
			cote = 2 * n + 1;
			x = new float[cote * cote];
			y = new float[cote * cote];
			z = new float[cote * cote];
			c = new int[cote * cote];
			connu = new boolean[cote * cote];
		}
	}

	private static final Maillage FIN = new Maillage(N_FIN), LARGE = new Maillage(N_LARGE);

	private RenduSol() {
	}

	static void dessiner(LevelRenderContext contexte, ClientLevel niveau) {
		if (SolClient.vide()) {
			return;
		}
		double temps = niveau.getGameTime() + contexte.levelState().worldPartialTicks;
		Repere repere = EtatClient.repere(temps);
		if (repere == null) {
			return;
		}
		Vec3 camera = contexte.levelState().cameraRenderState.pos;
		Vector3fc ciel = contexte.levelState().skyRenderState.skyColor;
		if (ciel == null) {
			ciel = CIEL_PAR_DEFAUT;
		}
		float jour = 1 - niveau.getSkyDarken() / 15f;
		// Le maillage fin est aligné sur le large, pour que leurs bords se rejoignent.
		int rapport = CarteSol.PAS_LARGE / CarteSol.PAS_FIN;
		int ciFin = Math.floorDiv((int) Math.floor(repere.x / CarteSol.PAS_FIN), rapport) * rapport;
		int cjFin = Math.floorDiv((int) Math.floor(repere.z / CarteSol.PAS_FIN), rapport) * rapport;
		remplir(FIN, CarteSol.PAS_FIN, ciFin - N_FIN, cjFin - N_FIN, repere, camera, ciel, jour);
		int ciLarge = (int) Math.floor(repere.x / CarteSol.PAS_LARGE), cjLarge = (int) Math.floor(repere.z / CarteSol.PAS_LARGE);
		remplir(LARGE, CarteSol.PAS_LARGE, ciLarge - N_LARGE, cjLarge - N_LARGE, repere, camera, ciel, jour);
		// Partie du monde couverte par le maillage fin, en blocs.
		int finX0 = FIN.i0 * FIN.pas, finX1 = (FIN.i0 + FIN.cote - 1) * FIN.pas;
		int finZ0 = FIN.j0 * FIN.pas, finZ1 = (FIN.j0 + FIN.cote - 1) * FIN.pas;
		contexte.submitNodeCollector().submitCustomGeometry(contexte.poseStack(), Dessin.FORMES, (pose, sommets) -> {
			quadrillage(FIN, pose, sommets, Integer.MAX_VALUE, 0, 0, 0);
			quadrillage(LARGE, pose, sommets, finX0, finX1, finZ0, finZ1);
			jupe(FIN, pose, sommets);
		});
		if (niveau.getGameTime() % 100 == 0) {
			SolClient.oublierLoin(repere);
		}
	}

	/** Calcule chaque sommet : position sur le dos (par rapport à la caméra) et couleur. */
	private static void remplir(Maillage m, int pas, int i0, int j0, Repere repere, Vec3 camera, Vector3fc ciel, float jour) {
		m.i0 = i0;
		m.j0 = j0;
		m.pas = pas;
		for (int a = 0; a < m.cote; a++) {
			for (int b = 0; b < m.cote; b++) {
				int k = a * m.cote + b;
				long point = SolClient.point(pas, i0 + a, j0 + b);
				m.connu[k] = point != SolClient.INCONNU;
				if (!m.connu[k]) {
					continue;
				}
				double xs = (i0 + a) * (double) pas, zs = (j0 + b) * (double) pas;
				double xd = repere.versDosX(xs, zs) - camera.x;
				double zd = repere.versDosZ(xs, zs) - camera.z;
				m.x[k] = (float) xd;
				m.z[k] = (float) zd;
				m.y[k] = (float) (repere.versDosY(SolClient.hauteur(point)) - camera.y);
			}
		}
		for (int a = 0; a < m.cote; a++) {
			for (int b = 0; b < m.cote; b++) {
				int k = a * m.cote + b;
				if (!m.connu[k]) {
					continue;
				}
				long point = SolClient.point(pas, i0 + a, j0 + b);
				int brut = SolClient.couleur(point);
				boolean eau = (brut & 0x1000000) != 0;
				float lumiere = 1;
				if (!eau) {
					// La pente, d'après les voisins (dans le monde d'en bas : le relief ne tourne pas avec la lumière).
					float dx = hauteurVoisin(m, a + 1, b, k) - hauteurVoisin(m, a - 1, b, k);
					float dz = hauteurVoisin(m, a, b + 1, k) - hauteurVoisin(m, a, b - 1, k);
					lumiere = Dessin.eclairage(-dx, 2 * pas, -dz);
				}
				float distance = (float) Math.sqrt(m.x[k] * m.x[k] + m.z[k] * m.z[k]);
				m.c[k] = Dessin.couleur(brut & 0xFFFFFF, lumiere, jour, distance, ciel, PORTEE);
			}
		}
	}

	private static float hauteurVoisin(Maillage m, int a, int b, int defaut) {
		if (a < 0 || b < 0 || a >= m.cote || b >= m.cote || !m.connu[a * m.cote + b]) {
			return m.y[defaut];
		}
		return m.y[a * m.cote + b];
	}

	/** Les carrés du maillage, sauf ceux qui tombent entièrement dans la zone [x0, x1] x [z0, z1] (en blocs). */
	private static void quadrillage(Maillage m, PoseStack.Pose pose, VertexConsumer sommets, int x0, int x1, int z0, int z1) {
		for (int a = 0; a < m.cote - 1; a++) {
			for (int b = 0; b < m.cote - 1; b++) {
				int xs = (m.i0 + a) * m.pas, zs = (m.j0 + b) * m.pas;
				if (xs >= x0 && xs + m.pas <= x1 && zs >= z0 && zs + m.pas <= z1) {
					continue;
				}
				int k00 = a * m.cote + b, k10 = k00 + m.cote, k01 = k00 + 1, k11 = k10 + 1;
				if (!m.connu[k00] || !m.connu[k10] || !m.connu[k01] || !m.connu[k11]) {
					continue;
				}
				sommet(m, k00, pose, sommets);
				sommet(m, k01, pose, sommets);
				sommet(m, k11, pose, sommets);
				sommet(m, k10, pose, sommets);
			}
		}
	}

	private static void sommet(Maillage m, int k, PoseStack.Pose pose, VertexConsumer sommets) {
		sommets.addVertex(pose, m.x[k], m.y[k], m.z[k]).setColor(m.c[k]);
	}

	/** Un rebord qui descend tout autour du maillage fin : il cache les fentes avec le maillage large. */
	private static void jupe(Maillage m, PoseStack.Pose pose, VertexConsumer sommets) {
		int n = m.cote - 1;
		for (int t = 0; t < n; t++) {
			bord(m, t * m.cote, (t + 1) * m.cote, pose, sommets);
			bord(m, t * m.cote + n, (t + 1) * m.cote + n, pose, sommets);
			bord(m, t, t + 1, pose, sommets);
			bord(m, n * m.cote + t, n * m.cote + t + 1, pose, sommets);
		}
	}

	private static void bord(Maillage m, int k1, int k2, PoseStack.Pose pose, VertexConsumer sommets) {
		if (!m.connu[k1] || !m.connu[k2]) {
			return;
		}
		float profondeur = 24;
		sommets.addVertex(pose, m.x[k1], m.y[k1], m.z[k1]).setColor(m.c[k1]);
		sommets.addVertex(pose, m.x[k2], m.y[k2], m.z[k2]).setColor(m.c[k2]);
		sommets.addVertex(pose, m.x[k2], m.y[k2] - profondeur, m.z[k2]).setColor(m.c[k2]);
		sommets.addVertex(pose, m.x[k1], m.y[k1] - profondeur, m.z[k1]).setColor(m.c[k1]);
	}
}
