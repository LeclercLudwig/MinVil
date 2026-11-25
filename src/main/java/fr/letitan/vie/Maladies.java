package fr.letitan.vie;

import fr.letitan.LeTitan;
import fr.letitan.monde.FormeTitan;
import fr.letitan.monde.FormeTitan.Matiere;
import fr.letitan.monde.GenerateurTitan;
import fr.letitan.monde.Interieur;
import fr.letitan.vie.EtatCorps.Foyer;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.monster.Silverfish;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

import java.util.List;
import java.util.Set;

/**
 * Les maladies du Titan. De temps en temps (et quand il boit une eau croupie), un foyer
 * d'infection se loge dans la paroi d'un organe : un kyste (un catalyseur de sculk) d'où
 * l'infection s'étend, et d'où sortent des parasites. Tant qu'il en a, sa santé baisse : il
 * ralentit, puis ne peut plus marcher. Casser le kyste guérit le foyer.
 */
public final class Maladies {
	/** Sous cette santé, le Titan est trop faible pour marcher. */
	public static final double SANTE_IMMOBILE = 20;
	private static final int FOYERS_MAX = 3;
	/** Une chance sur ce nombre, chaque minute, qu'un nouveau foyer apparaisse. */
	private static final int RARETE = 20;
	private static final String PARASITE = "letitan.parasite";

	/** Les blocs de paroi que l'infection peut gagner. */
	private static final Set<Block> PAROIS = Set.of(Blocks.NETHER_WART_BLOCK, Blocks.MUD, Blocks.PACKED_MUD,
			Blocks.WOOL.pick(net.minecraft.world.item.DyeColor.PINK),
			Blocks.DYED_TERRACOTTA.pick(net.minecraft.world.item.DyeColor.PINK));

	/** Les organes où un foyer peut se loger. */
	private enum Lieu {
		COEUR("le cœur", Interieur.COEUR, "Le cœur du Titan bat de travers : une infection s'y est logée."),
		POUMON_GAUCHE("le poumon gauche", Interieur.POUMONS[0], "Le Titan tousse : quelque chose infecte son poumon gauche."),
		POUMON_DROIT("le poumon droit", Interieur.POUMONS[1], "Le Titan tousse : quelque chose infecte son poumon droit."),
		ESTOMAC("l'estomac", Interieur.ESTOMAC, "Le Titan a mal au ventre : une infection ronge son estomac."),
		CONDUIT("le grand conduit", new double[] {0, 62, -40}, "Le Titan gémit : une infection s'installe dans son grand conduit.");

		final String nom;
		final double[] centre;
		final String annonce;

		Lieu(String nom, double[] centre, String annonce) {
			this.nom = nom;
			this.centre = centre;
			this.annonce = annonce;
		}
	}

	private Maladies() {
	}

	public static EtatCorps etat(MinecraftServer serveur) {
		return serveur.getDataStorage().computeIfAbsent(EtatCorps.TYPE);
	}

	static void tick(ServerLevel dos) {
		EtatCorps etat = etat(dos.getServer());
		long temps = dos.getGameTime();
		RandomSource hasard = dos.getRandom();
		if (temps % 1200 == 0) {
			double avant = etat.sante;
			if (etat.foyers.isEmpty()) {
				etat.sante = Math.min(100, etat.sante + 2);
			} else {
				for (Foyer f : etat.foyers) {
					etat.sante -= 1 + 2 * f.force;
				}
				etat.sante = Math.max(0, etat.sante);
			}
			annoncerSante(dos, avant, etat.sante);
			if (etat.foyers.size() < FOYERS_MAX && hasard.nextInt(RARETE) == 0) {
				infecter(dos, Lieu.values()[hasard.nextInt(Lieu.values().length)]);
			}
			etat.setDirty();
		}
		for (Foyer f : List.copyOf(etat.foyers)) {
			BlockPos kyste = new BlockPos(f.x, f.y, f.z);
			if (!dos.isLoaded(kyste)) {
				continue;
			}
			if (!dos.getBlockState(kyste).is(Blocks.SCULK_CATALYST)) {
				// Le kyste a disparu (cassé, explosé...) : le foyer guérit.
				guerir(dos, etat, f, null);
				continue;
			}
			if (temps % 100 == 0) {
				f.force = Math.min(1, f.force + 0.01);
				etendre(dos, f, hasard);
				etat.setDirty();
			}
			if (temps % 200 == 0) {
				lacherDesParasites(dos, f, hasard);
			}
		}
		// Les poumons malades font tousser le Titan : un nuage noir sort de l'évent.
		if (temps % 600 == 300 && etat.foyers.stream().anyMatch(f -> f.organe.startsWith("POUMON"))) {
			tousser(dos);
		}
	}

