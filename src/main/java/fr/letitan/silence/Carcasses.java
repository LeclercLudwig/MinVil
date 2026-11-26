package fr.letitan.silence;

import fr.letitan.LeTitan;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.Filterable;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.item.component.WrittenBookContent;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;

import java.util.List;

/**
 * Les carcasses de Titans dans le monde d'en bas, côté serveur : on les bâtit morceau par
 * morceau à mesure que les terres apparaissent, avec leurs reliques et le carnet de leur
 * Gardien ; un Gardien muet se lève quand on approche du cœur ; et notre Titan pleure en
 * passant près des siens.
 */
public final class Carcasses {
	/** Étiquette du Gardien muet (il ne s'évanouit pas comme les autres Muets). */
	public static final String GARDIEN = "letitan.gardien";

	private Carcasses() {
	}

	// --- Construction ------------------------------------------------------------

	/** Un nouveau morceau du monde : y bâtir la part des carcasses qui le traversent. */
	static void construire(ServerLevel sol, ChunkPos chunk) {
		double x0 = chunk.getMinBlockX() + 8, z0 = chunk.getMinBlockZ() + 8;
		for (Carcasse c : Silence.carcasses(sol.getServer(), x0, z0, 12)) {
			batir(sol, chunk, c);
		}
	}

	private static void batir(ServerLevel sol, ChunkPos chunk, Carcasse c) {
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
		int hauteurMax = (int) Math.ceil(45 * c.echelle());
		for (int dx = 0; dx < 16; dx++) {
			for (int dz = 0; dz < 16; dz++) {
				int x = chunk.getMinBlockX() + dx, z = chunk.getMinBlockZ() + dz;
				double[] l = c.local(x + 0.5, z + 0.5);
				if (!Carcasse.sousLaCarcasse(l[0], l[1])) {
					continue;
				}
				int sol0 = Pourriture.solide(sol, pos, x, z);
				if (sol0 == Integer.MIN_VALUE) {
					sol0 = sol.getHeight(Heightmap.Types.OCEAN_FLOOR, x, z) - 1;
				}
				// Le sol sous la carcasse : du sculk, et plus rien n'y pousse.
				if (sol.getBlockState(pos.set(x, sol0, z)).getFluidState().isEmpty()
						&& fr.letitan.monde.Interieur.hasard(x, sol0, z) < 0.8) {
					sol.setBlock(pos, Blocks.SCULK.defaultBlockState(), Block.UPDATE_CLIENTS);
				}
				for (int dy = 1; dy <= hauteurMax + 2; dy++) {
					BlockState etat = sol.getBlockState(pos.set(x, sol0 + dy, z));
					if (etat.is(BlockTags.LEAVES) || etat.is(Blocks.BAMBOO)
							|| (etat.canBeReplaced() && !etat.isAir() && etat.getFluidState().isEmpty())) {
						sol.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
					}
				}
				// Les os.
				for (int dy = -2; dy <= hauteurMax; dy++) {
					Carcasse.Os os = Carcasse.os(l[0], (dy + 0.5) / c.echelle(), l[1]);
					if (os != null) {
						sol.setBlock(pos.set(x, sol0 + 1 + dy, z), bloc(os), Block.UPDATE_CLIENTS);
					}
				}
			}
		}
		poserUnCoffre(sol, chunk, c, Carcasse.RELIQUES, true);
		poserUnCoffre(sol, chunk, c, Carcasse.CRANE, false);
	}

	private static BlockState bloc(Carcasse.Os os) {
		return switch (os) {
			case OS -> Blocks.BONE_BLOCK.defaultBlockState();
			case OS_USE -> Blocks.CALCITE.defaultBlockState();
			case SCULK -> Blocks.SCULK.defaultBlockState();
			case COEUR -> Blocks.TUFF.defaultBlockState();
			case LARME -> Blocks.CRYING_OBSIDIAN.defaultBlockState();
		};
	}

