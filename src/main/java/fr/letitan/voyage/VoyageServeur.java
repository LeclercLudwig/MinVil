package fr.letitan.voyage;

import fr.letitan.LeTitan;
import fr.letitan.Rythme;
import fr.letitan.sol.CarteSol;
import fr.letitan.vie.AnimationTitan.Patte;
import fr.letitan.vie.Squelette;
import org.joml.Vector3f;
import fr.letitan.monde.FormeTitan;
import fr.letitan.voyage.Navigation.Arret;
import fr.letitan.voyage.Navigation.Genre;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.QuartPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BiomeTags;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LevelChunk;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Le voyage du Titan, côté serveur : il avance sur la carte du monde d'en bas, son dos prend
 * le biome de la région qu'il traverse, et les joueurs sont tenus au courant (messages,
 * boussole, état envoyé au client pour la caméra et les nuages).
 */
public final class VoyageServeur {
	/** Hauteur à laquelle on regarde le biome du monde d'en bas (la surface, pas les grottes). */
	private static final int Y_SURFACE = 64;
	/** Chunks occupés par le corps du Titan, dont on change le biome. */
	private static final int CX_MIN = -8, CX_MAX = 7;
	private static final int CZ_MIN = Math.floorDiv(FormeTitan.AVANT - 5, 16), CZ_MAX = Math.floorDiv(FormeTitan.ARRIERE + 5, 16);

	/** Dernier état « en marche » envoyé aux clients, pour ne prévenir qu'aux changements. */
	private static Boolean marcheEnvoyee;
	/** Vitesse maximale à laquelle le Titan monte ou descend avec le relief (blocs par tick). */
	private static final double MONTEE = 0.06;
	/** Dans le monde d'en bas, on est prévenu de ce que fait le Titan jusqu'à cette distance. */
	private static final double PORTEE_NOUVELLES = 1500;
	/** On prévient qu'il va repartir ce nombre de ticks avant (30 secondes). */
	private static final int PREAVIS = 600;
	/** Dernier état « éveillé » connu, pour annoncer le réveil et l'endormissement. */
	private static Boolean eveilleAvant;
	/** Faux tant que l'altitude n'a pas été calculée depuis le démarrage du serveur. */
	private static boolean altitudePrete;

	private VoyageServeur() {
	}

	public static EtatVoyage etat(MinecraftServer serveur) {
		return serveur.getDataStorage().computeIfAbsent(EtatVoyage.TYPE);
	}

	/** Le Titan avance-t-il en ce moment (éveillé et pas arrêté) ? */
	public static boolean enMarche(ServerLevel dos) {
		return Rythme.eveille(dos) && etat(dos.getServer()).enMarche();
	}