	/** Il a bu une eau croupie : une chance sur deux d'avoir mal au ventre. */
	public static void eauCroupie(ServerLevel dos) {
		EtatCorps etat = etat(dos.getServer());
		if (etat.foyers.size() < FOYERS_MAX && dos.getRandom().nextBoolean()
				&& etat.foyers.stream().noneMatch(f -> f.organe.equals(Lieu.ESTOMAC.name()))) {
			infecter(dos, Lieu.ESTOMAC);
		}
	}

	/** Un foyer dans un organe au hasard (commande d'essai). */
	public static void infecterAuHasard(ServerLevel dos) {
		infecter(dos, Lieu.values()[dos.getRandom().nextInt(Lieu.values().length)]);
	}

	/** Loge un foyer dans la paroi de cet organe : on part du centre dans une direction au hasard jusqu'au mur. */
	static void infecter(ServerLevel dos, Lieu lieu) {
		EtatCorps etat = etat(dos.getServer());
		RandomSource hasard = dos.getRandom();
		for (int essai = 0; essai < 30; essai++) {
			double dx = hasard.nextGaussian(), dy = hasard.nextGaussian() * 0.5, dz = hasard.nextGaussian();
			double n = Math.sqrt(dx * dx + dy * dy + dz * dz);
			for (double r = 1; r < 40; r += 0.5) {
				int x = (int) Math.floor(lieu.centre[0] + dx / n * r);
				int y = (int) Math.floor(lieu.centre[1] + dy / n * r);
				int z = (int) Math.floor(lieu.centre[2] + dz / n * r);
				Matiere m = FormeTitan.matiere(x, y, z);
				if (m == Matiere.SANG || m == Matiere.ACIDE) {
					break;
				}
				if (m.plein()) {
					BlockPos pos = new BlockPos(x, y, z);
					dos.setBlock(pos, Blocks.SCULK_CATALYST.defaultBlockState(), Block.UPDATE_ALL);
					etat.foyers.add(new Foyer(lieu.name(), x, y, z, 0));
					etat.setDirty();
					prevenir(dos, Component.literal(lieu.annonce + " Va crever le kyste dans " + lieu.nom + " !")
							.withStyle(ChatFormatting.DARK_RED));
					LeTitan.LOGGER.info("Un foyer d'infection dans {} en {} {} {}.", lieu.nom, x, y, z);
					return;
				}
			}
		}
	}

	/** L'infection gagne quelques blocs de paroi autour du kyste, plus loin à mesure qu'il grossit. */
	private static void etendre(ServerLevel dos, Foyer f, RandomSource hasard) {
		int rayon = 2 + (int) Math.round(f.force * 7);
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
		for (int i = 0; i < 4; i++) {
			pos.set(f.x + hasard.nextInt(2 * rayon + 1) - rayon, f.y + hasard.nextInt(2 * rayon + 1) - rayon,
					f.z + hasard.nextInt(2 * rayon + 1) - rayon);
			if (PAROIS.contains(dos.getBlockState(pos).getBlock())) {
				dos.setBlock(pos, Blocks.SCULK.defaultBlockState(), Block.UPDATE_ALL);
			}
		}
	}