	/** Le coffre des reliques (devant le cœur) ou celui du crâne, s'il tombe dans ce morceau du monde. */
	private static void poserUnCoffre(ServerLevel sol, ChunkPos chunk, Carcasse c, double[] ou, boolean reliques) {
		double[] m = c.monde(ou[0], ou[2]);
		int x = (int) Math.floor(m[0]), z = (int) Math.floor(m[1]);
		if (x >> 4 != chunk.x() || z >> 4 != chunk.z()) {
			return;
		}
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
		int sol0 = Pourriture.solide(sol, pos, x, z);
		if (sol0 == Integer.MIN_VALUE) {
			return;
		}
		int y = sol0 + 1 + (int) Math.floor(ou[1] * c.echelle());
		while (!sol.getBlockState(pos.set(x, y, z)).isAir() && y < sol0 + 50) {
			y++;
		}
		BlockPos coffre = new BlockPos(x, y, z);
		sol.setBlock(coffre, Blocks.CHEST.defaultBlockState(), Block.UPDATE_ALL);
		if (sol.getBlockEntity(coffre) instanceof ChestBlockEntity ch) {
			RandomSource hasard = RandomSource.create(c.cle() ^ (reliques ? 1 : 2));
			if (reliques) {
				remplirLesReliques(ch, c, hasard);
			} else {
				remplirLeCrane(ch, c, hasard);
			}
		}
		LeTitan.LOGGER.debug("Carcasse de {} : {} en {} {} {}.", c.nom(), reliques ? "les reliques" : "le crâne", x, y, z);
	}

	private static void remplirLesReliques(ChestBlockEntity coffre, Carcasse c, RandomSource hasard) {
		ItemStack coeur = new ItemStack(Items.HEART_OF_THE_SEA);
		coeur.set(DataComponents.CUSTOM_NAME, Component.literal("Cœur pétrifié de " + c.nom()).withStyle(ChatFormatting.AQUA));
		coeur.set(DataComponents.LORE, new ItemLore(List.of(
				Component.literal("Il ne bat plus.").withStyle(ChatFormatting.GRAY),
				Component.literal("Pourtant, en le serrant, on croit entendre un écho.").withStyle(ChatFormatting.GRAY),
				Component.literal("Le porter ralentit le Silence de moitié.").withStyle(ChatFormatting.DARK_AQUA))));
		coffre.setItem(13, coeur);
		mettre(coffre, 0, Items.DIAMOND, 1, 3, hasard);
		mettre(coffre, 2, Items.EMERALD, 4, 10, hasard);
		mettre(coffre, 4, Items.ECHO_SHARD, 1, 3, hasard);
		mettre(coffre, 6, Items.GOLDEN_APPLE, 1, 2, hasard);
		mettre(coffre, 8, Items.IRON_INGOT, 4, 10, hasard);
		mettre(coffre, 20, Items.BONE_BLOCK, 3, 6, hasard);
		if (hasard.nextFloat() < 0.1f) {
			coffre.setItem(22, new ItemStack(Items.ENCHANTED_GOLDEN_APPLE));
		}
		if (hasard.nextFloat() < 0.08f) {
			coffre.setItem(24, new ItemStack(Items.TOTEM_OF_UNDYING));
		}
	}

	private static void remplirLeCrane(ChestBlockEntity coffre, Carcasse c, RandomSource hasard) {
		coffre.setItem(13, carnet(c));
		mettre(coffre, 0, Items.BONE, 4, 12, hasard);
		mettre(coffre, 4, Items.BONE_MEAL, 6, 16, hasard);
		mettre(coffre, 9, Items.ARROW, 8, 24, hasard);
		mettre(coffre, 18, Items.CANDLE, 1, 3, hasard);
	}

	private static void mettre(ChestBlockEntity coffre, int place, Item item, int min, int max, RandomSource hasard) {
		coffre.setItem(place, new ItemStack(item, min + hasard.nextInt(max - min + 1)));
	}

	// --- Les carnets des Gardiens tombés ------------------------------------------

