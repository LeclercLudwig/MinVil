package fr.letitan.silence;

import fr.letitan.LeTitan;
import fr.letitan.Rythme;
import fr.letitan.silence.EtatMuet.Phase;
import fr.letitan.sol.CarteSol;
import fr.letitan.vie.Maladies;
import fr.letitan.voyage.EtatVoyage;
import fr.letitan.voyage.Navigation;
import fr.letitan.voyage.Repere;
import fr.letitan.voyage.VoyageServeur;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.BossEvent;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Le Titan muet : un Titan que le Silence a pris, sans voix, qui erre dans le monde d'en bas.
 * Il entend le cœur du nôtre et vient le chercher. Il se met flanc contre flanc et charge,
 * encore et encore ; les Gardiens crèvent à l'arc les kystes qui luisent sur son flanc. S'ils
 * les crèvent tous, il s'effondre et ses os restent là ; si notre Titan faiblit trop, il le
 * mord une dernière fois et s'en va, pour revenir plus tard.
 */
public final class TitanMuet {
	/** Il apparaît une première fois quand notre Titan a parcouru cette distance. */
	private static final double PREMIER_VOYAGE = 4000;
	/** Il apparaît à cette distance, et approche à cette vitesse (plus vite la nuit, quand le nôtre dort). */
	private static final double APPARITION = 1400, VITESSE = 0.45, VITESSE_NUIT = 0.6;
	/** Il revient tant de ticks après une fuite, et un autre se lève tant de ticks après une victoire. */
	private static final long RETOUR = 24000, SUIVANT = 72000;
	/** Sous cette santé, notre Titan ne peut plus se battre : le Titan muet s'en va. */
	private static final double SANTE_MINIMALE = 12;
	private static final int DUREE_FUITE = 2400, DUREE_CHUTE = 200;

	private static ServerBossEvent barreMuet, barreTitan;

	private TitanMuet() {
	}

	public static EtatMuet etat(MinecraftServer serveur) {
		return serveur.getDataStorage().computeIfAbsent(EtatMuet.TYPE);
	}

	/** Notre Titan est-il en train de se battre (il ne marche plus) ? */
	public static boolean enCombat(MinecraftServer serveur) {
		return etat(serveur).phase == Phase.COMBAT;
	}

	public static void oublier() {
		if (barreMuet != null) {
			barreMuet.removeAllPlayers();
			barreTitan.removeAllPlayers();
		}
		barreMuet = null;
		barreTitan = null;
	}

	/** Le faire venir tout de suite (commande d'essai). */
	public static void appeler(ServerLevel dos) {
		EtatMuet m = etat(dos.getServer());
		if (m.phase == Phase.ABSENT || m.phase == Phase.FUITE) {
			apparaitre(dos, m, 900);
		}
	}

	// --- Tick ------------------------------------------------------------------

	/** Appelé à chaque tick de la dimension du Titan. */
	public static void tick(ServerLevel dos) {
		MinecraftServer serveur = dos.getServer();
		EtatMuet m = etat(serveur);
		EtatVoyage titan = VoyageServeur.etat(serveur);
		long temps = dos.getGameTime();
		switch (m.phase) {
			case ABSENT -> {
				if (titan.odometre >= PREMIER_VOYAGE && temps >= m.prochain && m.prochain >= 0) {
					apparaitre(dos, m, APPARITION);
				}
			}
			case APPROCHE -> approcher(dos, m, titan, temps);
			case COMBAT -> combattre(dos, m, titan, temps);
			case FUITE -> fuir(dos, m, titan, temps);
			case VAINCU -> tomber(dos, m, temps);
		}
		if (m.phase != Phase.ABSENT) {
			m.setDirty();
			if (temps % 20 == 0) {
				suivreLeRelief(dos, m);
			}
		}
		boolean vite = m.phase == Phase.COMBAT || m.phase == Phase.VAINCU;
		if (temps % (vite ? 2 : 20) == 0) {
			envoyer(dos, m, temps);
		}
		if (temps % 10 == 0) {
			barres(dos, m, titan);
		}
	}

