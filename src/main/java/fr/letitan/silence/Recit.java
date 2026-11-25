package fr.letitan.silence;

import fr.letitan.LeTitan;
import fr.letitan.monde.FormeTitan;
import fr.letitan.monde.Interieur;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.Filterable;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.WrittenBookContent;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LecternBlock;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.entity.BarrelBlockEntity;
import net.minecraft.world.level.block.entity.LecternBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.BlockHitResult;

import java.util.List;

/**
 * Le récit du Silence : la cabane de la dernière Gardienne sur le dos du Titan, avec son
 * journal, et les Pierres-Échos dressées dans le monde d'en bas, qui rejouent chacune un
 * fragment de l'histoire du monde quand on les touche.
 */
public final class Recit {

	/** Les fragments de l'histoire, dans l'ordre où on les découvre. */
	static final List<String> FRAGMENTS = List.of(
			"Avant, le monde chantait. Chaque arbre, chaque bête, chaque pierre avait son rythme. Nous appelions cela le Chant.",
			"Le Silence est monté des profondeurs, là où le Chant ne descendait plus. Il a éteint les grottes, puis les forêts, puis les villes.",
			"Ce que le Silence touche cesse de battre. Les Muets sont ceux qu'il a pris : ils n'ont plus de voix, mais ils entendent tout.",
			"Seuls les Titans ont résisté. Leur cœur bat si fort que le Silence ne peut pas les avaler d'un coup. Alors ils marchent, sans jamais s'arrêter longtemps.",
			"Quand les dernières villes sont tombées, les Titans ont baissé la tête. Nous avons grimpé. Nous sommes devenus leurs Gardiens.",
			"Le Silence s'accroche aux Titans quand ils traversent les terres mortes, ou boivent leur eau. Les Gardiens descendent dans leurs entrailles crever ses kystes.",
			"Tous les Titans marchent vers le même lieu : le Berceau, d'où le Silence est venu. Aucun n'en est revenu.",
			"Si un Gardien atteint le Berceau avec son Titan, le Chant reviendra peut-être. Peut-être faudra-t-il le payer d'un cœur.");

	private static final List<String> JOURNAL = List.of(
			"Journal d'Ysolde,\nGardienne du Titan.\n\nSi tu lis ceci, le Titan t'a choisi. Il ne laisse monter que ceux dont il entend battre le cœur.",
			"Nous étions vingt sur son dos. Cet hiver, le Silence a gagné ses poumons. Nous sommes descendus dans ses entrailles par l'évent, près du cou, pour crever les kystes.",
			"Nous n'avons pas été assez rapides. Il a boité des jours entiers. Les autres sont partis chercher des remèdes au sol, au-delà de son ombre.",
			"Je n'en ai entendu aucun revenir.\n\nNe t'éloigne jamais trop de lui : son battement est le seul abri. Loin de lui, le Silence te gagne, et la nuit plus vite encore.",
			"En bas, ne cours pas. Les Muets chassent au bruit : un pas de course, un coup, un bloc qu'on casse. Accroupis-toi, et ils passeront à côté de toi.",
			"Les anciens ont laissé des Pierres-Échos dans le monde d'en bas. Touche-les : elles se souviennent du temps où le monde chantait.",
			"Il marche vers le Berceau, d'où le Silence est venu. Je ne sais pas ce qui l'y attend.\n\nGarde son cœur battant.\n\n— Ysolde");

	/** La cabane, sur le dos, non loin du point d'arrivée. */
	private static final int CABANE_X = 40, CABANE_Z = 16;

	private Recit() {
	}

	public static EtatRecit etat(net.minecraft.server.MinecraftServer serveur) {
		return serveur.getDataStorage().computeIfAbsent(EtatRecit.TYPE);
	}

	// --- La cabane et le journal ---------------------------------------------