	public static void tick(ServerLevel dos) {
		MinecraftServer serveur = dos.getServer();
		ServerLevel sol = serveur.overworld();
		EtatVoyage etat = etat(serveur);
		long temps = dos.getGameTime();
		if (!etat.demarre) {
			// Le voyage commence au-dessus du point d'apparition du monde d'en bas.
			BlockPos depart = sol.getRespawnData().pos();
			etat.x = depart.getX();
			etat.z = depart.getZ();
			etat.demarre = true;
		}
		Navigation.Terrain terrain = (x, z) -> genre(sol.getBiome(BlockPos.containing(x, Y_SURFACE, z)));
		boolean change = Navigation.tick(etat, terrain, temps, Rythme.eveille(dos));
		etat.setDirty();
		if (change) {
			annoncerArret(dos, etat);
		}
		if (etat.arret != Arret.AUCUN && etat.arretRestant == PREAVIS && Rythme.eveille(dos)) {
			prevenir(dos, Component.literal("Le Titan s'ébroue : il repartira dans 30 secondes ! Touche une de ses pattes pour remonter.")
					.withStyle(ChatFormatting.RED));
		}
		boolean eveille = Rythme.eveille(dos);
		if (eveilleAvant != null && eveille != eveilleAvant) {
			prevenir(dos, Component.literal(eveille
					? "Le jour se lève : le Titan s'éveille et va reprendre sa route."
					: "La nuit tombe : le Titan s'endort. Ses pattes restent plantées dans le sol jusqu'au matin.")
					.withStyle(ChatFormatting.GOLD));
		}
		eveilleAvant = eveille;
		suivreLeRelief(sol, etat, temps);
		CarteSol.de(serveur).tick(dos, etat.repere(), temps);
		boolean marche = enMarche(dos);
		if (marcheEnvoyee == null || marche != marcheEnvoyee || temps % 20 == 0) {
			marcheEnvoyee = marche;
			dos.players().forEach(joueur -> envoyer(joueur, etat, marche, temps));
			sol.players().forEach(joueur -> envoyer(joueur, etat, marche, temps));
		}
		if (temps % 100 == 0) {
			suivreLeBiome(dos, sol, etat);
		}
		if (etat.arret != Arret.AUCUN && Rythme.eveille(dos) && temps % 46 == 0) {
			for (ServerPlayer joueur : dos.players()) {
				// On l'entend boire ou mâcher, au loin, vers la tête.
				var son = etat.arret == Arret.BOIRE ? SoundEvents.GENERIC_DRINK : SoundEvents.GENERIC_EAT;
				dos.playSound(null, joueur.getX(), joueur.getY(), joueur.getZ() - 40, son.value(),
						SoundSource.AMBIENT, 1.5f, 0.3f);
			}
		}
		if (temps % 10 == 0) {
			for (ServerPlayer joueur : dos.players()) {
				if (tientUneBoussole(joueur)) {
					joueur.sendOverlayMessage(boussole(dos, sol, etat));
				}
			}
			for (ServerPlayer joueur : sol.players()) {
				if (tientUneBoussole(joueur)) {
					joueur.sendOverlayMessage(boussoleAuSol(dos, joueur, etat));
				}
			}
		}
	}

	/**
	 * Prévient un joueur qui arrive dans un monde (le dos du Titan ou le monde d'en bas) de l'état
	 * du Titan ; sur le dos, il reçoit aussi la carte du sol qu'il voit en dessous.
	 */
	public static void accueillir(ServerPlayer joueur) {
		MinecraftServer serveur = joueur.level().getServer();
		ServerLevel dos = serveur.getLevel(LeTitan.DOS);
		if (dos == null) {
			return;
		}
		envoyer(joueur, etat(serveur), enMarche(dos), dos.getGameTime());
		if (joueur.level() == dos) {
			CarteSol.de(serveur).envoyerTout(joueur);
		}
	}

	private static void envoyer(ServerPlayer joueur, EtatVoyage etat, boolean marche, long temps) {
		ServerPlayNetworking.send(joueur, EtatTitanPayload.de(etat, marche, temps));
	}

	/** Le Titan monte et descend doucement avec le relief, sous ses quatre pattes et son ventre. */
	private static void suivreLeRelief(ServerLevel sol, EtatVoyage etat, long temps) {
		if (altitudePrete && temps % 20 != 0) {
			approcherAltitude(etat);
			return;
		}
		CarteSol carte = CarteSol.de(sol.getServer());
		Repere repere = etat.repere();
		double somme = carte.hauteur(repere.x, repere.z);
		for (Patte patte : Patte.values()) {
			Vector3f hanche = Squelette.ancrePatte(patte);
			somme += carte.hauteur(repere.versSolX(hanche.x, hanche.z), repere.versSolZ(hanche.x, hanche.z));
		}
		cibleAltitude = somme / (Patte.values().length + 1);
		if (!altitudePrete) {
			etat.altitude = cibleAltitude;
			altitudePrete = true;
		}
		approcherAltitude(etat);
	}

	private static double cibleAltitude = 64;

	private static void approcherAltitude(EtatVoyage etat) {
		etat.altitude += Math.max(-MONTEE, Math.min(MONTEE, cibleAltitude - etat.altitude));
	}

