package fr.letitan.silence;

import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;

/**
 * Le Silence, côté serveur : la forme des terres mortes du monde en cours, et le tick du monde
 * d'en bas (pourriture des nouvelles terres, ombre du Titan, Muets, Pierres-Échos).
 */
public final class Silence {
	private static MinecraftServer serveurActuel;
	private static ZonesMortes zones;

	private Silence() {
	}

	/** Les terres mortes de ce monde. */
	public static ZonesMortes zones(MinecraftServer serveur) {
		if (zones == null || serveurActuel != serveur) {
			ServerLevel sol = serveur.overworld();
			BlockPos spawn = sol.getRespawnData().pos();
			zones = new ZonesMortes(sol.getSeed(), spawn.getX(), spawn.getZ());
			serveurActuel = serveur;
		}
		return zones;
	}

	/** Les carcasses de Titans dont la forme peut toucher ce point (à {@code marge} blocs près). */
	public static java.util.List<Carcasse> carcasses(MinecraftServer serveur, double x, double z, double marge) {
		ServerLevel sol = serveur.overworld();
		BlockPos spawn = sol.getRespawnData().pos();
		return Carcasse.pres(x, z, marge, sol.getSeed(), spawn.getX(), spawn.getZ());
	}

	/** Le Silence a-t-il pris cet endroit (terre morte, ou sol sous une carcasse) ? */
	public static boolean mort(MinecraftServer serveur, double x, double z) {
		if (zones(serveur).morte(x, z)) {
			return true;
		}
		for (Carcasse c : carcasses(serveur, x, z, 0)) {
			double[] l = c.local(x, z);
			if (Carcasse.sousLaCarcasse(l[0], l[1])) {
				return true;
			}
		}
		return false;
	}

	/** Un morceau du monde d'en bas vient d'être créé : le Silence l'a peut-être déjà pris. */
	public static void nouveauMorceau(net.minecraft.world.level.chunk.LevelChunk chunk) {
		Pourriture.nouveau(chunk);
	}

	/** Appelé à chaque tick du monde d'en bas. */
	public static void tickSol(ServerLevel sol) {
		Pourriture.tick(sol);
		Ombre.tickSol(sol);
		Muets.tick(sol);
		Recit.tickSol(sol);
		Carcasses.tickSol(sol);
	}

	/** À l'arrêt du serveur. */
	public static void oublier() {
		zones = null;
		serveurActuel = null;
		Pourriture.oublier();
		Ombre.oublier();
		Muets.oublier();
		TitanMuet.oublier();
		Carcasses.oublier();
	}
}
