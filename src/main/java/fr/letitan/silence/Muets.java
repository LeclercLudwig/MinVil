package fr.letitan.silence;

import fr.letitan.mixin.MobAccesseur;
import fr.letitan.voyage.EtatVoyage;
import fr.letitan.voyage.VoyageServeur;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.levelgen.Heightmap;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Les Muets : ceux que le Silence a pris. Sans voix (ils ne font aucun bruit) et sans visage
 * (une masse de sculk à la place de la tête), ils ne voient rien mais entendent tout. Celui
 * qui court, saute, frappe ou casse un bloc les attire de loin ; celui qui marche, de près ;
 * accroupi, on passe à côté d'eux sans qu'ils s'en doutent.
 *
 * Ils rôdent toujours dans les terres mortes, et la nuit partout, surtout loin du Titan.
 */
public final class Muets {
	public static final String ETIQUETTE = "letitan.muet";
	/** Portée de l'ouïe : un pas de course, un pas de marche, un bruit (coup, bloc cassé). */
	private static final double COURSE = 36, MARCHE = 12, BRUIT = 24, CONTACT = 2.5;
	/** Ils oublient celui qu'ils n'entendent plus depuis ce nombre de ticks. */
	private static final int OUBLI = 80;

	private static final List<Zombie> MUETS = new ArrayList<>();
	/** Dernier tick où ils ont entendu leur proie. */
	private static final Map<UUID, Long> ENTENDU = new HashMap<>();
	/** Dernier bruit fait par chaque joueur (coup, bloc cassé). */
	private static final Map<UUID, Long> BRUITS = new HashMap<>();
	/** Position de chaque joueur à la dernière écoute, pour savoir s'il marche. */
	private static final Map<UUID, double[]> POSITIONS = new HashMap<>();

	private Muets() {
	}

	static void oublier() {
		MUETS.clear();
		ENTENDU.clear();
		BRUITS.clear();
		POSITIONS.clear();
	}

	/** Un Muet d'une partie précédente se recharge : il retrouve son ouïe (et perd sa vue). */
	public static void charge(net.minecraft.world.entity.Entity entite) {
		if (entite instanceof Zombie muet && estUnMuet(muet) && !MUETS.contains(muet)) {
			((MobAccesseur) muet).letitan$selecteurDeCibles().removeAllGoals(but -> true);
			MUETS.add(muet);
		}
	}

	/** Un joueur vient de faire du bruit (il a frappé, cassé un bloc...). */
	public static void bruit(ServerPlayer joueur) {
		BRUITS.put(joueur.getUUID(), joueur.level().getGameTime());
	}

	/** Un Muet a touché un joueur : le Silence le gagne. */
	public static void touche(ServerPlayer joueur) {
		Ombre.ajouter(joueur, 12);
		joueur.addEffect(new MobEffectInstance(MobEffects.DARKNESS, 100, 0));
	}

	public static boolean estUnMuet(net.minecraft.world.entity.Entity entite) {
		return entite != null && entite.entityTags().contains(ETIQUETTE);
	}

	static void tick(ServerLevel sol) {
		long temps = sol.getGameTime();
		if (temps % 80 == 0) {
			faireApparaitre(sol);
		}
		if (temps % 5 == 0) {
			ecouter(sol, temps);
		}
	}

	// --- Apparition ----------------------------------------------------------