	/** Sur le dos : la cabane de la dernière Gardienne, posée une fois pour toutes. */
	public static void tickDos(ServerLevel dos) {
		EtatRecit etat = etat(dos.getServer());
		if (etat.cabane || dos.players().isEmpty()) {
			return;
		}
		construireLaCabane(dos);
		etat.cabane = true;
		etat.setDirty();
		dos.players().forEach(j -> j.sendSystemMessage(Component.literal(
				"Non loin de la crête se dresse une cabane en ruine : quelqu'un vivait ici avant toi.")
				.withStyle(ChatFormatting.GOLD, ChatFormatting.ITALIC)));
	}

	private static void construireLaCabane(ServerLevel dos) {
		int sol = FormeTitan.hautDuDos(CABANE_X, CABANE_Z);
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
		for (int x = CABANE_X - 3; x <= CABANE_X + 3; x++) {
			for (int z = CABANE_Z - 3; z <= CABANE_Z + 3; z++) {
				int ici = FormeTitan.hautDuDos(x, z);
				// Un terrain plat : on comble les creux, on dégage le dessus (arbres compris).
				for (int y = ici + 1; y <= sol; y++) {
					dos.setBlock(pos.set(x, y, z), Blocks.DIRT.defaultBlockState(), Block.UPDATE_CLIENTS);
				}
				for (int y = sol + 1; y <= sol + 16; y++) {
					dos.setBlock(pos.set(x, y, z), Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
				}
				dos.setBlock(pos.set(x, sol, z), Blocks.GRASS_BLOCK.defaultBlockState(), Block.UPDATE_CLIENTS);
			}
		}
		for (int x = CABANE_X - 2; x <= CABANE_X + 2; x++) {
			for (int z = CABANE_Z - 2; z <= CABANE_Z + 2; z++) {
				boolean bord = Math.abs(x - CABANE_X) == 2 || Math.abs(z - CABANE_Z) == 2;
				boolean coin = Math.abs(x - CABANE_X) == 2 && Math.abs(z - CABANE_Z) == 2;
				dos.setBlock(pos.set(x, sol, z), Interieur.hasard(x, sol, z) < 0.2
						? Blocks.MOSSY_COBBLESTONE.defaultBlockState() : Blocks.SPRUCE_PLANKS.defaultBlockState(), Block.UPDATE_CLIENTS);
				if (coin) {
					for (int y = sol + 1; y <= sol + 3; y++) {
						dos.setBlock(pos.set(x, y, z), Blocks.SPRUCE_LOG.defaultBlockState(), Block.UPDATE_CLIENTS);
					}
				} else if (bord) {
					boolean porte = x == CABANE_X - 2 && z == CABANE_Z;
					for (int y = sol + 1; y <= sol + 2; y++) {
						// Des murs effondrés par endroits : c'est une ruine.
						if (!porte && Interieur.hasard(x, y, z) > 0.3) {
							dos.setBlock(pos.set(x, y, z), Interieur.hasard(z, y, x) < 0.5
									? Blocks.MOSSY_COBBLESTONE.defaultBlockState() : Blocks.SPRUCE_PLANKS.defaultBlockState(), Block.UPDATE_CLIENTS);
						}
					}
				}
				// Ce qui reste du toit, côté est.
				if (x >= CABANE_X && Interieur.hasard(x, sol + 4, z) > 0.25) {
					dos.setBlock(pos.set(x, sol + 4, z), Blocks.SPRUCE_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.BOTTOM),
							Block.UPDATE_CLIENTS);
				}
			}
		}
		// Le lutrin et le journal, face à la porte.
		BlockPos lutrin = new BlockPos(CABANE_X + 1, sol + 1, CABANE_Z);
		dos.setBlock(lutrin, Blocks.LECTERN.defaultBlockState().setValue(LecternBlock.FACING, Direction.WEST)
				.setValue(LecternBlock.HAS_BOOK, true), Block.UPDATE_ALL);
		if (dos.getBlockEntity(lutrin) instanceof LecternBlockEntity l) {
			l.setBook(journal());
		}
		// Un tonneau : ce qu'Ysolde a laissé pour celui qui viendrait après elle.
		BlockPos tonneau = new BlockPos(CABANE_X + 1, sol + 1, CABANE_Z - 1);
		dos.setBlock(tonneau, Blocks.BARREL.defaultBlockState(), Block.UPDATE_ALL);
		if (dos.getBlockEntity(tonneau) instanceof BarrelBlockEntity b) {
			b.setItem(0, new ItemStack(Items.COMPASS));
			b.setItem(1, new ItemStack(Items.BREAD, 8));
			b.setItem(2, new ItemStack(Items.TORCH, 16));
			b.setItem(3, new ItemStack(Items.WOODEN_PICKAXE));
			b.setItem(4, journal());
		}
		dos.setBlock(new BlockPos(CABANE_X - 1, sol + 3, CABANE_Z + 1), Blocks.COBWEB.defaultBlockState(), Block.UPDATE_CLIENTS);
		LeTitan.LOGGER.info("La cabane de la dernière Gardienne est posée en {} {} {}.", CABANE_X, sol, CABANE_Z);
	}

