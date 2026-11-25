package fr.letitan.silence;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

import java.util.ArrayList;
import java.util.List;

import static fr.letitan.LeTitan.id;

/**
 * Où en est le récit dans ce monde : la cabane de la dernière Gardienne est-elle posée, où sont
 * les Pierres-Échos, et quel fragment chacune a déjà rejoué.
 */
public final class EtatRecit extends SavedData {
	public static final Codec<EtatRecit> CODEC = RecordCodecBuilder.create(i -> i.group(
			Codec.BOOL.optionalFieldOf("cabane", false).forGetter(e -> e.cabane),
			Codec.LONG.listOf().optionalFieldOf("pierres", List.of()).forGetter(e -> e.pierres),
			Codec.INT.listOf().optionalFieldOf("fragments", List.of()).forGetter(e -> e.fragments)
	).apply(i, EtatRecit::new));

	public static final SavedDataType<EtatRecit> TYPE =
			new SavedDataType<>(id("recit"), EtatRecit::new, CODEC, DataFixTypes.SAVED_DATA_COMMAND_STORAGE);

	public boolean cabane;
	/** Le cœur de chaque Pierre-Écho (BlockPos compacté). */
	public final List<Long> pierres = new ArrayList<>();
	/** Pour chaque pierre, le fragment qu'elle rejoue (-1 : pas encore écoutée). */
	public final List<Integer> fragments = new ArrayList<>();

	public EtatRecit() {
	}

	private EtatRecit(boolean cabane, List<Long> pierres, List<Integer> fragments) {
		this.cabane = cabane;
		this.pierres.addAll(pierres);
		this.fragments.addAll(fragments);
	}

	/** Nombre de fragments déjà entendus (chacun n'est attribué qu'une fois). */
	public int entendus() {
		return (int) fragments.stream().filter(f -> f >= 0).count();
	}
}
