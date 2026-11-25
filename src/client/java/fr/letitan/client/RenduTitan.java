package fr.letitan.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import fr.letitan.monde.Bruit;
import fr.letitan.monde.FormeTitan;
import fr.letitan.vie.AnimationTitan;
import fr.letitan.vie.AnimationTitan.Patte;
import fr.letitan.vie.Boite;
import fr.letitan.vie.ModeleTitan;
import fr.letitan.vie.ModeleTitan.Piece;
import fr.letitan.vie.Squelette;
import fr.letitan.vie.Squelette.Pose;
import fr.letitan.voyage.Repere;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;
import org.joml.Vector3fc;

import java.util.ArrayList;
import java.util.List;

/**
 * Le Titan vu depuis le monde d'en bas : son corps couvert de forêt, sa crête d'os, sa tête
 * et ses pattes animées exactement comme sur son dos, à sa vraie place dans le monde et
 * tourné selon son cap. On le voit marcher au loin, enjamber les collines, dormir la nuit.
 *
 * Le corps est un maillage fixe (calculé une fois, dans le repère du dos) ; la tête et les
 * pattes sont recalculées à chaque image à partir du temps de jeu.
 */
final class RenduTitan {
	/** Au-delà, le Titan n'est plus dessiné. */
	private static final double DISTANCE_MAX = 6000;
	private static final float PORTEE = 3000;
	private static final Vector3fc CIEL_PAR_DEFAUT = new Vector3f(0.62f, 0.74f, 0.9f);
	private static final int PAS = 4;

	private static final int HERBE = 0x5A8A36, FORET = 0x3C6A2A, PEAU = 0x8C6A4F, VENTRE = 0x6E5442, OS = 0xE3DBC2,
			ECAILLE = 0x89674E, OEIL = 0xFFC04A, NARINE = 0x2A2428, GUEULE = 0x7A1E1E;

	/** Le corps fixe : quatre sommets par face, et la couleur éclairée de chaque sommet. */
	private static float[] corps;
	private static int[] couleursCorps;

	private RenduTitan() {
	}

	static void dessiner(LevelRenderContext contexte, ClientLevel niveau) {
		double temps = niveau.getGameTime() + contexte.levelState().worldPartialTicks;
		Repere repere = EtatClient.repere(temps);
		if (repere == null) {
			return;
		}
		Vec3 camera = contexte.levelState().cameraRenderState.pos;
		if (Math.hypot(camera.x - repere.x, camera.z - repere.z) > DISTANCE_MAX) {
			return;
		}
		if (corps == null) {
			construireCorps();
		}
		Vector3fc ciel = contexte.levelState().skyRenderState.skyColor;
		Vector3fc voile = ciel == null ? CIEL_PAR_DEFAUT : ciel;
		float jour = 1 - niveau.getSkyDarken() / 15f;
		double veille = EtatClient.veille(), marche = EtatClient.marcheLissee();

		// Les pièces animées, posées dans le repère du dos.
		List<float[]> faces = new ArrayList<>();
		List<Integer> couleurs = new ArrayList<>();
		Pose tete = Squelette.tete(temps, veille, marche);
		Pose machoire = Squelette.machoire(tete, temps, veille, marche);
		boolean yeux = AnimationTitan.yeuxOuverts(temps, veille);
		Vector3f cou = Squelette.ancreTete();
		boites(Piece.TETE, tete, cou, yeux, faces, couleurs);
		boites(Piece.MACHOIRE, machoire, cou, yeux, faces, couleurs);
		for (Patte patte : Patte.values()) {
			Pose[] segments = Squelette.patte(patte, temps, veille, marche);
			Vector3f hanche = Squelette.ancrePatte(patte);
			boites(Piece.CUISSE, segments[0], hanche, yeux, faces, couleurs);
			boites(Piece.JAMBE, segments[1], hanche, yeux, faces, couleurs);
			boites(Piece.PIED, segments[2], hanche, yeux, faces, couleurs);
		}

		contexte.submitNodeCollector().submitCustomGeometry(contexte.poseStack(), Dessin.FORMES, (pose, sommets) -> {
			for (int f = 0; f < couleursCorps.length / 4; f++) {
				for (int s = 0; s < 4; s++) {
					int k = (f * 4 + s) * 3;
					sommet(pose, sommets, repere, camera, corps[k], corps[k + 1], corps[k + 2],
							couleursCorps[f * 4 + s], jour, voile);
				}
			}
			for (int f = 0; f < faces.size(); f++) {
				float[] face = faces.get(f);
				for (int s = 0; s < 4; s++) {
					sommet(pose, sommets, repere, camera, face[s * 3], face[s * 3 + 1], face[s * 3 + 2],
							couleurs.get(f), jour, voile);
				}
			}
		});
	}