	private static void apparaitre(ServerLevel dos, EtatMuet m, double distance) {
		EtatVoyage titan = VoyageServeur.etat(dos.getServer());
		RandomSource hasard = dos.getRandom();
		double angle = titan.cap + (hasard.nextDouble() - 0.5) * Math.PI * 1.2;
		m.x = titan.x + Math.sin(angle) * distance;
		m.z = titan.z - Math.cos(angle) * distance;
		m.cap = angle + Math.PI;
		m.altitude = CarteSol.de(dos.getServer()).hauteur(m.x, m.z);
		m.cote = hasard.nextBoolean() ? 1 : -1;
		if (m.solidite.size() != Affrontement.KYSTES) {
			m.solidite.clear();
			for (int i = 0; i < Affrontement.KYSTES; i++) {
				m.solidite.add(Affrontement.SOLIDITE);
			}
		}
		m.phase = Phase.APPROCHE;
		m.debut = dos.getGameTime();
		m.annonces = 0;
		m.setDirty();
		LeTitan.LOGGER.info("Un Titan muet apparaît en {} {}.", Math.round(m.x), Math.round(m.z));
	}

	/** Il vient se placer contre le flanc de notre Titan. */
	private static void approcher(ServerLevel dos, EtatMuet m, EtatVoyage titan, long temps) {
		Repere notre = titan.repere();
		double[] p = Affrontement.position(m.cote);
		double cx = notre.versSolX(p[0], p[1]), cz = notre.versSolZ(p[0], p[1]);
		double dx = cx - m.x, dz = cz - m.z, d = Math.hypot(dx, dz);
		double vitesse = Rythme.eveille(dos) ? VITESSE : VITESSE_NUIT;
		if (d < 40) {
			m.phase = Phase.COMBAT;
			m.debut = temps;
			prevenir(dos, Component.literal("Le Titan muet attaque ! Tire à l'arc sur les kystes qui luisent sur son flanc.")
					.withStyle(ChatFormatting.DARK_AQUA, ChatFormatting.BOLD));
			rugir(dos);
			return;
		}
		m.x += dx / d * Math.min(vitesse, d);
		m.z += dz / d * Math.min(vitesse, d);
		// Il regarde où il va ; près du but, il s'aligne sur notre Titan.
		double vers = Math.atan2(dx, -dz);
		double cible = d < 150 ? notre.cap : vers;
		m.cap = m.cap + Math.max(-0.01, Math.min(0.01, Math.IEEEremainder(cible - m.cap, 2 * Math.PI)));
		double loin = Math.hypot(m.x - titan.x, m.z - titan.z);
		if (loin < 1200 && (m.annonces & 1) == 0) {
			m.annonces |= 1;
			prevenir(dos, Component.literal("À l'horizon, une silhouette immense sort de la brume. Un Titan... mais il ne fait aucun bruit.")
					.withStyle(ChatFormatting.DARK_AQUA, ChatFormatting.ITALIC));
		}
		if (loin < 500 && (m.annonces & 2) == 0) {
			m.annonces |= 2;
			prevenir(dos, Component.literal("Le Titan muet vient droit sur nous ! Prends ton arc : ses kystes luisent sur son flanc.")
					.withStyle(ChatFormatting.RED));
			// Notre Titan l'entend venir : il se réveille en sursaut.
			rugir(dos);
		}
	}

