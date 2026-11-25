package fr.letitan.vie;

import fr.letitan.LeTitan;
import fr.letitan.monde.FormeTitan;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.VineBlock;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Les lianes qui pendent du flanc du Titan au-dessus de chaque patte : on les attrape en haut
 * de la patte, sous le ventre, et elles mènent jusqu'au dos. Posées au démarrage du serveur,
 * là où il en manque : elles repoussent si on les coupe, et apparaissent aussi sur un Titan
 * créé avant leur arrivée.
 */
public final class Lianes {
	private Lianes() {
	}

	public static void pousser(ServerLevel dos) {
		BlockState est = Blocks.VINE.defaultBlockState().setValue(VineBlock.EAST, true);
		BlockState ouest = Blocks.VINE.defaultBlockState().setValue(VineBlock.WEST, true);
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
		int posees = 0;
		for (int[] colonne : FormeTitan.colonnesDeLianes()) {
			int x = colonne[0], z = colonne[1];
			for (int y = FormeTitan.Y_LIANES_BAS; y <= FormeTitan.Y_CENTRE + 60; y++) {
				int cote = FormeTitan.liane(x, y, z);
				if (cote == 0) {
					continue;
				}
				pos.set(x, y, z);
				if (dos.getBlockState(pos).isAir()) {
					// Sans mise à jour des voisins : une liane qui pend ne tient qu'à celle du dessus.
					dos.setBlock(pos, cote > 0 ? est : ouest, Block.UPDATE_CLIENTS);
					posees++;
				}
			}
		}
		if (posees > 0) {
			LeTitan.LOGGER.info("Des lianes poussent sur le flanc du Titan : {} blocs.", posees);
		}
	}
}