	private static void faireApparaitre(ServerLevel sol) {
		MUETS.removeIf(m -> m.isRemoved() || !m.isAlive());
		EtatVoyage titan = VoyageServeur.etat(sol.getServer());
		ZonesMortes zones = Silence.zones(sol.getServer());
		boolean nuit = !sol.isBrightOutside();
		RandomSource hasard = sol.getRandom();
		// Ceux que plus personne n'entend s'évanouissent ; le jour, hors des terres mortes aussi.
		for (Zombie m : List.copyOf(MUETS)) {
			if (m.entityTags().contains(Carcasses.GARDIEN)) {
				continue;
			}
			boolean seul = sol.getNearestPlayer(m, 96) == null;
			if (seul || (!nuit && !zones.morte(m.getX(), m.getZ()) && m.getTarget() == null)) {
				m.discard();
			}
		}
		for (ServerPlayer joueur : sol.players()) {
			if (joueur.isCreative() || joueur.isSpectator()) {
				continue;
			}
			boolean ombre = Math.hypot(joueur.getX() - titan.x, joueur.getZ() - titan.z) < Ombre.RAYON;
			boolean morte = zones.intensite(joueur.getX(), joueur.getZ()) >= ZonesMortes.BORD
					|| Silence.mort(sol.getServer(), joueur.getX(), joueur.getZ());
			int maximum = morte ? 6 : !nuit ? 0 : ombre ? 2 : 5;
			long presents = MUETS.stream().filter(m -> m.distanceToSqr(joueur) < 48 * 48).count();
			if (presents >= maximum) {
				continue;
			}
			double angle = hasard.nextDouble() * Math.PI * 2, distance = 18 + hasard.nextDouble() * 18;
			int x = (int) Math.floor(joueur.getX() + Math.cos(angle) * distance);
			int z = (int) Math.floor(joueur.getZ() + Math.sin(angle) * distance);
			BlockPos pos = new BlockPos(x, 0, z);
			if (!sol.isLoaded(pos)) {
				continue;
			}
			int y = sol.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
			if (!sol.getBlockState(new BlockPos(x, y - 1, z)).getFluidState().isEmpty()) {
				continue;
			}
			creer(sol, x + 0.5, y, z + 0.5, hasard);
		}
	}

	static Zombie creer(ServerLevel sol, double x, double y, double z, RandomSource hasard) {
		Zombie muet = EntityTypes.ZOMBIE.create(sol, EntitySpawnReason.EVENT);
		if (muet == null) {
			return null;
		}
		muet.snapTo(x, y, z, hasard.nextFloat() * 360, 0);
		muet.setBaby(false);
		// Pas de visage : une masse de sculk. Et pas de voix.
		muet.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.SCULK));
		muet.setDropChance(EquipmentSlot.HEAD, 0);
		muet.setSilent(true);
		muet.setCustomName(Component.literal("Muet"));
		muet.setCustomNameVisible(false);
		muet.addTag(ETIQUETTE);
		muet.setPersistenceRequired();
		muet.getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(0.3);
		muet.getAttribute(Attributes.FOLLOW_RANGE).setBaseValue(40);
		muet.getAttribute(Attributes.ATTACK_DAMAGE).setBaseValue(4);
		// Ils ne visent pas à vue : seul le bruit les guide.
		((MobAccesseur) muet).letitan$selecteurDeCibles().removeAllGoals(but -> true);
		sol.addFreshEntity(muet);
		MUETS.add(muet);
		return muet;
	}

	// --- Ouïe ----------------------------------------------------------------

	private static void ecouter(ServerLevel sol, long temps) {
		Map<UUID, Boolean> marchent = new HashMap<>();
		for (ServerPlayer joueur : sol.players()) {
			double[] avant = POSITIONS.put(joueur.getUUID(), new double[] {joueur.getX(), joueur.getZ()});
			marchent.put(joueur.getUUID(), avant != null && Math.hypot(joueur.getX() - avant[0], joueur.getZ() - avant[1]) > 0.2);
		}
		for (Zombie m : MUETS) {
			if (m.isRemoved()) {
				continue;
			}
			ServerPlayer entendu = null;
			for (ServerPlayer joueur : sol.players()) {
				if (!joueur.isCreative() && !joueur.isSpectator()
						&& entend(m, joueur, temps, marchent.getOrDefault(joueur.getUUID(), false))) {
					entendu = joueur;
					break;
				}
			}
			if (entendu != null) {
				m.setTarget(entendu);
				ENTENDU.put(m.getUUID(), temps);
			} else if (m.getTarget() instanceof Player && temps - ENTENDU.getOrDefault(m.getUUID(), 0L) > OUBLI) {
				// Plus un bruit : il perd ta trace.
				m.setTarget(null);
				m.getNavigation().stop();
			}
		}
	}

	/** Ce Muet entend-il ce joueur ? */
	private static boolean entend(Zombie m, ServerPlayer joueur, long temps, boolean bouge) {
		double distance = Math.sqrt(m.distanceToSqr(joueur));
		if (distance < CONTACT) {
			return true;
		}
		if (temps - BRUITS.getOrDefault(joueur.getUUID(), -1000L) < 40 && distance < BRUIT) {
			return true;
		}
		if (joueur.isCrouching()) {
			return false;
		}
		boolean saute = !joueur.onGround() && !joueur.isInWater() && !joueur.onClimbable();
		if (joueur.isSprinting() || saute) {
			return distance < COURSE;
		}
		return bouge && distance < MARCHE;
	}
}
