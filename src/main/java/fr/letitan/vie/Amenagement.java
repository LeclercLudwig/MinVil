package fr.letitan.vie;

import fr.letitan.LeTitan;
import fr.letitan.monde.FormeTitan;
import fr.letitan.monde.FormeTitan.Matiere;
import fr.letitan.monde.GenerateurTitan;
import fr.letitan.monde.Interieur;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.util.Set;

/**
 * Creuse l'intérieur du Titan dans un monde créé avant qu'il en ait un : ses organes sont
 * creusés dans la chair, par tranches à chaque tick pour ne pas figer le jeu. Les mondes
 * nouveaux sont déjà générés ainsi ; ici, on ne fait que rattraper les anciens.
 */
final class Amenagement {
	/** À augmenter quand la forme de l'intérieur change : les mondes seront retouchés. */
	static final int VERSION = 1;
	/** Blocs examinés par tick. */
	private static final int PAR_TICK = 60_000;

	/** Les blocs que le Titan avait avant (et qu'on peut donc remplacer sans rien casser au joueur). */
	private static final Set<Block> NATURELS = Set.of(Blocks.NETHER_WART_BLOCK, Blocks.PACKED_MUD, Blocks.DIRT,
			Blocks.GRASS_BLOCK, Blocks.BONE_BLOCK, Blocks.SHORT_GRASS, Blocks.TALL_GRASS);

	private static long curseur = -1;

	private Amenagement() {
	}

	/** Avance l'aménagement ; vrai quand il est terminé (ou déjà fait). */
	static boolean avancer(ServerLevel dos, EtatCorps etat) {
		if (etat.amenagement >= VERSION) {
			return true;
		}
		int[] b = Interieur.boite();
		long nx = b[3] - b[0] + 1L, ny = b[4] - b[1] + 1L, nz = b[5] - b[2] + 1L;
		long total = nx * ny * nz;
		if (curseur < 0) {
			curseur = 0;
			LeTitan.LOGGER.info("Le Titan se creuse des entrailles ({} blocs à examiner).", total);
		}
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
		int changes = 0;
		long fin = Math.min(total, curseur + PAR_TICK);
		// On parcourt colonne par colonne (y en dernier), chunk après chunk dans l'ordre z puis x.
		for (; curseur < fin; curseur++) {
			long i = curseur;
			int y = (int) (i % ny) + b[1];
			i /= ny;
			int z = (int) (i % nz) + b[2];
			int x = (int) (i / nz) + b[0];
			Matiere m = FormeTitan.matiere(x, y, z);
			if (m == Matiere.VIDE) {
				continue;
			}
			BlockState voulu = GenerateurTitan.bloc(m);
			pos.set(x, y, z);
			BlockState actuel = dos.getBlockState(pos);
			// L'air déjà là (un tunnel creusé par un joueur) reste de l'air.
			if (actuel.equals(voulu) || actuel.isAir() || !NATURELS.contains(actuel.getBlock())) {
				continue;
			}
			// Sans mise à jour des voisins : rien ne coule ni ne tombe pendant les travaux.
			dos.setBlock(pos, voulu, Block.UPDATE_CLIENTS);
			changes++;
		}
		if (curseur >= total) {
			etat.amenagement = VERSION;
			etat.setDirty();
			curseur = -1;
			LeTitan.LOGGER.info("Les entrailles du Titan sont prêtes.");
			return true;
		}
		if (changes > 0) {
			etat.setDirty();
		}
		return false;
	}
}
