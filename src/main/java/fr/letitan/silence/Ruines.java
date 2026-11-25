package fr.letitan.silence;

import fr.letitan.LeTitan;
import fr.letitan.monde.Interieur;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.animal.feline.Cat;
import net.minecraft.world.entity.animal.golem.IronGolem;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.StandingSignBlock;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.level.block.entity.SignText;
import net.minecraft.world.level.block.entity.SignTextSlot;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Les ruines de la fin du monde. Les villages (et toutes les constructions que le monde
 * crée : avant-postes, temples, igloos...) sont retrouvés en ruine : toits effondrés, vitres
 * brisées, lumières éteintes, champs morts, toiles d'araignée, et plus personne. Et l'on
 * croise aussi les restes de l'ancien monde : maisons effondrées, tours écroulées, cimetières.
 *
 * Seulement dans les terres nouvelles, comme le reste du Silence.
 */
final class Ruines {
	/** Sous ce nombre de blocs construits, ce n'est pas une construction (un arbre, un rocher...). */
	private static final int SEUIL = 12;

	/** Ce que les hommes ont bâti en pierre, et ce que ça devient avec le temps. */
	private static final Map<Block, Block> PIERRE_USEE = Map.of(
			Blocks.COBBLESTONE, Blocks.MOSSY_COBBLESTONE,
			Blocks.STONE_BRICKS, Blocks.CRACKED_STONE_BRICKS,
			Blocks.SMOOTH_SANDSTONE, Blocks.SANDSTONE,
			Blocks.CUT_SANDSTONE, Blocks.SANDSTONE,
			Blocks.BRICKS, Blocks.MOSSY_COBBLESTONE,
			Blocks.POLISHED_ANDESITE, Blocks.ANDESITE);

	private static final Set<Block> LUMIERES = Set.of(Blocks.TORCH, Blocks.WALL_TORCH, Blocks.LANTERN, Blocks.SOUL_LANTERN,
			Blocks.SOUL_TORCH, Blocks.SOUL_WALL_TORCH, Blocks.GLOWSTONE, Blocks.SEA_LANTERN, Blocks.JACK_O_LANTERN);

	private Ruines() {
	}

	// --- Les constructions du monde, en ruine ---------------------------------

	private static boolean construit(BlockState etat) {
		return etat.is(BlockTags.PLANKS) || etat.is(BlockTags.WOODEN_STAIRS) || etat.is(BlockTags.WOODEN_SLABS)
				|| etat.is(BlockTags.WOODEN_FENCES) || etat.is(BlockTags.FENCE_GATES) || etat.is(BlockTags.DOORS)
				|| etat.is(BlockTags.BEDS) || etat.is(BlockTags.WOOL) || etat.is(BlockTags.WOOL_CARPETS)
				|| etat.is(BlockTags.WOODEN_TRAPDOORS) || etat.is(BlockTags.CANDLES) || etat.is(BlockTags.IMPERMEABLE)
				|| etat.is(Blocks.GLASS_PANE) || etat.is(Blocks.HAY_BLOCK) || etat.is(Blocks.FARMLAND)
				|| etat.is(Blocks.DIRT_PATH) || etat.is(Blocks.CAMPFIRE) || LUMIERES.contains(etat.getBlock())
				|| PIERRE_USEE.containsKey(etat.getBlock()) || etat.is(Blocks.BOOKSHELF) || etat.is(Blocks.CRAFTING_TABLE);
	}

	/** Si ce morceau du monde porte une construction, elle est en ruine, et ses habitants ont disparu. */
	static void ruiner(ServerLevel sol, ChunkPos chunk) {
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
		List<BlockPos> construits = new ArrayList<>();
		int base = Integer.MAX_VALUE;
		for (int dx = 0; dx < 16; dx++) {
			for (int dz = 0; dz < 16; dz++) {
				int x = chunk.getMinBlockX() + dx, z = chunk.getMinBlockZ() + dz;
				int surface = sol.getHeight(Heightmap.Types.WORLD_SURFACE, x, z);
				// Près de la surface seulement : les mines et les forteresses souterraines restent telles quelles.
				for (int y = surface - 14; y <= surface; y++) {
					if (construit(sol.getBlockState(pos.set(x, y, z)))) {
						construits.add(pos.immutable());
						base = Math.min(base, y);
					}
				}
			}
		}
		if (construits.size() < SEUIL) {
			return;
		}
		for (BlockPos p : construits) {
			user(sol, p, p.getY() - base);
		}
		// Des toiles d'araignée dans les coins.
		for (BlockPos p : construits) {
			BlockPos dessous = p.below();
			if (sol.getBlockState(dessous).isAir() && Interieur.hasard(p.getX(), p.getY(), p.getZ()) < 0.06) {
				sol.setBlock(dessous, Blocks.COBWEB.defaultBlockState(), Block.UPDATE_CLIENTS);
			}
		}
		// Plus personne : ni villageois, ni golem, ni chat.
		AABB zone = new AABB(chunk.getMinBlockX(), sol.getMinY(), chunk.getMinBlockZ(),
				chunk.getMaxBlockX() + 1, sol.getMaxY(), chunk.getMaxBlockZ() + 1);
		sol.getEntitiesOfClass(Villager.class, zone).forEach(Entity::discard);
		sol.getEntitiesOfClass(IronGolem.class, zone).forEach(Entity::discard);
		sol.getEntitiesOfClass(Cat.class, zone).forEach(Entity::discard);
	}