	/** Le journal d'Ysolde, la dernière Gardienne. */
	static ItemStack journal() {
		ItemStack livre = new ItemStack(Items.WRITTEN_BOOK);
		livre.set(DataComponents.WRITTEN_BOOK_CONTENT, new WrittenBookContent(Filterable.passThrough("Journal d'Ysolde"),
				"Ysolde", 0, JOURNAL.stream().map(p -> Filterable.<Component>passThrough(Component.literal(p))).toList(), true));
		return livre;
	}

	// --- Les Pierres-Échos -----------------------------------------------------

	/** Un nouveau morceau du monde d'en bas : y dresser une Pierre-Écho ? Plus souvent dans les terres mortes. */
	static void peutEtreUnePierre(ServerLevel sol, ChunkPos chunk) {
		ZonesMortes zones = Silence.zones(sol.getServer());
		int x = chunk.getMinBlockX() + 8, z = chunk.getMinBlockZ() + 8;
		BlockPos spawn = sol.getRespawnData().pos();
		if (Math.hypot(x - spawn.getX(), z - spawn.getZ()) < 300) {
			return;
		}
		double chance = zones.intensite(x, z) >= ZonesMortes.BORD ? 1 / 90.0 : 1 / 500.0;
		if (Interieur.hasard(chunk.x(), (int) (sol.getSeed() & 0xFFFF), chunk.z()) >= chance) {
			return;
		}
		int y = sol.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z) - 1;
		if (!sol.getBlockState(new BlockPos(x, y, z)).getFluidState().isEmpty()) {
			return;
		}
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
		for (int dx = -1; dx <= 1; dx++) {
			for (int dz = -1; dz <= 1; dz++) {
				double h = Interieur.hasard(x + dx, y, z + dz);
				sol.setBlock(pos.set(x + dx, y, z + dz), h < 0.3 ? Blocks.MOSSY_STONE_BRICKS.defaultBlockState()
						: h < 0.6 ? Blocks.CRACKED_STONE_BRICKS.defaultBlockState() : Blocks.STONE_BRICKS.defaultBlockState(), Block.UPDATE_CLIENTS);
				for (int dy = 1; dy <= 5; dy++) {
					if (dx != 0 || dz != 0) {
						sol.setBlock(pos.set(x + dx, y + dy, z + dz), Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
					}
				}
			}
		}
		sol.setBlock(pos.set(x, y + 1, z), Blocks.TUFF_BRICKS.defaultBlockState(), Block.UPDATE_CLIENTS);
		sol.setBlock(pos.set(x, y + 2, z), Blocks.CHISELED_TUFF_BRICKS.defaultBlockState(), Block.UPDATE_CLIENTS);
		sol.setBlock(pos.set(x, y + 3, z), Blocks.TUFF_BRICKS.defaultBlockState(), Block.UPDATE_CLIENTS);
		sol.setBlock(pos.set(x, y + 4, z), Blocks.AMETHYST_CLUSTER.defaultBlockState(), Block.UPDATE_CLIENTS);
		EtatRecit etat = etat(sol.getServer());
		etat.pierres.add(new BlockPos(x, y + 2, z).asLong());
		etat.fragments.add(-1);
		etat.setDirty();
		LeTitan.LOGGER.info("Une Pierre-Écho se dresse en {} {} {}.", x, y + 2, z);
	}