	/** Flanc contre flanc : il charge, encore et encore. */
	private static void combattre(ServerLevel dos, EtatMuet m, EtatVoyage titan, long temps) {
		long depuis = temps - m.debut;
		Repere notre = titan.repere();
		double[] p = Affrontement.position(m.cote);
		double x = p[0] - m.cote * Affrontement.ELAN * Affrontement.charge(depuis);
		m.x = notre.versSolX(x, p[1]);
		m.z = notre.versSolZ(x, p[1]);
		m.cap = notre.cap;
		if (Affrontement.choc(depuis)) {
			choc(dos, m);
		}
		flechesSurLeDos(dos, m, notre);
		flechesAuSol(dos.getServer().overworld(), m);
		if (m.kystesVivants() == 0) {
			m.phase = Phase.VAINCU;
			m.debut = temps;
			prevenir(dos, Component.literal("Le dernier kyste éclate. Le Titan muet vacille... et s'effondre.")
					.withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));
			for (ServerPlayer j : dos.players()) {
				dos.playSound(null, j.getX(), j.getY(), j.getZ(), SoundEvents.ENDER_DRAGON_DEATH, SoundSource.HOSTILE, 1.0f, 0.6f);
			}
		} else if (Maladies.etat(dos.getServer()).sante <= SANTE_MINIMALE) {
			m.phase = Phase.FUITE;
			m.debut = temps;
			prevenir(dos, Component.literal("Notre Titan n'en peut plus. Le Titan muet le mord une dernière fois et s'éloigne dans la brume... Il reviendra.")
					.withStyle(ChatFormatting.DARK_RED));
		}
	}

	/** Le choc d'une charge : tout tremble, notre Titan souffre, des Muets sautent sur son dos. */
	private static void choc(ServerLevel dos, EtatMuet m) {
		RandomSource hasard = dos.getRandom();
		Maladies.blesser(dos.getServer(), 3);
		for (ServerPlayer j : dos.players()) {
			ServerPlayNetworking.send(j, new SecoussePayload(1));
			dos.playSound(null, j.getX(), j.getY(), j.getZ(), SoundEvents.WARDEN_SONIC_BOOM, SoundSource.HOSTILE, 1.5f, 0.5f);
			dos.playSound(null, j.getX(), j.getY(), j.getZ(), SoundEvents.GENERIC_EXPLODE.value(), SoundSource.HOSTILE, 1.5f, 0.4f);
			if (j.onGround() && !j.isCreative() && !j.isSpectator()) {
				// Le dos se soulève : on est projeté.
				Vec3 elan = new Vec3((hasard.nextDouble() - 0.5) * 0.9, 0.55, (hasard.nextDouble() - 0.5) * 0.9);
				j.setDeltaMovement(j.getDeltaMovement().add(elan));
				j.connection.send(new ClientboundSetEntityMotionPacket(j));
			}
		}
		ServerLevel sol = dos.getServer().overworld();
		for (ServerPlayer j : sol.players()) {
			if (Math.hypot(j.getX() - m.x, j.getZ() - m.z) < 400) {
				ServerPlayNetworking.send(j, new SecoussePayload(0.6f));
				sol.playSound(null, j.getX(), j.getY(), j.getZ(), SoundEvents.GENERIC_EXPLODE.value(), SoundSource.HOSTILE, 2f, 0.3f);
			}
		}
		if (hasard.nextFloat() < 0.3f && !dos.players().isEmpty()) {
			ServerPlayer cible = dos.players().get(hasard.nextInt(dos.players().size()));
			for (int i = 0; i < 2; i++) {
				int x = (int) Math.floor(cible.getX() + hasard.nextInt(13) - 6), z = (int) Math.floor(cible.getZ() + hasard.nextInt(13) - 6);
				int y = dos.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
				Muets.creer(dos, x + 0.5, y, z + 0.5, hasard);
			}
			prevenir(dos, Component.literal("Des Muets sautent de son dos sur le nôtre !").withStyle(ChatFormatting.DARK_AQUA));
		}
		if (hasard.nextFloat() < 0.15f) {
			prevenir(dos, Component.literal("La morsure grise du Titan muet a infecté notre Titan.").withStyle(ChatFormatting.DARK_RED));
			Maladies.infecterAuHasard(dos);
		}
	}

	// --- Les flèches -------------------------------------------------------------

	/** Les flèches tirées depuis le dos de notre Titan. */
	private static void flechesSurLeDos(ServerLevel dos, EtatMuet m, Repere notre) {
		AABB zone = new AABB(-400, -64, -400, 400, 320, 400);
		for (AbstractArrow fleche : dos.getEntitiesOfClass(AbstractArrow.class, zone, f -> f.getDeltaMovement().lengthSqr() > 0.04)) {
			double xs = notre.versSolX(fleche.getX(), fleche.getZ()), zs = notre.versSolZ(fleche.getX(), fleche.getZ());
			toucher(dos, m, fleche, xs, notre.versSolY(fleche.getY()), zs, fleche.getX(), fleche.getY(), fleche.getZ());
		}
	}

	/** Les flèches tirées depuis le sol. */
	private static void flechesAuSol(ServerLevel sol, EtatMuet m) {
		AABB zone = new AABB(m.x - 350, -64, m.z - 350, m.x + 350, 400, m.z + 350);
		for (AbstractArrow fleche : sol.getEntitiesOfClass(AbstractArrow.class, zone, f -> f.getDeltaMovement().lengthSqr() > 0.04)) {
			toucher(sol, m, fleche, fleche.getX(), fleche.getY(), fleche.getZ(), fleche.getX(), fleche.getY(), fleche.getZ());
		}
	}

	private static void toucher(ServerLevel niveau, EtatMuet m, AbstractArrow fleche, double xs, double ys, double zs,
			double x, double y, double z) {
		int[] solidite = m.solidites();
		int k = Affrontement.kysteTouche(m.repere(), m.cote, solidite, xs, ys, zs);
		if (k < 0) {
			return;
		}
		m.solidite.set(k, solidite[k] - 1);
		fleche.discard();
		niveau.sendParticles(ParticleTypes.SCULK_SOUL, x, y, z, 12, 0.6, 0.6, 0.6, 0.05);
		niveau.playSound(null, x, y, z, SoundEvents.SCULK_SHRIEKER_SHRIEK, SoundSource.HOSTILE, 3.0f, 1.4f);
		Entity tireur = fleche.getOwner();
		if (m.solidite.get(k) <= 0) {
			int restants = Integer.bitCount(m.kystesVivants());
			String qui = tireur instanceof ServerPlayer j ? j.getName().getString() + " crève" : "Une flèche crève";
			ServerLevel dos = niveau.getServer().getLevel(LeTitan.DOS);
			if (dos != null) {
				prevenir(dos, Component.literal(qui + " un kyste du Titan muet ! " + (restants == 0 ? "" : restants == 1 ? "Plus qu'un." : restants + " restants."))
						.withStyle(ChatFormatting.AQUA));
				for (ServerPlayer j : dos.players()) {
					dos.playSound(null, j.getX(), j.getY(), j.getZ(), SoundEvents.ENDER_DRAGON_HURT, SoundSource.HOSTILE, 2.0f, 0.5f);
				}
			}
		}
	}

	// --- Fuite et chute -------------------------------------------------------

	private static void fuir(ServerLevel dos, EtatMuet m, EtatVoyage titan, long temps) {
		double dx = m.x - titan.x, dz = m.z - titan.z, d = Math.max(1, Math.hypot(dx, dz));
		m.x += dx / d * VITESSE;
		m.z += dz / d * VITESSE;
		m.cap = Navigation.tourner(m.cap, Math.atan2(dx, -dz));
		if (temps - m.debut > DUREE_FUITE) {
			m.phase = Phase.ABSENT;
			m.prochain = temps + RETOUR;
			// Ses kystes cicatrisent un peu en attendant.
			for (int i = 0; i < m.solidite.size(); i++) {
				if (m.solidite.get(i) > 0) {
					m.solidite.set(i, Math.min(Affrontement.SOLIDITE, m.solidite.get(i) + 1));
				}
			}
		}
	}

	/** Effondrement en cours, de 0 à 1. */
	static float effondrement(EtatMuet m, long temps) {
		return m.phase == Phase.VAINCU ? Math.min(1, (temps - m.debut) / (float) DUREE_CHUTE) : 0;
	}

	private static void tomber(ServerLevel dos, EtatMuet m, long temps) {
		if (temps - m.debut < DUREE_CHUTE) {
			if ((temps - m.debut) % 20 == 0) {
				for (ServerPlayer j : dos.players()) {
					ServerPlayNetworking.send(j, new SecoussePayload(0.5f));
				}
			}
			return;
		}
		// Il est tombé : ses os restent là, et notre Titan reprend des forces.
		Carcasses.ajouterLeTitanMuet(dos.getServer(), new Carcasse(m.x, m.z, m.cap, 1.0, Carcasse.MUET));
		Maladies.soigner(dos.getServer(), 25);
		m.phase = Phase.ABSENT;
		m.prochain = temps + SUIVANT;
		m.solidite.clear();
		prevenir(dos, Component.literal("Le Silence le quitte enfin. Ses os resteront là, et dans sa cage brille encore quelque chose. "
				+ "Notre Titan pousse un long cri et reprend des forces.").withStyle(ChatFormatting.GOLD));
		rugir(dos);
	}

	// --- Le relief, les messages, les barres --------------------------------------

	private static void suivreLeRelief(ServerLevel dos, EtatMuet m) {
		double cible = CarteSol.de(dos.getServer()).hauteur(m.x, m.z);
		m.altitude += Math.max(-1.2, Math.min(1.2, cible - m.altitude));
	}

	private static void envoyer(ServerLevel dos, EtatMuet m, long temps) {
		MuetPayload message = new MuetPayload(m.phase.ordinal(), temps, m.x, m.z, m.cap, m.altitude, m.cote,
				m.kystesVivants(), effondrement(m, temps));
		dos.players().forEach(j -> ServerPlayNetworking.send(j, message));
		dos.getServer().overworld().players().forEach(j -> ServerPlayNetworking.send(j, message));
	}

	private static void barres(ServerLevel dos, EtatMuet m, EtatVoyage titan) {
		if (barreMuet == null) {
			barreMuet = new ServerBossEvent(UUID.nameUUIDFromBytes("letitan:muet".getBytes()), Component.literal("Le Titan muet"),
					BossEvent.BossBarColor.BLUE, BossEvent.BossBarOverlay.NOTCHED_20);
			barreTitan = new ServerBossEvent(UUID.nameUUIDFromBytes("letitan:titan".getBytes()), Component.literal("Notre Titan"),
					BossEvent.BossBarColor.GREEN, BossEvent.BossBarOverlay.PROGRESS);
		}
		boolean visible = m.phase == Phase.COMBAT
				|| (m.phase == Phase.APPROCHE && Math.hypot(m.x - titan.x, m.z - titan.z) < 600);
		int restant = 0;
		for (int s : m.solidites()) {
			restant += Math.max(0, s);
		}
		barreMuet.setProgress(restant / (float) (Affrontement.KYSTES * Affrontement.SOLIDITE));
		barreTitan.setProgress((float) Math.max(0, Math.min(1, Maladies.etat(dos.getServer()).sante / 100)));
		List<ServerPlayer> concernes = new ArrayList<>(dos.players());
		for (ServerPlayer j : dos.getServer().overworld().players()) {
			if (Math.hypot(j.getX() - m.x, j.getZ() - m.z) < 600) {
				concernes.add(j);
			}
		}
		for (ServerPlayer j : dos.getServer().getPlayerList().getPlayers()) {
			if (visible && concernes.contains(j)) {
				barreMuet.addPlayer(j);
				barreTitan.addPlayer(j);
			} else {
				barreMuet.removePlayer(j);
				barreTitan.removePlayer(j);
			}
		}
	}

	private static void rugir(ServerLevel dos) {
		for (ServerPlayer j : dos.players()) {
			dos.playSound(null, j.getX(), j.getY(), j.getZ(), SoundEvents.ENDER_DRAGON_GROWL, SoundSource.HOSTILE, 3.0f, 0.4f);
		}
	}

	private static void prevenir(ServerLevel dos, Component message) {
		dos.players().forEach(j -> j.sendSystemMessage(message));
		EtatMuet m = etat(dos.getServer());
		for (ServerPlayer j : dos.getServer().overworld().players()) {
			if (Math.hypot(j.getX() - m.x, j.getZ() - m.z) < 1500) {
				j.sendSystemMessage(message);
			}
		}
	}
}