	/** Au démarrage du serveur : l'altitude sera recalculée. */
	public static void reinitialiser() {
		altitudePrete = false;
		marcheEnvoyee = null;
		eveilleAvant = null;
	}

	// --- Terrain d'en bas ----------------------------------------------------

	static Genre genre(Holder<Biome> biome) {
		if (biome.is(BiomeTags.IS_OCEAN) || biome.is(BiomeTags.IS_DEEP_OCEAN)) {
			return Genre.OCEAN;
		}
		if (biome.is(BiomeTags.IS_RIVER)) {
			return Genre.RIVIERE;
		}
		if (biome.is(BiomeTags.IS_BEACH)) {
			return Genre.PLAGE;
		}
		if (biome.is(BiomeTags.IS_FOREST) || biome.is(BiomeTags.IS_TAIGA) || biome.is(BiomeTags.IS_JUNGLE)) {
			return Genre.FORET;
		}
		return Genre.AUTRE;
	}

	private static String nom(Holder<Biome> biome) {
		return biome.unwrapKey().map(cle -> cle.identifier().toString()).orElse("minecraft:forest");
	}

	private static Component nomAffiche(String biome) {
		Identifier id = Identifier.parse(biome);
		return Component.translatable("biome." + id.getNamespace() + "." + id.getPath());
	}

	// --- Le dos prend le climat de la région ---------------------------------