	/** Un joueur touche un bloc : si c'est une Pierre-Écho, elle rejoue son fragment. */
	public static InteractionResult toucher(Player joueur, Level niveau, BlockHitResult cible) {
		if (niveau.isClientSide() || niveau.dimension() != Level.OVERWORLD || !(joueur instanceof ServerPlayer j)
				|| !niveau.getBlockState(cible.getBlockPos()).is(Blocks.CHISELED_TUFF_BRICKS)) {
			return InteractionResult.PASS;
		}
		EtatRecit etat = etat(j.level().getServer());
		int i = etat.pierres.indexOf(cible.getBlockPos().asLong());
		if (i < 0) {
			return InteractionResult.PASS;
		}
		int fragment = etat.fragments.get(i);
		boolean nouveau = fragment < 0;
		if (nouveau) {
			int entendus = etat.entendus();
			fragment = entendus < FRAGMENTS.size() ? entendus : niveau.getRandom().nextInt(FRAGMENTS.size());
			etat.fragments.set(i, fragment);
			etat.setDirty();
		}
		ecouter(j, (ServerLevel) niveau, cible.getBlockPos(), fragment, nouveau && etat.entendus() == FRAGMENTS.size());
		return InteractionResult.SUCCESS;
	}

	private static void ecouter(ServerPlayer joueur, ServerLevel sol, BlockPos pierre, int fragment, boolean dernier) {
		joueur.connection.send(new ClientboundSetTitleTextPacket(Component.literal("Pierre-Écho").withStyle(ChatFormatting.AQUA)));
		joueur.connection.send(new ClientboundSetSubtitleTextPacket(
				Component.literal("Fragment " + (fragment + 1) + " sur " + FRAGMENTS.size()).withStyle(ChatFormatting.GRAY)));
		joueur.sendSystemMessage(Component.literal("« " + FRAGMENTS.get(fragment) + " »")
				.withStyle(ChatFormatting.AQUA, ChatFormatting.ITALIC));
		sol.playSound(null, pierre, SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.BLOCKS, 2.0f, 0.6f);
		sol.playSound(null, pierre, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.BLOCKS, 2.0f, 0.5f);
		sol.sendParticles(ParticleTypes.END_ROD, pierre.getX() + 0.5, pierre.getY() + 1, pierre.getZ() + 0.5, 30, 0.6, 1.2, 0.6, 0.02);
		if (dernier) {
			joueur.sendSystemMessage(Component.literal("Tu as entendu tous les échos. Quelque part, loin devant le Titan, le Berceau attend.")
					.withStyle(ChatFormatting.GOLD));
		}
	}

	/** Les Pierres-Échos chantent doucement quand quelqu'un passe à proximité : on les trouve à l'oreille. */
	static void tickSol(ServerLevel sol) {
		if (sol.getGameTime() % 160 != 0 || sol.players().isEmpty()) {
			return;
		}
		for (long cle : etat(sol.getServer()).pierres) {
			BlockPos pierre = BlockPos.of(cle);
			if (sol.isLoaded(pierre) && sol.getNearestPlayer(pierre.getX(), pierre.getY(), pierre.getZ(), 64, false) != null) {
				sol.playSound(null, pierre, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.BLOCKS, 3.0f, 0.5f);
				sol.sendParticles(ParticleTypes.END_ROD, pierre.getX() + 0.5, pierre.getY() + 3, pierre.getZ() + 0.5, 3, 0.2, 0.8, 0.2, 0.01);
			}
		}
	}
}