	/** Un sommet du repère du dos, placé dans le monde d'en bas, par rapport à la caméra. */
	private static void sommet(PoseStack.Pose pose, VertexConsumer sommets, Repere repere, Vec3 camera,
			float xd, float yd, float zd, int couleur, float jour, Vector3fc voile) {
		float x = (float) (repere.versSolX(xd, zd) - camera.x);
		float y = (float) (repere.versSolY(yd) - camera.y);
		float z = (float) (repere.versSolZ(xd, zd) - camera.z);
		boolean lumineux = (couleur & 0x1000000) != 0;
		float distance = (float) Math.sqrt(x * x + y * y + z * z);
		int finale = Dessin.couleur(couleur & 0xFFFFFF, 1, lumineux ? 1 : jour, distance, voile, PORTEE);
		sommets.addVertex(pose, x, y, z).setColor(finale);
	}

	// --- Pièces animées ------------------------------------------------------

	private static void boites(Piece piece, Pose pose, Vector3f ancre, boolean yeux, List<float[]> faces, List<Integer> couleurs) {
		for (Boite b : ModeleTitan.boites(piece)) {
			Vector3f[] coins = new Vector3f[8];
			for (int i = 0; i < 8; i++) {
				Vector3f c = new Vector3f((i & 1) == 0 ? b.x0() : b.x1(), (i & 2) == 0 ? b.y0() : b.y1(), (i & 4) == 0 ? b.z0() : b.z1());
				coins[i] = pose.rotation().transform(c).add(pose.pivot()).add(ancre);
			}
			int base = switch (b.bloc()) {
				case PEAU -> PEAU;
				case ECAILLE -> ECAILLE;
				case OS -> OS;
				case OEIL -> yeux ? OEIL : PEAU;
				case NARINE -> NARINE;
				case GUEULE -> GUEULE;
			};
			boolean lumineux = b.bloc() == Boite.Bloc.OEIL && yeux;
			// Les six faces, chacune par ses quatre coins (indices : bit 0 = x, bit 1 = y, bit 2 = z).
			int[][] cotes = {{0, 2, 6, 4}, {1, 5, 7, 3}, {0, 4, 5, 1}, {2, 3, 7, 6}, {0, 1, 3, 2}, {4, 6, 7, 5}};
			for (int[] cote : cotes) {
				float[] face = new float[12];
				for (int s = 0; s < 4; s++) {
					face[s * 3] = coins[cote[s]].x;
					face[s * 3 + 1] = coins[cote[s]].y;
					face[s * 3 + 2] = coins[cote[s]].z;
				}
				Vector3f u = new Vector3f(coins[cote[1]]).sub(coins[cote[0]]);
				Vector3f v = new Vector3f(coins[cote[3]]).sub(coins[cote[0]]);
				Vector3f n = u.cross(v);
				// Les faces sont dans un ordre quelconque : on éclaire selon la normale tournée vers l'extérieur.
				Vector3f centre = new Vector3f(coins[0]).add(coins[7]).mul(0.5f);
				Vector3f milieuFace = new Vector3f(coins[cote[0]]).add(coins[cote[2]]).mul(0.5f);
				if (n.dot(milieuFace.sub(centre)) < 0) {
					n.negate();
				}
				float lumiere = lumineux ? 1 : Dessin.eclairage(n.x, n.y, n.z);
				faces.add(face);
				couleurs.add(Dessin.fois(base, lumiere) | (lumineux ? 0x1000000 : 0));
			}
		}
	}

	// --- Corps fixe ----------------------------------------------------------

