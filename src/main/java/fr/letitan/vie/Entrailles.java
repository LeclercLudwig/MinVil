package fr.letitan.vie;

import fr.letitan.Rythme;
import fr.letitan.monde.FormeTitan;
import fr.letitan.monde.Interieur;
import fr.letitan.monde.Interieur.Organe;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.entity.BarrelBlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import java.util.List;

/**
 * La vie des entrailles, côté serveur : l'évent souffle, l'acide de l'estomac brûle, et ce que
 * le Titan broute finit dans son estomac — des boules de feuillage et des tonneaux de restes
 * (ce qu'il a avalé avec : bois, pommes, et parfois les affaires d'un voyageur malchanceux).
 */
public final class Entrailles {
	/** On garde les restes des derniers repas ; les plus vieux se dissolvent dans l'acide. */
	private static final int REPAS_MAX = 6;

	private Entrailles() {
	}

	/** Appelé à chaque tick de la dimension du Titan. */
	public static void tick(ServerLevel dos) {
		EtatCorps etat = Maladies.etat(dos.getServer());
		if (!Amenagement.avancer(dos, etat)) {
			return;
		}
		Coeur.tick(dos);
		Maladies.tick(dos);
		long temps = dos.getGameTime();
		souffler(dos, temps);
		for (ServerPlayer joueur : dos.players()) {
			Organe organe = Interieur.organe(joueur.getX(), joueur.getY() + 0.5, joueur.getZ());
			if (organe == Organe.EVENT || organe == Organe.SORTIE) {
				// On s'y agrippe aux parois : pas de chute qui blesse.
				joueur.resetFallDistance();
			}
			if (organe == Organe.ESTOMAC && joueur.isInWater() && temps % 20 == 0) {
				joueur.addEffect(new MobEffectInstance(MobEffects.POISON, 60, 1));
				joueur.sendOverlayMessage(Component.literal("L'acide de l'estomac te ronge ! Sors-en vite.")
						.withStyle(ChatFormatting.DARK_GREEN));
			}
		}
	}

	/** Le souffle de l'évent : un panache de vapeur et un grand bruit d'air à chaque expiration. */
	private static void souffler(ServerLevel dos, long temps) {
		if (!Rythme.expire(temps)) {
			return;
		}
		int x = Interieur.EVENT[0], z = Interieur.EVENT[1];
		int y = FormeTitan.hautDuDos(x, z) + 1;
		if (temps % 3 == 0) {
			dos.sendParticles(ParticleTypes.CLOUD, x + 0.5, y, z + 0.5, 6, 1.0, 0.3, 1.0, 0.12);
		}
		if (Rythme.debutExpiration(temps)) {
			dos.playSound(null, x + 0.5, y, z + 0.5, SoundEvents.ENDER_DRAGON_FLAP, SoundSource.AMBIENT, 4.0f, 0.3f);
		}
	}

	// --- Repas ---------------------------------------------------------------

	/** Le Titan vient de brouter : une boule de feuillage et un tonneau de restes arrivent dans l'estomac. */
	public static void repas(ServerLevel dos) {
		EtatCorps etat = Maladies.etat(dos.getServer());
		RandomSource hasard = dos.getRandom();
		double[] e = Interieur.ESTOMAC;
		// Une boule de feuillage flotte dans l'acide, avec du bois au milieu.
		BlockPos boule = surLAcide(hasard, e);
		if (boule != null) {
			BlockState feuilles = Blocks.OAK_LEAVES.defaultBlockState().setValue(LeavesBlock.PERSISTENT, true);
			for (BlockPos p : BlockPos.betweenClosed(boule.offset(-2, -1, -2), boule.offset(2, 2, 2))) {
				double d = p.distSqr(boule);
				if (d <= 5 && Interieur.organe(p.getX() + 0.5, p.getY() + 0.5, p.getZ() + 0.5) == Organe.ESTOMAC) {
					dos.setBlock(p, d <= 1 ? Blocks.OAK_LOG.defaultBlockState() : feuilles, Block.UPDATE_CLIENTS);
				}
			}
		}
		// Un radeau de glu porte un tonneau de restes.
		BlockPos radeau = surLAcide(hasard, e);
		if (radeau != null) {
			dos.setBlock(radeau.below(), Blocks.SLIME_BLOCK.defaultBlockState(), Block.UPDATE_ALL);
			dos.setBlock(radeau, Blocks.BARREL.defaultBlockState(), Block.UPDATE_ALL);
			if (dos.getBlockEntity(radeau) instanceof BarrelBlockEntity tonneau) {
				remplir(tonneau, hasard);
			}
			etat.repas.add(radeau.asLong());
			while (etat.repas.size() > REPAS_MAX) {
				dissoudre(dos, BlockPos.of(etat.repas.removeFirst()));
			}
			etat.setDirty();
		}
	}

