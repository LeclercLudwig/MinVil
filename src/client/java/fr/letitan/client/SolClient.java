package fr.letitan.client;

import fr.letitan.sol.CarteSol;
import fr.letitan.sol.SolPayload;
import fr.letitan.voyage.Repere;
import it.unimi.dsi.fastutil.longs.Long2LongOpenHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.BiomeTags;
import net.minecraft.world.level.biome.Biome;

/**
 * La carte du monde d'en bas reçue du serveur : pour chaque case, sa hauteur et sa couleur
 * vue de haut (herbe, forêt, sable, neige, eau...), calculée ici avec les couleurs des biomes.
 */
final class SolClient {
	/** Valeur d'une case inconnue. */
	static final long INCONNU = Long.MIN_VALUE;

	private static final Long2LongOpenHashMap FIN = carte(), LARGE = carte();

	private SolClient() {
	}

	private static Long2LongOpenHashMap carte() {
		Long2LongOpenHashMap carte = new Long2LongOpenHashMap();
		carte.defaultReturnValue(INCONNU);
		return carte;
	}

	static void recevoir(SolPayload message) {
		var niveau = Minecraft.getInstance().level;
		if (niveau == null) {
			return;
		}
		Registry<Biome> registre = niveau.registryAccess().lookupOrThrow(Registries.BIOME);
		@SuppressWarnings("unchecked")
		Holder<Biome>[] biomes = new Holder[message.biomes().size()];
		for (int i = 0; i < biomes.length; i++) {
			biomes[i] = registre.get(ResourceKey.create(Registries.BIOME, Identifier.parse(message.biomes().get(i))))
					.map(h -> (Holder<Biome>) h).orElse(null);
		}
		Long2LongOpenHashMap carte = message.pas() == CarteSol.PAS_FIN ? FIN : LARGE;
		for (int i = 0; i < message.taille(); i++) {
			int x = message.cx()[i] * message.pas(), z = message.cz()[i] * message.pas();
			Holder<Biome> biome = biomes[message.biome()[i]];
			String nom = message.biomes().get(message.biome()[i]);
			int couleur = couleur(biome, nom, message.eau()[i], message.hauteur()[i], x, z);
			carte.put(CarteSol.cle(message.cx()[i], message.cz()[i]),
					((long) message.hauteur()[i] << 32) | (couleur & 0xFFFFFFFFL));
		}
	}

	static long point(int pas, int cx, int cz) {
		return (pas == CarteSol.PAS_FIN ? FIN : LARGE).get(CarteSol.cle(cx, cz));
	}

	static int hauteur(long point) {
		return (int) (point >> 32);
	}

	/** Couleur RVB ; le bit 24 dit que c'est de l'eau. */
	static int couleur(long point) {
		return (int) point;
	}

	static boolean vide() {
		return FIN.isEmpty() && LARGE.isEmpty();
	}

	static void oublier() {
		FIN.clear();
		LARGE.clear();
	}

	/** Oublie les cases trop loin du Titan. */
	static void oublierLoin(Repere repere) {
		oublierLoin(FIN, CarteSol.PAS_FIN, CarteSol.RAYON_FIN, repere);
		oublierLoin(LARGE, CarteSol.PAS_LARGE, CarteSol.RAYON_LARGE, repere);
	}

	private static void oublierLoin(Long2LongOpenHashMap carte, int pas, int rayon, Repere repere) {
		double limite = rayon * 1.3 + pas;
		carte.keySet().removeIf((long cle) -> {
			int cx = (int) (cle >> 32), cz = (int) cle;
			return Math.max(Math.abs(cx * pas - repere.x), Math.abs(cz * pas - repere.z)) > limite;
		});
	}

	// --- Couleurs vues de haut -----------------------------------------------

	private static final int SABLE = 0xD9CC94, ARGILE = 0xB86A3C, NEIGE = 0xE9EEF2, ROCHE = 0x8A8A8A, MYCELIUM = 0x8B7D86;

	static int couleur(Holder<Biome> biome, String nom, boolean eau, int hauteur, int x, int z) {
		if (biome == null) {
			return eau ? 0x1000000 | 0x3060A0 : 0x6A9A40;
		}
		Biome b = biome.value();
		if (eau) {
			int base = b.getWaterColor() & 0xFFFFFF;
			return 0x1000000 | Dessin.melange(Dessin.fois(base, 0.8f), 0x1E3A6E, 0.35f);
		}
		String chemin = nom.substring(nom.indexOf(':') + 1);
		int couleur;
		if (chemin.contains("snowy") || chemin.contains("frozen") || chemin.contains("ice") || chemin.equals("grove")
				|| chemin.equals("jagged_peaks")) {
			couleur = NEIGE;
		} else if (chemin.contains("desert") || (chemin.contains("beach") && !chemin.contains("stony"))) {
			couleur = SABLE;
		} else if (biome.is(BiomeTags.IS_BADLANDS)) {
			couleur = ARGILE;
		} else if (chemin.contains("stony") || chemin.contains("peaks")) {
			couleur = ROCHE;
		} else if (chemin.contains("mushroom")) {
			couleur = MYCELIUM;
		} else if (biome.is(BiomeTags.IS_FOREST) || biome.is(BiomeTags.IS_TAIGA) || biome.is(BiomeTags.IS_JUNGLE)
				|| chemin.contains("swamp")) {
			// Vue de haut, une forêt, c'est la cime des arbres : plus sombre que l'herbe.
			couleur = Dessin.fois(b.getFoliageColor() & 0xFFFFFF, 0.72f);
		} else {
			couleur = Dessin.fois(b.getGrassColor(x, z) & 0xFFFFFF, 0.88f);
		}
		// Les sommets : la roche, puis la neige.
		if (hauteur > 190) {
			couleur = Dessin.melange(couleur, NEIGE, Math.min(1, (hauteur - 190) / 25f));
		} else if (hauteur > 140 && couleur != NEIGE) {
			couleur = Dessin.melange(couleur, ROCHE, Math.min(0.8f, (hauteur - 140) / 50f));
		}
		return couleur;
	}
}
