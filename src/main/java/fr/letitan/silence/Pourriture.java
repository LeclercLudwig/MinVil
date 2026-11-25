package fr.letitan.silence;

import fr.letitan.monde.Interieur;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.MultifaceBlock;
import net.minecraft.world.level.block.SculkShriekerBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap;

import java.util.ArrayDeque;
import java.util.Queue;

/**
 * La pourriture des terres mortes : quand un morceau de monde est créé dans une terre morte,
 * le Silence l'a déjà recouvert. Le sol devient sculk, les arbres perdent leurs feuilles, des
 * capteurs guettent le moindre bruit. C'est aussi là qu'on dresse les Pierres-Échos.
 *
 * Seulement sur les terres nouvelles : on ne touche jamais à ce que le joueur a déjà vu.
 */
final class Pourriture {
	/** Morceaux traités par tick, pour ne pas ralentir l'exploration. */
	private static final int PAR_TICK = 2;
	private static final Queue<ChunkPos> A_FAIRE = new ArrayDeque<>();

	private Pourriture() {
	}

	/** Un morceau de monde vient d'être créé. */
	static void nouveau(LevelChunk chunk) {
		A_FAIRE.add(chunk.getPos());
	}

	static void oublier() {
		A_FAIRE.clear();
	}

	static void tick(ServerLevel sol) {
		for (int i = 0; i < PAR_TICK && !A_FAIRE.isEmpty(); i++) {
			ChunkPos pos = A_FAIRE.poll();
			if (sol.hasChunk(pos.x(), pos.z())) {
				Ruines.ruiner(sol, pos);
				pourrir(sol, pos);
				Ruines.peutEtreUneRuine(sol, pos);
				Recit.peutEtreUnePierre(sol, pos);
			}
		}
	}

	/**
	 * Le vrai sol de la colonne (un bloc plein qui n'est ni bois ni feuillage), sous les arbres
	 * et les bambous ; ou Integer.MIN_VALUE si c'est de l'eau.
	 */
	private static int solide(ServerLevel sol, BlockPos.MutableBlockPos pos, int x, int z) {
		int haut = sol.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z) - 1;
		for (int y = haut; y > haut - 48 && y > sol.getMinY(); y--) {
			BlockState etat = sol.getBlockState(pos.set(x, y, z));
			if (!etat.getFluidState().isEmpty()) {
				return Integer.MIN_VALUE;
			}
			if (etat.isSolidRender() && !etat.is(BlockTags.LOGS) && !etat.is(BlockTags.LEAVES)) {
				return y;
			}
		}
		return Integer.MIN_VALUE;
	}

	private static void pourrir(ServerLevel sol, ChunkPos chunk) {
		ZonesMortes zones = Silence.zones(sol.getServer());
		BlockState vrille = Blocks.SCULK_VEIN.defaultBlockState().setValue(MultifaceBlock.getFaceProperty(Direction.DOWN), true);
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
		for (int dx = 0; dx < 16; dx++) {
			for (int dz = 0; dz < 16; dz++) {
				int x = chunk.getMinBlockX() + dx, z = chunk.getMinBlockZ() + dz;
				double force = zones.intensite(x, z);
				if (force < ZonesMortes.BORD) {
					continue;
				}
				double hasard = Interieur.hasard(x, 7, z);
				int sommet = solide(sol, pos, x, z);
				if (sommet == Integer.MIN_VALUE) {
					continue;
				}
				pos.set(x, sommet, z);
				if (force < ZonesMortes.MORTE) {
					// Au bord : quelques plaques et des veines de sculk.
					double part = (force - ZonesMortes.BORD) / (ZonesMortes.MORTE - ZonesMortes.BORD);
					if (hasard < part * 0.5) {
						sol.setBlock(pos, Blocks.SCULK.defaultBlockState(), Block.UPDATE_CLIENTS);
					} else if (hasard < part) {
						pos.setY(sommet + 1);
						if (sol.getBlockState(pos).canBeReplaced()) {
							sol.setBlock(pos, vrille, Block.UPDATE_CLIENTS);
						}
					}
					continue;
				}
				// Au cœur : le sol est du sculk sur trois blocs.
				for (int y = sommet; y > sommet - 3; y--) {
					pos.setY(y);
					BlockState etat = sol.getBlockState(pos);
					if (etat.isSolid() && !etat.is(Blocks.BEDROCK) && !etat.is(BlockTags.LOGS)) {
						sol.setBlock(pos, Blocks.SCULK.defaultBlockState(), Block.UPDATE_CLIENTS);
					}
				}
				// Plus rien ne pousse : herbes et fleurs disparaissent, les arbres perdent leurs feuilles.
				for (int y = sommet + 1; y < sommet + 30; y++) {
					pos.setY(y);
					BlockState etat = sol.getBlockState(pos);
					if (etat.is(BlockTags.LEAVES) || etat.is(Blocks.BAMBOO)
							|| (etat.canBeReplaced() && !etat.isAir() && etat.getFluidState().isEmpty())) {
						sol.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
					}
				}
				// Et le Silence écoute.
				pos.setY(sommet + 1);
				if (!sol.getBlockState(pos).isAir()) {
					continue;
				}
				if (hasard > 0.993) {
					sol.setBlock(pos, Blocks.SCULK_SENSOR.defaultBlockState(), Block.UPDATE_CLIENTS);
				} else if (hasard > 0.990) {
					sol.setBlock(pos, Blocks.SCULK_SHRIEKER.defaultBlockState().setValue(SculkShriekerBlock.CAN_SUMMON, false),
							Block.UPDATE_CLIENTS);
				} else if (hasard > 0.989) {
					sol.setBlock(pos.setY(sommet), Blocks.SCULK_CATALYST.defaultBlockState(), Block.UPDATE_CLIENTS);
				}
			}
		}
	}
}