	private static void lacherDesParasites(ServerLevel dos, Foyer f, RandomSource hasard) {
		BlockPos kyste = new BlockPos(f.x, f.y, f.z);
		if (dos.getNearestPlayer(f.x, f.y, f.z, 48, false) == null) {
			return;
		}
		int presents = dos.getEntitiesOfClass(Silverfish.class, new AABB(kyste).inflate(32),
				s -> s.entityTags().contains(PARASITE)).size();
		if (presents >= 1 + 3 * f.force) {
			return;
		}
		for (int essai = 0; essai < 20; essai++) {
			BlockPos pos = kyste.offset(hasard.nextInt(9) - 4, hasard.nextInt(5) - 2, hasard.nextInt(9) - 4);
			if (dos.getBlockState(pos).isAir() && dos.getBlockState(pos.above()).isAir() && dos.getBlockState(pos.below()).isSolid()) {
				Silverfish parasite = EntityTypes.SILVERFISH.create(dos, EntitySpawnReason.MOB_SUMMONED);
				if (parasite == null) {
					return;
				}
				parasite.snapTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, hasard.nextFloat() * 360, 0);
				parasite.setCustomName(Component.literal("Parasite du Titan"));
				parasite.addTag(PARASITE);
				parasite.setPersistenceRequired();
				dos.addFreshEntity(parasite);
				return;
			}
		}
	}

	/** Un bloc vient d'être cassé sur le dos : était-ce un kyste ? */
	public static void blocCasse(ServerLevel dos, ServerPlayer joueur, BlockPos pos, BlockState etat) {
		if (!etat.is(Blocks.SCULK_CATALYST)) {
			return;
		}
		EtatCorps corps = etat(dos.getServer());
		for (Foyer f : List.copyOf(corps.foyers)) {
			if (f.x == pos.getX() && f.y == pos.getY() && f.z == pos.getZ()) {
				guerir(dos, corps, f, joueur);
			}
		}
	}

	/** Le foyer guérit : l'infection se résorbe, les parasites meurent, le Titan va mieux. */
	private static void guerir(ServerLevel dos, EtatCorps etat, Foyer f, ServerPlayer joueur) {
		etat.foyers.remove(f);
		etat.sante = Math.min(100, etat.sante + 10);
		etat.setDirty();
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
		int r = 10;
		for (int x = f.x - r; x <= f.x + r; x++) {
			for (int y = f.y - r; y <= f.y + r; y++) {
				for (int z = f.z - r; z <= f.z + r; z++) {
					pos.set(x, y, z);
					if (dos.getBlockState(pos).is(Blocks.SCULK)) {
						dos.setBlock(pos, GenerateurTitan.bloc(FormeTitan.matiere(x, y, z)), Block.UPDATE_ALL);
					}
				}
			}
		}
		dos.getEntitiesOfClass(Silverfish.class, new AABB(f.x, f.y, f.z, f.x + 1, f.y + 1, f.z + 1).inflate(40),
				s -> s.entityTags().contains(PARASITE)).forEach(s -> s.kill(dos));
		dos.playSound(null, f.x, f.y, f.z, SoundEvents.SCULK_SHRIEKER_SHRIEK, SoundSource.HOSTILE, 2.0f, 0.6f);
		String qui = joueur == null ? "Le kyste a crevé" : joueur.getName().getString() + " a crevé le kyste";
		prevenir(dos, Component.literal(qui + " : le Titan se sent mieux. Santé : " + Math.round(etat.sante) + " %.")
				.withStyle(ChatFormatting.GREEN));
	}

	private static void tousser(ServerLevel dos) {
		int x = Interieur.EVENT[0], z = Interieur.EVENT[1];
		int y = FormeTitan.hautDuDos(x, z) + 2;
		dos.sendParticles(ParticleTypes.LARGE_SMOKE, x + 0.5, y, z + 0.5, 60, 1.5, 2.0, 1.5, 0.08);
		for (ServerPlayer joueur : dos.players()) {
			dos.playSound(null, joueur.getX(), joueur.getY(), joueur.getZ(), SoundEvents.RAVAGER_ROAR,
					SoundSource.AMBIENT, 1.5f, 0.3f);
		}
	}

	private static void annoncerSante(ServerLevel dos, double avant, double apres) {
		if (avant >= 50 && apres < 50) {
			prevenir(dos, Component.literal("Le Titan est malade : il ralentit et souffre. Soigne-le !").withStyle(ChatFormatting.RED));
		} else if (avant >= SANTE_IMMOBILE && apres < SANTE_IMMOBILE) {
			prevenir(dos, Component.literal("Le Titan est trop faible pour marcher. Il ne repartira pas tant qu'il ne sera pas soigné.")
					.withStyle(ChatFormatting.DARK_RED));
		} else if (avant < SANTE_IMMOBILE && apres >= SANTE_IMMOBILE) {
			prevenir(dos, Component.literal("Le Titan reprend des forces : il peut de nouveau marcher.").withStyle(ChatFormatting.GREEN));
		}
	}

	/** Où sont les foyers, pour la boussole : « le cœur, l'estomac ». */
	public static String organesMalades(MinecraftServer serveur) {
		return String.join(", ", etat(serveur).foyers.stream().map(f -> Lieu.valueOf(f.organe).nom).toList());
	}

	private static void prevenir(ServerLevel dos, Component message) {
		dos.players().forEach(j -> j.sendSystemMessage(message));
	}
}