	/** Un bloc construit, usé par la fin du monde ; plus il est haut, plus il a de chances d'être tombé. */
	private static void user(ServerLevel sol, BlockPos p, int hauteur) {
		BlockState etat = sol.getBlockState(p);
		double h = Interieur.hasard(p.getX(), p.getY(), p.getZ());
		double effondre = Math.min(0.85, 0.12 + 0.1 * hauteur);
		BlockState air = Blocks.AIR.defaultBlockState();
		if (LUMIERES.contains(etat.getBlock())) {
			sol.setBlock(p, air, Block.UPDATE_CLIENTS);
		} else if (etat.is(Blocks.CAMPFIRE)) {
			sol.setBlock(p, etat.setValue(CampfireBlock.LIT, false), Block.UPDATE_CLIENTS);
		} else if (etat.is(BlockTags.IMPERMEABLE) || etat.is(Blocks.GLASS_PANE)) {
			if (h < 0.85) {
				sol.setBlock(p, air, Block.UPDATE_CLIENTS);
			}
		} else if (etat.is(BlockTags.BEDS) || etat.is(BlockTags.WOOL) || etat.is(BlockTags.WOOL_CARPETS)
				|| etat.is(Blocks.HAY_BLOCK) || etat.is(BlockTags.CANDLES)) {
			sol.setBlock(p, air, Block.UPDATE_CLIENTS);
		} else if (etat.is(BlockTags.DOORS)) {
			if (h < 0.7) {
				sol.setBlock(p, air, Block.UPDATE_CLIENTS);
				BlockPos autre = etat.getValue(DoorBlock.HALF) == DoubleBlockHalf.LOWER ? p.above() : p.below();
				if (sol.getBlockState(autre).is(BlockTags.DOORS)) {
					sol.setBlock(autre, air, Block.UPDATE_CLIENTS);
				}
			}
		} else if (etat.is(Blocks.FARMLAND) || etat.is(Blocks.DIRT_PATH)) {
			sol.setBlock(p, Blocks.COARSE_DIRT.defaultBlockState(), Block.UPDATE_CLIENTS);
			BlockState dessus = sol.getBlockState(p.above());
			if (dessus.is(BlockTags.CROPS)) {
				sol.setBlock(p.above(), h < 0.4 ? Blocks.DEAD_BUSH.defaultBlockState() : air, Block.UPDATE_CLIENTS);
			}
		} else if (PIERRE_USEE.containsKey(etat.getBlock())) {
			if (h < effondre * 0.6) {
				sol.setBlock(p, air, Block.UPDATE_CLIENTS);
			} else if (h < effondre * 0.6 + 0.3) {
				sol.setBlock(p, PIERRE_USEE.get(etat.getBlock()).defaultBlockState(), Block.UPDATE_CLIENTS);
			}
		} else if (etat.is(BlockTags.PLANKS)) {
			if (h < effondre) {
				sol.setBlock(p, air, Block.UPDATE_CLIENTS);
			} else if (h < effondre + 0.25) {
				// Le bois noirci par les incendies.
				sol.setBlock(p, Blocks.DARK_OAK_PLANKS.defaultBlockState(), Block.UPDATE_CLIENTS);
			}
		} else if (h < effondre) {
			sol.setBlock(p, air, Block.UPDATE_CLIENTS);
		}
	}

	// --- Les restes de l'ancien monde -----------------------------------------

	private static final List<String[]> EPITAPHES = List.of(
			new String[] {"Ici repose", "Aldric,", "qui n'entendait", "plus rien."},
			new String[] {"Maëlle", "partie chercher", "les Titans.", ""},
			new String[] {"", "Ils sont venus", "sans bruit.", ""},
			new String[] {"Que le Chant", "te retrouve,", "mon fils.", ""},
			new String[] {"Ci-gît", "le dernier", "forgeron", "du village."},
			new String[] {"Nous n'avons", "pas su", "nous taire.", ""},
			new String[] {"Hugo", "a couru.", "", ""},
			new String[] {"Attendez", "le Titan.", "Il reviendra.", ""});