	/** Une place au ras de l'acide, au hasard dans l'estomac. */
	private static BlockPos surLAcide(RandomSource hasard, double[] e) {
		for (int essai = 0; essai < 40; essai++) {
			int x = (int) Math.floor(e[0] + (hasard.nextDouble() * 2 - 1) * 20);
			int z = (int) Math.floor(e[2] + (hasard.nextDouble() * 2 - 1) * 26);
			int y = Interieur.Y_ACIDE + 1;
			if (Interieur.organe(x + 0.5, y + 0.5, z + 0.5) == Organe.ESTOMAC
					&& Interieur.organe(x + 0.5, y + 3.5, z + 0.5) == Organe.ESTOMAC) {
				return new BlockPos(x, y, z);
			}
		}
		return null;
	}

	/** Les plus vieux restes se dissolvent : le tonneau et son radeau redeviennent acide. */
	private static void dissoudre(ServerLevel dos, BlockPos tonneau) {
		if (dos.getBlockState(tonneau).is(Blocks.BARREL)) {
			dos.setBlock(tonneau, Blocks.CAVE_AIR.defaultBlockState(), Block.UPDATE_ALL);
		}
		if (dos.getBlockState(tonneau.below()).is(Blocks.SLIME_BLOCK)) {
			dos.setBlock(tonneau.below(), Blocks.WATER.defaultBlockState(), Block.UPDATE_ALL);
		}
	}

	private record Reste(Item item, int min, int max, float chance) {
	}

	private static final List<Reste> RESTES = List.of(
			new Reste(Items.OAK_LOG, 4, 12, 1f),
			new Reste(Items.STICK, 3, 10, 0.8f),
			new Reste(Items.APPLE, 1, 4, 0.7f),
			new Reste(Items.OAK_SAPLING, 1, 3, 0.6f),
			new Reste(Items.WHEAT_SEEDS, 2, 8, 0.5f),
			new Reste(Items.BONE, 1, 5, 0.5f),
			new Reste(Items.ROTTEN_FLESH, 1, 4, 0.4f),
			new Reste(Items.SLIME_BALL, 1, 3, 0.3f),
			// Ce que des voyageurs ont perdu en se trouvant sous sa bouche.
			new Reste(Items.IRON_INGOT, 1, 4, 0.25f),
			new Reste(Items.GOLD_NUGGET, 2, 9, 0.25f),
			new Reste(Items.EMERALD, 1, 3, 0.12f),
			new Reste(Items.LEATHER_BOOTS, 1, 1, 0.08f),
			new Reste(Items.SADDLE, 1, 1, 0.05f),
			new Reste(Items.NAME_TAG, 1, 1, 0.05f),
			new Reste(Items.DIAMOND, 1, 2, 0.04f));

	private static void remplir(BarrelBlockEntity tonneau, RandomSource hasard) {
		int case_ = 0;
		for (Reste r : RESTES) {
			if (hasard.nextFloat() < r.chance() && case_ < tonneau.getContainerSize()) {
				tonneau.setItem(case_++, new ItemStack(r.item(), r.min() + hasard.nextInt(r.max() - r.min() + 1)));
			}
		}
	}
}
