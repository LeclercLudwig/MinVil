package fr.letitan.monde;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import fr.letitan.monde.FormeTitan.Matiere;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.RegistryAccess;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.NaturalSpawner;
import net.minecraft.world.level.NoiseColumn;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeManager;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.chunk.ChunkGeneratorStructureState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.LegacyRandomSource;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.RandomSupport;
import net.minecraft.world.level.levelgen.WorldgenRandom;
import net.minecraft.world.level.levelgen.blending.Blender;
import net.minecraft.world.level.levelgen.densityfunction.SamplerContext;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;

import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

/**
 * Générateur du dos du Titan : le corps de la créature, suspendu dans le vide
 * (la tête et les pattes, animées, sont ajoutées par {@link fr.letitan.vie.CorpsVivant}).
 * La forme vient de {@link FormeTitan} ; la végétation vient du biome (arbres, herbes, fleurs).
 */
public final class GenerateurTitan extends ChunkGenerator {
	public static final MapCodec<GenerateurTitan> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
			BiomeSource.CODEC.fieldOf("biome_source").forGetter(ChunkGenerator::getBiomeSource)
	).apply(instance, GenerateurTitan::new));

	private static final int Y_MAX_FORME = 200;
	/** Au-delà, il n'y a plus rien du Titan : inutile de calculer bloc par bloc. */
	private static final int X_MAX = 110;
	private static final int Z_MIN = FormeTitan.AVANT - 5;
	private static final int Z_MAX = FormeTitan.ARRIERE + 5;

	public GenerateurTitan(BiomeSource biomeSource) {
		super(biomeSource);
	}

	@Override
	protected MapCodec<? extends ChunkGenerator> codec() {
		return CODEC;
	}

	public static BlockState bloc(Matiere matiere) {
		return switch (matiere) {
			case VIDE -> Blocks.AIR.defaultBlockState();
			case HERBE -> Blocks.GRASS_BLOCK.defaultBlockState();
			case TERRE -> Blocks.DIRT.defaultBlockState();
			case PEAU -> Blocks.PACKED_MUD.defaultBlockState();
			case CHAIR -> Blocks.NETHER_WART_BLOCK.defaultBlockState();
			case OS -> Blocks.BONE_BLOCK.defaultBlockState();
			case CAVITE -> Blocks.CAVE_AIR.defaultBlockState();
			// Sang et acide : de l'eau, teintée par le biome des entrailles (rouge) ou de l'estomac (vert).
			case SANG, ACIDE -> Blocks.WATER.defaultBlockState();
			case LUMIERE -> Blocks.SHROOMLIGHT.defaultBlockState();
			case LUMIERE_ROSE -> Blocks.PEARLESCENT_FROGLIGHT.defaultBlockState();
			case LUMIERE_VERTE -> Blocks.VERDANT_FROGLIGHT.defaultBlockState();
			case POUMON -> Blocks.DYED_TERRACOTTA.pick(DyeColor.PINK).defaultBlockState();
			case POUMON_CLAIR -> Blocks.WOOL.pick(DyeColor.PINK).defaultBlockState();
			case ESTOMAC -> Blocks.MUD.defaultBlockState();
			case GLU -> Blocks.SLIME_BLOCK.defaultBlockState();
			case VRILLE -> Blocks.WEEPING_VINES.defaultBlockState();
		};
	}

	private static boolean horsDuTitan(int x, int z) {
		return Math.abs(x) > X_MAX || z < Z_MIN || z > Z_MAX;
	}

	@Override
	public CompletableFuture<ChunkAccess> buildTerrain(ChunkAccess chunk, Blender blender, RandomState randomState,
			StructureManager structureManager, BiomeManager biomeManager, WorldGenRegion region,
			Set<Holder<Biome>> biomes) {
		Heightmap fond = chunk.getOrCreateHeightmapUnprimed(Heightmap.Types.OCEAN_FLOOR_WG);
		Heightmap surface = chunk.getOrCreateHeightmapUnprimed(Heightmap.Types.WORLD_SURFACE_WG);
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
		int x0 = chunk.getPos().getMinBlockX();
		int z0 = chunk.getPos().getMinBlockZ();
		int yMin = Math.max(chunk.getMinY(), FormeTitan.Y_MIN);
		int yMax = Math.min(chunk.getMaxY(), Y_MAX_FORME);
		for (int dx = 0; dx < 16; dx++) {
			for (int dz = 0; dz < 16; dz++) {
				int x = x0 + dx, z = z0 + dz;
				if (horsDuTitan(x, z)) {
					continue;
				}
				for (int y = yMin; y <= yMax; y++) {
					Matiere matiere = FormeTitan.matiere(x, y, z);
					if (matiere == Matiere.VIDE) {
						continue;
					}
					BlockState etat = bloc(matiere);
					chunk.setBlockState(pos.set(x, y, z), etat);
					fond.update(dx, y, dz, etat);
					surface.update(dx, y, dz, etat);
				}
			}
		}
		return CompletableFuture.completedFuture(chunk);
	}

	/** Pas de villages ni de mines dans la chair du Titan. */
	@Override
	public void createStructures(RegistryAccess registryAccess, ChunkGeneratorStructureState structureState,
			StructureManager structureManager, ChunkAccess chunk, StructureTemplateManager templateManager,
			ResourceKey<Level> level) {
	}

	/** Les animaux apparaissent sur le dos à la création des chunks, comme dans le monde normal. */
	@Override
	public void spawnOriginalMobs(WorldGenRegion region) {
		var centre = region.getCenter();
		BlockPos source = centre.getWorldPosition().atY(region.getMaxY());
		WorldgenRandom random = new WorldgenRandom(new LegacyRandomSource(RandomSupport.generateUniqueSeed()));
		random.setDecorationSeed(region.getSeed(), centre.getMinBlockX(), centre.getMinBlockZ());
		NaturalSpawner.spawnMobsForChunkGeneration(region, source, centre, random);
	}

	@Override
	public int getGenDepth() {
		return 384;
	}

	@Override
	public int getSeaLevel() {
		return FormeTitan.Y_MIN;
	}

	@Override
	public int getMinY() {
		return FormeTitan.Y_MIN;
	}

	@Override
	public int getBaseHeight(int x, int z, Heightmap.Types type, LevelHeightAccessor level, RandomState randomState) {
		if (horsDuTitan(x, z)) {
			return level.getMinY();
		}
		return Math.max(level.getMinY(), FormeTitan.plusHautBloc(x, z, Y_MAX_FORME) + 1);
	}

	@Override
	public NoiseColumn getBaseColumn(int x, int z, LevelHeightAccessor level, RandomState randomState) {
		BlockState[] colonne = new BlockState[level.getHeight()];
		for (int i = 0; i < colonne.length; i++) {
			int y = level.getMinY() + i;
			colonne[i] = horsDuTitan(x, z) ? Blocks.AIR.defaultBlockState() : bloc(FormeTitan.matiere(x, y, z));
		}
		return new NoiseColumn(level.getMinY(), colonne);
	}

	@Override
	public void addDebugScreenInfo(List<String> lignes, RandomState randomState, BlockPos pos, SamplerContext contexte) {
		lignes.add("Titan : " + FormeTitan.matiere(pos.getX(), pos.getY(), pos.getZ()));
	}
}
