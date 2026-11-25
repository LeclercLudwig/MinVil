package fr.letitan.sol;

import fr.letitan.LeTitan;
import fr.letitan.voyage.Repere;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.Holder;
import net.minecraft.core.QuartPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.RandomState;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * La carte du monde d'en bas autour du Titan, telle qu'on la voit de son dos : la hauteur
 * du sol et le biome, tous les quelques blocs.
 *
 * Elle est calculée à partir du générateur du monde, sans générer les chunks : c'est rapide,
 * et le relief est exactement celui qu'on trouvera en descendant. Deux grilles : une fine
 * sous le Titan, une large jusqu'à l'horizon. Le calcul se fait en tâche de fond, et chaque
 * nouveau point est envoyé aux joueurs sur le dos du Titan.
 */
public final class CarteSol {
	public static final int PAS_FIN = 8, RAYON_FIN = 384;
	public static final int PAS_LARGE = 32, RAYON_LARGE = 1536;
	/** Points demandés à la fois, au plus, pour ne pas prendre de retard sur le Titan. */
	private static final int EN_COURS_MAX = 3000;
	private static final int PAR_MESSAGE = 4096;

	/** Un point calculé. */
	private record Point(int pas, int cx, int cz, short hauteur, boolean eau, String biome) {
	}

	/** Une grille : les points connus et ceux en cours de calcul. */
	private static final class Grille {
		final int pas, rayon;
		final Map<Long, Point> points = new HashMap<>();
		final Set<Long> demandes = new HashSet<>();

		Grille(int pas, int rayon) {
			this.pas = pas;
			this.rayon = rayon;
		}
	}

	private static CarteSol actuelle;

	private final ServerLevel sol;
	private final ChunkGenerator generateur;
	private final RandomState aleas;
	private final Grille fine = new Grille(PAS_FIN, RAYON_FIN);
	private final Grille large = new Grille(PAS_LARGE, RAYON_LARGE);
	private final ExecutorService ouvriers;
	private final ConcurrentLinkedQueue<Point> prets = new ConcurrentLinkedQueue<>();
	private int enCours;

	private CarteSol(ServerLevel sol) {
		this.sol = sol;
		this.generateur = sol.getChunkSource().getGenerator();
		this.aleas = sol.getChunkSource().randomState();
		this.ouvriers = Executors.newFixedThreadPool(1, tache -> {
			Thread fil = new Thread(tache, "Le Titan - carte du sol");
			fil.setDaemon(true);
			fil.setPriority(Thread.MIN_PRIORITY);
			return fil;
		});
	}

	public static CarteSol de(MinecraftServer serveur) {
		if (actuelle == null || actuelle.sol != serveur.overworld()) {
			arreter();
			actuelle = new CarteSol(serveur.overworld());
		}
		return actuelle;
	}

	/** À l'arrêt du serveur. */
	public static void arreter() {
		if (actuelle != null) {
			actuelle.ouvriers.shutdownNow();
			actuelle = null;
		}
	}

	// --- Hauteur du sol --------------------------------------------------------

	/** Hauteur de la surface (de l'eau s'il y en a) en ce point du monde d'en bas. */
	public int hauteur(double x, double z) {
		int fond = generateur.getBaseHeight((int) Math.floor(x), (int) Math.floor(z), Heightmap.Types.OCEAN_FLOOR_WG, sol, aleas);
		return Math.max(fond, generateur.getSeaLevel());
	}

	private Point calculer(int pas, int cx, int cz) {
		int x = cx * pas, z = cz * pas;
		try {
			int fond = generateur.getBaseHeight(x, z, Heightmap.Types.OCEAN_FLOOR_WG, sol, aleas);
			int mer = generateur.getSeaLevel();
			int haut = Math.max(fond, mer);
			Holder<Biome> biome = sol.getUncachedNoiseBiome(
					QuartPos.fromBlock(x), QuartPos.fromBlock(fond), QuartPos.fromBlock(z));
			String nom = biome.unwrapKey().map(cle -> cle.identifier().toString()).orElse("minecraft:plains");
			return new Point(pas, cx, cz, (short) haut, fond < mer, nom);
		} catch (RuntimeException erreur) {
			LeTitan.LOGGER.debug("Point du sol impossible à calculer en {} {}", x, z, erreur);
			return new Point(pas, cx, cz, (short) generateur.getSeaLevel(), false, "minecraft:plains");
		}
	}