	private static void construireCorps() {
		int nx = 2 * 92 / PAS + 1;
		int nz = (FormeTitan.ARRIERE - FormeTitan.AVANT) / PAS + 1;
		float[][] dessusX = new float[nx][nz], dessusY = new float[nx][nz], dessousY = new float[nx][nz];
		int[][] couleurDessus = new int[nx][nz];
		for (int i = 0; i < nx; i++) {
			for (int j = 0; j < nz; j++) {
				int x = -92 + i * PAS, z = FormeTitan.AVANT + j * PAS;
				double w = FormeTitan.demiLargeur(z);
				if (Math.abs(x) < w - 1) {
					dessusX[i][j] = x;
					dessusY[i][j] = FormeTitan.hautDuDos(x, z) + 1;
					dessousY[i][j] = FormeTitan.basDuVentre(x, z);
					boolean dosPlat = Math.abs(x) < w * 0.82;
					double arbres = Bruit.lisse(x / 11.0, z / 11.0, 7) + 0.5 * Bruit.lisse(x / 4.0, z / 4.0, 8);
					couleurDessus[i][j] = !dosPlat ? PEAU : arbres > -0.2 ? FORET : HERBE;
				} else {
					// Hors du corps : le sommet se pose sur le bord, là où le dos rejoint le ventre.
					dessusX[i][j] = (float) (Math.signum(x) * Math.max(0, w - 1));
					dessusY[i][j] = FormeTitan.Y_CENTRE;
					dessousY[i][j] = FormeTitan.Y_CENTRE;
					couleurDessus[i][j] = PEAU;
				}
			}
		}
		List<float[]> faces = new ArrayList<>();
		List<int[]> couleurs = new ArrayList<>();
		for (int i = 0; i < nx - 1; i++) {
			for (int j = 0; j < nz - 1; j++) {
				int z0 = FormeTitan.AVANT + j * PAS, z1 = z0 + PAS;
				int[][] coins = {{i, j}, {i, j + 1}, {i + 1, j + 1}, {i + 1, j}};
				float[] dessus = new float[12], dessous = new float[12];
				int[] cDessus = new int[4], cDessous = new int[4];
				for (int s = 0; s < 4; s++) {
					int a = coins[s][0], b = coins[s][1];
					float z = b == j ? z0 : z1;
					dessus[s * 3] = dessusX[a][b];
					dessus[s * 3 + 1] = dessusY[a][b];
					dessus[s * 3 + 2] = z;
					dessous[s * 3] = dessusX[a][b];
					dessous[s * 3 + 1] = dessousY[a][b];
					dessous[s * 3 + 2] = z;
				}
				float lumiereDessus = eclairageQuad(dessus, true), lumiereDessous = eclairageQuad(dessous, false);
				for (int s = 0; s < 4; s++) {
					cDessus[s] = Dessin.fois(couleurDessus[coins[s][0]][coins[s][1]], lumiereDessus);
					cDessous[s] = Dessin.fois(VENTRE, lumiereDessous);
				}
				faces.add(dessus);
				couleurs.add(cDessus);
				faces.add(dessous);
				couleurs.add(cDessous);
			}
		}
		crete(faces, couleurs);
		corps = new float[faces.size() * 12];
		couleursCorps = new int[faces.size() * 4];
		for (int f = 0; f < faces.size(); f++) {
			System.arraycopy(faces.get(f), 0, corps, f * 12, 12);
			System.arraycopy(couleurs.get(f), 0, couleursCorps, f * 4, 4);
		}
	}

	/** Éclairage d'un carré, selon sa normale tournée vers le haut (dessus) ou le bas (dessous). */
	private static float eclairageQuad(float[] q, boolean dessus) {
		Vector3f u = new Vector3f(q[6] - q[0], q[7] - q[1], q[8] - q[2]);
		Vector3f v = new Vector3f(q[9] - q[3], q[10] - q[4], q[11] - q[5]);
		Vector3f n = u.cross(v);
		if ((n.y < 0) == dessus) {
			n.negate();
		}
		return Dessin.eclairage(n.x, n.y, n.z);
	}

	/** Les plaques d'os le long de l'échine, en tranches d'un bloc. */
	private static void crete(List<float[]> faces, List<int[]> couleurs) {
		for (int z = FormeTitan.AVANT; z < FormeTitan.ARRIERE; z++) {
			int base = FormeTitan.hautDuDos(0, z);
			if (base == Integer.MIN_VALUE) {
				continue;
			}
			int haut = FormeTitan.plusHautBloc(0, z, base + 40);
			if (haut <= base) {
				continue;
			}
			float x0 = -1.5f, x1 = 1.5f, y0 = base, y1 = haut + 1, z0 = z, z1 = z + 1;
			float[][] cotes = {
					{x0, y0, z0, x0, y1, z0, x0, y1, z1, x0, y0, z1},
					{x1, y0, z0, x1, y1, z0, x1, y1, z1, x1, y0, z1},
					{x0, y1, z0, x1, y1, z0, x1, y1, z1, x0, y1, z1},
					{x0, y0, z0, x1, y0, z0, x1, y1, z0, x0, y1, z0},
					{x0, y0, z1, x1, y0, z1, x1, y1, z1, x0, y1, z1}};
			float[] lumieres = {0.75f, 0.85f, 1f, 0.7f, 0.8f};
			for (int k = 0; k < cotes.length; k++) {
				faces.add(cotes[k]);
				int c = Dessin.fois(OS, lumieres[k]);
				couleurs.add(new int[] {c, c, c, c});
			}
		}
	}
}