	/** Un nouveau morceau du monde : y laisser les restes de l'ancien monde ? */
	static void peutEtreUneRuine(ServerLevel sol, ChunkPos chunk) {
		BlockPos spawn = sol.getRespawnData().pos();
		int x = chunk.getMinBlockX() + 8, z = chunk.getMinBlockZ() + 8;
		if (Math.hypot(x - spawn.getX(), z - spawn.getZ()) < 200
				|| Interieur.hasard(chunk.x() * 3 + 1, (int) (sol.getSeed() >>> 20 & 0xFFF), chunk.z() * 5) >= 1 / 55.0) {
			return;
		}
		// Il faut un terrain à peu près plat et sec.
		int y = sol.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z) - 1;
		for (int[] c : new int[][] {{-5, -5}, {5, -5}, {-5, 5}, {5, 5}}) {
			int yc = sol.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x + c[0], z + c[1]) - 1;
			if (Math.abs(yc - y) > 3) {
				return;
			}
		}
		if (!sol.getBlockState(new BlockPos(x, y, z)).getFluidState().isEmpty()) {
			return;
		}
		double sorte = Interieur.hasard(chunk.x(), 77, chunk.z());
		if (sorte < 0.45) {
			maison(sol, x, y, z);
		} else if (sorte < 0.75) {
			tour(sol, x, y, z);
		} else {
			cimetiere(sol, x, y, z);
		}
	}

	private static void poser(ServerLevel sol, int x, int y, int z, BlockState etat) {
		sol.setBlock(new BlockPos(x, y, z), etat, Block.UPDATE_CLIENTS);
	}

	/** Le sol sous une ruine : on comble les creux et on dégage le dessus. */
	private static void aplanir(ServerLevel sol, int x, int y, int z, int rayon, int dessus) {
		for (int dx = -rayon; dx <= rayon; dx++) {
			for (int dz = -rayon; dz <= rayon; dz++) {
				for (int dy = -3; dy < 0; dy++) {
					if (!sol.getBlockState(new BlockPos(x + dx, y + dy + 1, z + dz)).isSolid()) {
						poser(sol, x + dx, y + dy + 1, z + dz, Blocks.DIRT.defaultBlockState());
					}
				}
				for (int dy = 1; dy <= dessus; dy++) {
					poser(sol, x + dx, y + dy, z + dz, Blocks.AIR.defaultBlockState());
				}
			}
		}
	}

	private static BlockState pierre(int x, int y, int z) {
		double h = Interieur.hasard(x, y, z);
		return (h < 0.35 ? Blocks.MOSSY_COBBLESTONE : h < 0.5 ? Blocks.CRACKED_STONE_BRICKS : Blocks.COBBLESTONE).defaultBlockState();
	}

	/** Une maison effondrée : des murs à moitié tombés, une cheminée, et ce que ses habitants ont laissé. */
	private static void maison(ServerLevel sol, int x, int y, int z) {
		aplanir(sol, x, y, z, 5, 8);
		for (int dx = -3; dx <= 3; dx++) {
			for (int dz = -4; dz <= 4; dz++) {
				int bx = x + dx, bz = z + dz;
				poser(sol, bx, y, bz, Interieur.hasard(bx, y, bz) < 0.6 ? Blocks.SPRUCE_PLANKS.defaultBlockState()
						: Blocks.COARSE_DIRT.defaultBlockState());
				boolean mur = Math.abs(dx) == 3 || Math.abs(dz) == 4;
				boolean porte = dx == 0 && dz == 4;
				if (!mur || porte) {
					continue;
				}
				int hauteur = 1 + (int) (Interieur.hasard(bx, 3, bz) * 4);
				for (int dy = 1; dy <= hauteur; dy++) {
					boolean coin = Math.abs(dx) == 3 && Math.abs(dz) == 4;
					poser(sol, bx, y + dy, bz, coin ? Blocks.DARK_OAK_LOG.defaultBlockState() : pierre(bx, y + dy, bz));
				}
			}
		}
		// La cheminée tient encore debout.
		for (int dy = 1; dy <= 6; dy++) {
			poser(sol, x + 2, y + dy, z - 3, Blocks.BRICKS.defaultBlockState());
		}
		poser(sol, x - 2, y + 2, z + 2, Blocks.COBWEB.defaultBlockState());
		BlockPos coffre = new BlockPos(x - 2, y + 1, z - 3);
		sol.setBlock(coffre, Blocks.CHEST.defaultBlockState(), Block.UPDATE_ALL);
		if (sol.getBlockEntity(coffre) instanceof ChestBlockEntity c) {
			c.setItem(0, new ItemStack(Items.PAPER, 3));
			c.setItem(4, new ItemStack(Items.IRON_NUGGET, 2 + (int) (Interieur.hasard(x, y, z) * 8)));
			c.setItem(9, new ItemStack(Items.ROTTEN_FLESH, 2));
			c.setItem(13, new ItemStack(Items.WHEAT_SEEDS, 5));
			if (Interieur.hasard(z, y, x) < 0.3) {
				c.setItem(22, new ItemStack(Items.EMERALD, 1 + (int) (Interieur.hasard(y, x, z) * 3)));
			}
			c.setItem(17, new ItemStack(Items.CANDLE, 2));
		}
		LeTitan.LOGGER.debug("Une maison en ruine en {} {} {}.", x, y, z);
	}

	/** Une tour écroulée : un anneau de pierre brisé à des hauteurs différentes, et ses gravats autour. */
	private static void tour(ServerLevel sol, int x, int y, int z) {
		LeTitan.LOGGER.debug("Une tour en ruine en {} {} {}.", x, y, z);
		aplanir(sol, x, y, z, 6, 16);
		for (int dx = -3; dx <= 3; dx++) {
			for (int dz = -3; dz <= 3; dz++) {
				double r = Math.hypot(dx, dz);
				int bx = x + dx, bz = z + dz;
				if (r <= 3.4) {
					poser(sol, bx, y, bz, pierre(bx, y, bz));
				}
				if (r < 2.5 || r > 3.4 || (dx == 0 && dz == 3)) {
					continue;
				}
				// Le sommet s'est effondré : chaque pan de mur s'arrête à une hauteur différente.
				int hauteur = 4 + (int) (Interieur.hasard(bx, 5, bz) * 11);
				for (int dy = 1; dy <= hauteur; dy++) {
					poser(sol, bx, y + dy, bz, Interieur.hasard(bx, y + dy, bz) < 0.5
							? Blocks.STONE_BRICKS.defaultBlockState() : pierre(bx, y + dy, bz));
				}
			}
		}
		// Les gravats tombés autour.
		for (int i = 0; i < 14; i++) {
			int gx = x + (int) ((Interieur.hasard(x, i, z) - 0.5) * 14), gz = z + (int) ((Interieur.hasard(z, i, x) - 0.5) * 14);
			int gy = sol.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, gx, gz);
			poser(sol, gx, gy, gz, pierre(gx, gy, gz));
		}
	}

	/** Un cimetière : des tombes alignées, certaines avec une épitaphe. */
	private static void cimetiere(ServerLevel sol, int x, int y, int z) {
		LeTitan.LOGGER.debug("Un cimetière en {} {} {}.", x, y, z);
		aplanir(sol, x, y, z, 5, 4);
		for (int i = -1; i <= 1; i++) {
			for (int j = -1; j <= 1; j++) {
				int tx = x + i * 3, tz = z + j * 4;
				poser(sol, tx, y, tz, Blocks.COARSE_DIRT.defaultBlockState());
				poser(sol, tx, y, tz + 1, Blocks.COARSE_DIRT.defaultBlockState());
				double h = Interieur.hasard(tx, y, tz);
				if (h < 0.55) {
					BlockPos panneau = new BlockPos(tx, y + 1, tz - 1);
					sol.setBlock(panneau, Blocks.SPRUCE_SIGN.defaultBlockState().setValue(StandingSignBlock.ROTATION, 0), Block.UPDATE_ALL);
					if (sol.getBlockEntity(panneau) instanceof SignBlockEntity s) {
						String[] e = EPITAPHES.get((int) (Interieur.hasard(tz, tx, y) * EPITAPHES.size()));
						List<Component> lignes = java.util.Arrays.stream(e).map(t -> (Component) Component.literal(t)).toList();
						s.updateText(t -> new SignText(lignes, lignes, DyeColor.BLACK, false), SignTextSlot.FRONT);
						sol.sendBlockUpdated(panneau, sol.getBlockState(panneau), sol.getBlockState(panneau), Block.UPDATE_ALL);
					}
				} else {
					poser(sol, tx, y + 1, tz - 1, Blocks.MOSSY_STONE_BRICK_WALL.defaultBlockState());
				}
				if (h > 0.8) {
					poser(sol, tx, y + 1, tz + 1, Blocks.DEAD_BUSH.defaultBlockState());
				}
			}
		}
		// Ce qui reste de la clôture.
		for (int dx = -5; dx <= 5; dx++) {
			for (int dz : new int[] {-6, 6}) {
				if (Interieur.hasard(x + dx, y, z + dz) < 0.5) {
					poser(sol, x + dx, y + 1, z + dz, Blocks.SPRUCE_FENCE.defaultBlockState());
				}
			}
		}
	}
}