	private static final List<List<String>> CARNETS = List.of(
			List.of("Carnet de Bran,\nGardien de Vael.\n\nVael est tombé ce matin. Ses genoux ont cédé d'un coup, et tout son dos a glissé dans la poussière.",
					"Le Silence était dans son cœur depuis l'automne. Nous crevions les kystes un à un ; il en revenait toujours deux.",
					"Avant de tomber, il s'est tourné vers le couchant et il a grondé longtemps. Je crois qu'il voyait le Berceau.",
					"Nous partons à pied. Si un autre Titan passe, nous lui ferons signe. Qu'il ne s'arrête pas ici.\n\n— Bran"),
			List.of("Carnet d'Ilse,\nGardienne d'Orm.\n\nOrm a bu dans une terre morte. Je n'ai pas su l'en empêcher.",
					"Ses poumons se sont remplis de sculk. Il toussait des nuages noirs. Puis il n'a plus toussé du tout.",
					"Les Muets sont venus la première nuit. Ils grimpent sur lui maintenant, sans un bruit, comme s'ils l'écoutaient.",
					"J'ai caché son cœur dans sa cage. S'il reste un écho dans ce monde, c'est là.\n\n— Ilse"),
			List.of("Carnet de Tomas,\nGardien de Sélène.\n\nSélène était la plus vieille. Sa crête touchait les nuages.",
					"Elle marchait vers le Berceau depuis trois cents ans. Elle disait qu'elle l'entendait déjà. Je la crois.",
					"Elle s'est couchée ici, doucement, comme pour dormir. Son cœur a battu encore sept jours.",
					"Le septième jour, le Silence est entré. Si tu trouves ce carnet, ne t'arrête pas. Continue.\n\n— Tomas"),
			List.of("Carnet de Nour,\nGardienne de Brakka.\n\nBrakka s'est battu. Un autre Titan, devenu muet, est sorti de la brume.",
					"Ils se sont heurtés jusqu'au soir. Le sol tremblait à des lieues. Brakka a gagné, mais l'autre l'avait mordu.",
					"La morsure était grise. Le gris a gagné son cou, puis son cœur. Nous n'avions plus de quoi le soigner.",
					"Garde ton Titan loin des grands Muets. Certains Titans ne meurent pas tout à fait.\n\n— Nour"));

	private static final String[] GARDIENS = {"Bran", "Ilse", "Tomas", "Nour"};

	static ItemStack carnet(Carcasse c) {
		ItemStack livre = new ItemStack(Items.WRITTEN_BOOK);
		livre.set(DataComponents.WRITTEN_BOOK_CONTENT, new WrittenBookContent(Filterable.passThrough("Carnet de " + GARDIENS[c.numero()]),
				GARDIENS[c.numero()], 0,
				CARNETS.get(c.numero()).stream().map(p -> Filterable.<Component>passThrough(Component.literal(p))).toList(), true));
		return livre;
	}

	// --- Le Gardien muet --------------------------------------------------------

	/** Près d'un cœur pétrifié, le Gardien muet se lève, une fois par carcasse. */
	static void tickSol(ServerLevel sol) {
		if (sol.getGameTime() % 40 != 0) {
			return;
		}
		EtatRecit etat = Recit.etat(sol.getServer());
		for (ServerPlayer joueur : sol.players()) {
			if (joueur.isCreative() || joueur.isSpectator()) {
				continue;
			}
			for (Carcasse c : Silence.carcasses(sol.getServer(), joueur.getX(), joueur.getZ(), 30)) {
				double[] coeur = c.monde(Carcasse.COEUR[0], Carcasse.COEUR[2]);
				if (Math.hypot(joueur.getX() - coeur[0], joueur.getZ() - coeur[1]) > 22 || etat.gardiens.contains(c.cle())) {
					continue;
				}
				etat.gardiens.add(c.cle());
				etat.setDirty();
				leverLeGardien(sol, c, coeur, joueur);
			}
		}
	}

	private static void leverLeGardien(ServerLevel sol, Carcasse c, double[] coeur, ServerPlayer joueur) {
		int x = (int) Math.floor(coeur[0] + 6), z = (int) Math.floor(coeur[1]);
		int y = sol.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
		Zombie gardien = Muets.creer(sol, x + 0.5, y, z + 0.5, sol.getRandom());
		if (gardien == null) {
			return;
		}
		gardien.addTag(GARDIEN);
		gardien.setCustomName(Component.literal("Gardien muet de " + c.nom()).withStyle(ChatFormatting.DARK_AQUA));
		gardien.setCustomNameVisible(true);
		gardien.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.SCULK_SHRIEKER));
		gardien.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.BONE));
		gardien.getAttribute(Attributes.MAX_HEALTH).setBaseValue(80);
		gardien.setHealth(80);
		gardien.getAttribute(Attributes.ATTACK_DAMAGE).setBaseValue(8);
		gardien.getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(0.27);
		gardien.setTarget(joueur);
		sol.playSound(null, x, y, z, SoundEvents.WARDEN_EMERGE, SoundSource.HOSTILE, 3.0f, 0.7f);
		joueur.sendSystemMessage(Component.literal("Quelque chose se lève près du cœur de " + c.nom() + "...")
				.withStyle(ChatFormatting.DARK_AQUA, ChatFormatting.ITALIC));
	}
}