	// --- Suivre le Titan -------------------------------------------------------

	/** Appelé à chaque tick de la dimension du Titan. */
	public void tick(ServerLevel dos, Repere repere, long temps) {
		if (temps % 4 != 0) {
			return;
		}
		List<Point> nouveaux = new ArrayList<>();
		Point point;
		while ((point = prets.poll()) != null) {
			enCours--;
			Grille grille = point.pas() == PAS_FIN ? fine : large;
			long cle = cle(point.cx(), point.cz());
			grille.demandes.remove(cle);
			grille.points.put(cle, point);
			nouveaux.add(point);
		}
		if (!nouveaux.isEmpty()) {
			for (ServerPlayer joueur : dos.players()) {
				envoyer(joueur, nouveaux);
			}
		}
		demander(fine, repere);
		demander(large, repere);
		if (temps % 200 == 0) {
			oublier(fine, repere);
			oublier(large, repere);
		}
	}

	/** Demande les points manquants autour du Titan, du plus proche au plus lointain. */
	private void demander(Grille grille, Repere repere) {
		int ccx = (int) Math.floor(repere.x / grille.pas), ccz = (int) Math.floor(repere.z / grille.pas);
		int n = grille.rayon / grille.pas;
		for (int r = 0; r <= n && enCours < EN_COURS_MAX; r++) {
			for (int i = -r; i <= r && enCours < EN_COURS_MAX; i++) {
				for (int j = -r; j <= r && enCours < EN_COURS_MAX; j++) {
					if (Math.max(Math.abs(i), Math.abs(j)) != r) {
						continue;
					}
					int cx = ccx + i, cz = ccz + j;
					long cle = cle(cx, cz);
					if (grille.points.containsKey(cle) || !grille.demandes.add(cle)) {
						continue;
					}
					enCours++;
					int pas = grille.pas;
					ouvriers.execute(() -> prets.add(calculer(pas, cx, cz)));
				}
			}
		}
	}

	private static void oublier(Grille grille, Repere repere) {
		double limite = grille.rayon * 1.3 + grille.pas;
		grille.points.values().removeIf(p ->
				Math.max(Math.abs(p.cx() * grille.pas - repere.x), Math.abs(p.cz() * grille.pas - repere.z)) > limite);
	}

	/** Envoie toute la carte connue à un joueur qui arrive sur le Titan. */
	public void envoyerTout(ServerPlayer joueur) {
		envoyer(joueur, fine.points.values());
		envoyer(joueur, large.points.values());
	}

	private static void envoyer(ServerPlayer joueur, Collection<Point> points) {
		List<Point> lot = new ArrayList<>(PAR_MESSAGE);
		int pas = -1;
		for (Point p : points) {
			if (lot.size() == PAR_MESSAGE || (pas != -1 && p.pas() != pas)) {
				ServerPlayNetworking.send(joueur, message(lot));
				lot.clear();
			}
			pas = p.pas();
			lot.add(p);
		}
		if (!lot.isEmpty()) {
			ServerPlayNetworking.send(joueur, message(lot));
		}
	}

	private static SolPayload message(List<Point> lot) {
		List<String> biomes = new ArrayList<>();
		Map<String, Integer> indices = new HashMap<>();
		int n = lot.size();
		int[] cx = new int[n], cz = new int[n];
		short[] hauteur = new short[n], biome = new short[n];
		boolean[] eau = new boolean[n];
		for (int i = 0; i < n; i++) {
			Point p = lot.get(i);
			cx[i] = p.cx();
			cz[i] = p.cz();
			hauteur[i] = p.hauteur();
			eau[i] = p.eau();
			biome[i] = (short) (int) indices.computeIfAbsent(p.biome(), nom -> {
				biomes.add(nom);
				return biomes.size() - 1;
			});
		}
		return new SolPayload(lot.get(0).pas(), biomes, cx, cz, hauteur, biome, eau);
	}

	public static long cle(int cx, int cz) {
		return ((long) cx << 32) | (cz & 0xFFFFFFFFL);
	}
}
