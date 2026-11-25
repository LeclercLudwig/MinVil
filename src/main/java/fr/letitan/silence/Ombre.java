package fr.letitan.silence;

import fr.letitan.voyage.EtatVoyage;
import fr.letitan.voyage.VoyageServeur;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.core.Holder;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * L'ombre du Titan. Près de lui, son battement repousse le Silence ; loin de lui (et plus vite
 * la nuit, ou dans une terre morte), le Silence gagne celui qui s'est éloigné : la vue
 * s'obscurcit, les gestes ralentissent, puis il fait mal. Sur le dos du Titan, il reflue.
 */
public final class Ombre {
	/** Rayon de l'ombre du Titan, autour de son corps, dans le monde d'en bas. */
	public static final double RAYON = 260;

	private static final Map<UUID, Double> NIVEAUX = new HashMap<>();

	private Ombre() {
	}

	static void oublier() {
		NIVEAUX.clear();
	}

	/** Le niveau de Silence d'un joueur, de 0 à 100. */
	public static double niveau(ServerPlayer joueur) {
		return NIVEAUX.getOrDefault(joueur.getUUID(), 0.0);
	}

	/** Le Silence gagne d'un coup (un Muet t'a touché). */
	static void ajouter(ServerPlayer joueur, double quantite) {
		NIVEAUX.merge(joueur.getUUID(), quantite, (a, b) -> Math.min(100, a + b));
	}

	/** On renaît sans le Silence. */
	public static void effacer(ServerPlayer joueur) {
		NIVEAUX.remove(joueur.getUUID());
	}

	static void tickSol(ServerLevel sol) {
		long temps = sol.getGameTime();
		if (temps % 20 != 0) {
			return;
		}
		EtatVoyage titan = VoyageServeur.etat(sol.getServer());
		ZonesMortes zones = Silence.zones(sol.getServer());
		boolean nuit = !sol.isBrightOutside();
		for (ServerPlayer joueur : sol.players()) {
			if (joueur.isCreative() || joueur.isSpectator()) {
				continue;
			}
			double distance = Math.hypot(joueur.getX() - titan.x, joueur.getZ() - titan.z);
			boolean ombre = distance < RAYON;
			boolean morte = zones.morte(joueur.getX(), joueur.getZ());
			double variation;
			if (morte) {
				variation = ombre ? 1.5 : 3;
			} else if (ombre) {
				variation = -2.5;
			} else {
				variation = nuit ? 1.5 : 0.6;
			}
			double niveau = Math.max(0, Math.min(100, niveau(joueur) + variation));
			NIVEAUX.put(joueur.getUUID(), niveau);
			effets(sol, joueur, niveau, temps);
			if (niveau >= 25 && temps % 40 == 0) {
				String ou = morte ? "Tu es dans une terre morte" : "Tu es loin de l'ombre du Titan";
				String conseil = ombre ? "sors de la terre morte" : "reviens vers le Titan (" + Math.round(distance - RAYON) + " blocs)";
				joueur.sendOverlayMessage(Component.literal(ou + " : le Silence te gagne (" + Math.round(niveau) + " %), " + conseil + ".")
						.withStyle(niveau >= 80 ? ChatFormatting.DARK_RED : ChatFormatting.GRAY));
			}
		}
	}

	/** Sur le dos du Titan, son cœur chasse le Silence. */
	public static void tickDos(ServerLevel dos) {
		if (dos.getGameTime() % 20 != 0) {
			return;
		}
		for (ServerPlayer joueur : dos.players()) {
			NIVEAUX.computeIfPresent(joueur.getUUID(), (id, n) -> n <= 5 ? null : n - 5);
		}
	}

	private static void effets(ServerLevel sol, ServerPlayer joueur, double niveau, long temps) {
		if (niveau >= 40) {
			appliquer(joueur, MobEffects.DARKNESS, 0);
		}
		if (niveau >= 60) {
			appliquer(joueur, MobEffects.SLOWNESS, niveau >= 80 ? 1 : 0);
			appliquer(joueur, MobEffects.MINING_FATIGUE, 0);
		}
		if (niveau >= 80) {
			appliquer(joueur, MobEffects.WEAKNESS, 0);
		}
		if (niveau >= 100 && temps % 40 == 0) {
			joueur.hurtServer(sol, sol.damageSources().magic(), 2);
		}
	}

	private static void appliquer(ServerPlayer joueur, Holder<MobEffect> effet, int force) {
		joueur.addEffect(new MobEffectInstance(effet, 60, force, true, false));
	}
}