	/** Si le Titan est entré dans un nouveau biome (sur la terre ferme), son dos le prend. */
	private static void suivreLeBiome(ServerLevel dos, ServerLevel sol, EtatVoyage etat) {
		Holder<Biome> sous = sol.getBiome(BlockPos.containing(etat.x, Y_SURFACE, etat.z));
		Genre genre = genre(sous);
		// Sur l'eau ou la plage, le dos garde le climat de la dernière terre.
		if (genre == Genre.OCEAN || genre == Genre.RIVIERE || genre == Genre.PLAGE) {
			return;
		}
		String nom = nom(sous);
		if (nom.equals(etat.biomeDos)) {
			return;
		}
		etat.biomeDos = nom;
		etat.setDirty();
		List<ChunkAccess> changes = new ArrayList<>();
		for (int cx = CX_MIN; cx <= CX_MAX; cx++) {
			for (int cz = CZ_MIN; cz <= CZ_MAX; cz++) {
				LevelChunk chunk = dos.getChunkSource().getChunkNow(cx, cz);
				if (chunk != null && remplir(chunk, sous)) {
					changes.add(chunk);
				}
			}
		}
		dos.getChunkSource().chunkMap.resendBiomesForChunks(changes);
		Component message = Component.literal("Le Titan entre dans une nouvelle région : ")
				.append(nomAffiche(nom)).withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC);
		dos.players().forEach(joueur -> joueur.sendSystemMessage(message));
	}

	/** Un chunk du dos qui vient de charger prend le biome actuel du Titan. */
	public static void chunkCharge(ServerLevel dos, LevelChunk chunk) {
		biomeDos(dos).ifPresent(biome -> remplir(chunk, biome));
	}

	private static Optional<? extends Holder<Biome>> biomeDos(ServerLevel dos) {
		String nom = etat(dos.getServer()).biomeDos;
		return dos.registryAccess().lookupOrThrow(Registries.BIOME)
				.get(ResourceKey.create(Registries.BIOME, Identifier.parse(nom)));
	}

	/** Donne ce biome à tout le chunk. Renvoie faux s'il l'avait déjà. */
	private static boolean remplir(ChunkAccess chunk, Holder<Biome> biome) {
		int qx = QuartPos.fromBlock(chunk.getPos().getMinBlockX());
		int qz = QuartPos.fromBlock(chunk.getPos().getMinBlockZ());
		if (chunk.getNoiseBiome(qx, QuartPos.fromBlock(Y_SURFACE), qz).is(biome)) {
			return false;
		}
		chunk.fillBiomesFromNoise((x, y, z) -> biome);
		chunk.markUnsaved();
		return true;
	}

	// --- Messages ------------------------------------------------------------

	private static void annoncerArret(ServerLevel dos, EtatVoyage etat) {
		String texte = switch (etat.arret) {
			case BOIRE -> "Le Titan s'arrête au bord d'une rivière pour boire, pendant 2 minutes. Saute de son flanc pour descendre ; touche une de ses pattes pour remonter.";
			case BROUTER -> "Le Titan s'arrête pour brouter la cime des arbres, pendant 2 minutes. Saute de son flanc pour descendre ; touche une de ses pattes pour remonter.";
			case AUCUN -> "Le Titan se remet en marche.";
		};
		prevenir(dos, Component.literal(texte).withStyle(ChatFormatting.GOLD));
	}

	private static boolean tientUneBoussole(ServerPlayer joueur) {
		return joueur.getMainHandItem().is(Items.COMPASS) || joueur.getOffhandItem().is(Items.COMPASS);
	}

	/** Prévient les joueurs sur le Titan, et ceux qui sont au sol pas trop loin de lui. */
	private static void prevenir(ServerLevel dos, Component message) {
		dos.players().forEach(joueur -> joueur.sendSystemMessage(message));
		EtatVoyage etat = etat(dos.getServer());
		for (ServerPlayer joueur : dos.getServer().overworld().players()) {
			if (Math.hypot(joueur.getX() - etat.x, joueur.getZ() - etat.z) < PORTEE_NOUVELLES) {
				joueur.sendSystemMessage(message);
			}
		}
	}

	/** Au sol, la boussole montre le Titan : où il est, et ce qu'il fait. */
	private static Component boussoleAuSol(ServerLevel dos, ServerPlayer joueur, EtatVoyage etat) {
		double dx = etat.x - joueur.getX(), dz = etat.z - joueur.getZ();
		long distance = Math.round(Math.hypot(dx, dz));
		String ou = distance < 80 ? "Le Titan est au-dessus de toi"
				: "Le Titan est à " + distance + " blocs vers " + Navigation.direction(Math.atan2(dx, -dz));
		return Component.literal(ou + " · " + activite(dos, etat)).withStyle(ChatFormatting.YELLOW);
	}

	private static String activite(ServerLevel dos, EtatVoyage etat) {
		if (!Rythme.eveille(dos)) {
			return "il dort jusqu'au matin";
		} else if (etat.arret == Arret.BOIRE) {
			return "il boit (encore " + duree(etat.arretRestant) + ")";
		} else if (etat.arret == Arret.BROUTER) {
			return "il broute (encore " + duree(etat.arretRestant) + ")";
		}
		return "il marche vers " + Navigation.direction(etat.cap);
	}

	private static Component boussole(ServerLevel dos, ServerLevel sol, EtatVoyage etat) {
		String sous = nom(sol.getBiome(BlockPos.containing(etat.x, Y_SURFACE, etat.z)));
		String activite;
		if (!Rythme.eveille(dos)) {
			activite = "Le Titan dort";
		} else if (etat.arret == Arret.BOIRE) {
			activite = "Le Titan boit (encore " + duree(etat.arretRestant) + ")";
		} else if (etat.arret == Arret.BROUTER) {
			activite = "Le Titan broute (encore " + duree(etat.arretRestant) + ")";
		} else {
			activite = "Le Titan marche vers " + Navigation.direction(etat.cap);
		}
		return Component.literal(activite + " · sous lui : ").append(nomAffiche(sous))
				.append(" · " + Math.round(etat.odometre) + " blocs parcourus")
				.withStyle(ChatFormatting.YELLOW);
	}

	private static String duree(int ticks) {
		int secondes = ticks / 20;
		return secondes / 60 + " min " + String.format("%02d", secondes % 60) + " s";
	}
}
