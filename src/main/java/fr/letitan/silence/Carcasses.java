package fr.letitan.silence;

import fr.letitan.LeTitan;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
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

	// --- Les Titans muets vaincus -------------------------------------------------

	/** Le Titan muet vient de tomber : on retient où, et l'on pose ses os là où le monde est déjà chargé. */
	static void ajouterLeTitanMuet(MinecraftServer serveur, Carcasse c) {
		EtatRecit etat = Recit.etat(serveur);
		etat.tombes.add(c.cx());
		etat.tombes.add(c.cz());
		etat.tombes.add(c.angle());
		etat.gardiens.add(c.cle());
		etat.deuils.add(c.cle());
		etat.setDirty();
		ServerLevel sol = serveur.overworld();
		int r = (int) Math.ceil(Carcasse.RAYON / 16) + 1;
		int ccx = (int) Math.floor(c.cx()) >> 4, ccz = (int) Math.floor(c.cz()) >> 4;
		for (int i = ccx - r; i <= ccx + r; i++) {
			for (int j = ccz - r; j <= ccz + r; j++) {
				if (sol.hasChunk(i, j)) {
					CHARGES.add(new ChunkPos(i, j));
				}
			}
		}
	}

	/** Morceaux du monde chargés, où il faudra peut-être poser des os (traités au tick suivant). */
	private static final java.util.Queue<ChunkPos> CHARGES = new java.util.ArrayDeque<>();

	/**
	 * Un morceau du monde se charge. On ne bâtit pas pendant le chargement (le morceau n'est pas
	 * encore prêt) : on le note, et on y posera les os au prochain tick.
	 */
	public static void morceauCharge(ServerLevel sol, ChunkPos chunk) {
		if (!Recit.etat(sol.getServer()).tombes.isEmpty()) {
			CHARGES.add(chunk);
		}
	}

	static void oublier() {
		CHARGES.clear();
	}

	private static void poserLesOsEnAttente(ServerLevel sol) {
		for (int i = 0; i < 4 && !CHARGES.isEmpty(); i++) {
			ChunkPos chunk = CHARGES.poll();
			if (sol.hasChunk(chunk.x(), chunk.z())) {
				poserLesOs(sol, chunk);
			}
		}
	}

	/** Pose dans ce morceau du monde les os des Titans muets vaincus, une seule fois. */
	private static void poserLesOs(ServerLevel sol, ChunkPos chunk) {
		EtatRecit etat = Recit.etat(sol.getServer());
		for (int t = 0; t + 2 < etat.tombes.size(); t += 3) {
			Carcasse c = new Carcasse(etat.tombes.get(t), etat.tombes.get(t + 1), etat.tombes.get(t + 2), 1.0, Carcasse.MUET);
			double cx = chunk.getMinBlockX() + 8, cz = chunk.getMinBlockZ() + 8;
			if (Math.hypot(cx - c.cx(), cz - c.cz()) > Carcasse.RAYON + 24) {
				continue;
			}
			long cle = ((long) (t / 3) << 40) ^ chunk.pack();
			if (etat.tombesBaties.contains(cle)) {
				continue;
			}
			etat.tombesBaties.add(cle);
			etat.setDirty();
			batir(sol, chunk, c);
		}
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
			if (reliques && c.numero() == Carcasse.MUET) {
				remplirLesReliquesDuMuet(ch, hasard);
			} else if (reliques) {
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

	/** Ce que le Titan muet vaincu laisse dans sa cage. */
	private static void remplirLesReliquesDuMuet(ChestBlockEntity coffre, RandomSource hasard) {
		ItemStack coeur = new ItemStack(Items.ECHO_SHARD);
		coeur.set(DataComponents.CUSTOM_NAME, Component.literal("Dernier battement du Titan muet").withStyle(ChatFormatting.AQUA));
		coeur.set(DataComponents.LORE, new ItemLore(List.of(
				Component.literal("Le Silence l'avait pris il y a longtemps.").withStyle(ChatFormatting.GRAY),
				Component.literal("En tombant, son cœur a battu une fois. Une seule.").withStyle(ChatFormatting.GRAY))));
		coffre.setItem(13, coeur);
		mettre(coffre, 0, Items.NETHERITE_SCRAP, 1, 3, hasard);
		mettre(coffre, 2, Items.DIAMOND, 3, 6, hasard);
		mettre(coffre, 4, Items.ECHO_SHARD, 4, 8, hasard);
		mettre(coffre, 6, Items.ENCHANTED_GOLDEN_APPLE, 1, 1, hasard);
		mettre(coffre, 8, Items.TOTEM_OF_UNDYING, 1, 1, hasard);
		mettre(coffre, 20, Items.SCULK_CATALYST, 1, 2, hasard);
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
		if (c.numero() == Carcasse.MUET) {
			ItemStack livre = new ItemStack(Items.WRITTEN_BOOK);
			livre.set(DataComponents.WRITTEN_BOOK_CONTENT, new WrittenBookContent(Filterable.passThrough("Ce qu'il entendait"),
					"?", 0, List.of(
							"Il n'avait plus de voix. Il ne lui restait que l'ouïe.",
							"Il entendait notre Titan battre, de très loin. C'était le seul bruit du monde. Il est venu vers lui, comme on va vers une lumière.",
							"Peut-être ne voulait-il pas le tuer. Peut-être voulait-il seulement l'entendre de plus près.")
							.stream().map(p -> Filterable.<Component>passThrough(Component.literal(p))).toList(), true));
			return livre;
		}
		ItemStack livre = new ItemStack(Items.WRITTEN_BOOK);
		livre.set(DataComponents.WRITTEN_BOOK_CONTENT, new WrittenBookContent(Filterable.passThrough("Carnet de " + GARDIENS[c.numero()]),
				GARDIENS[c.numero()], 0,
				CARNETS.get(c.numero()).stream().map(p -> Filterable.<Component>passThrough(Component.literal(p))).toList(), true));
		return livre;
	}

	// --- Le Gardien muet --------------------------------------------------------

	/** Près d'un cœur pétrifié, le Gardien muet se lève, une fois par carcasse. */
	static void tickSol(ServerLevel sol) {
		poserLesOsEnAttente(sol);
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
